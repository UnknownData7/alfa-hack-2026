package ru.hackathon.credit.task;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.audit.AuditService;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.document.ApplicationDocumentRepository;
import ru.hackathon.credit.product.Product;
import ru.hackathon.credit.scoring.ScoringService;

/**
 * Очередь задач андеррайтера: создание задачи с проверками, выдача следующей задачи, взятие, проверки.
 */
@Service
public class TaskService {

    private final BankTaskRepository tasks;
    private final TaskCheckRepository checks;
    private final BankDecisionRepository decisions;
    private final ApplicationDocumentRepository documents;
    private final AuditService audit;
    private final Duration sla;

    public TaskService(BankTaskRepository tasks, TaskCheckRepository checks, BankDecisionRepository decisions,
                       ApplicationDocumentRepository documents, AuditService audit,
                       @Value("${app.tasks.sla-hours}") long slaHours) {
        this.tasks = tasks;
        this.checks = checks;
        this.decisions = decisions;
        this.documents = documents;
        this.audit = audit;
        this.sla = Duration.ofHours(slaHours);
    }

    /**
     * Создаёт задачу ручной проверки. Если по заявке уже был запрос данных от андеррайтера, задача возвращается
     * тому же сотруднику с наивысшим приоритетом.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BankTask create(LoanApplication app, Product product, ScoringService.Run run, String reasonCode) {
        Optional<BankTask> previous = previousRequestInfoTask(app.getId());
        boolean repeat = previous.isPresent();

        BankTask task = new BankTask();
        task.setApplicationId(app.getId());
        task.setPriority(TaskRules.priority(run.amount(), repeat));
        task.setStatus(BankTask.NEW);
        task.setRepeat(repeat);
        task.setAssigneeUserId(previous.map(BankTask::getAssigneeUserId).orElse(null));
        task.setReasonCodes(reasonCode);
        Instant now = Instant.now();
        task.setCreatedAt(now);
        task.setSlaDueAt(now.plus(sla));
        tasks.save(task);

        double coverage = run.annualTurnover() > 0 ? run.amount() / run.annualTurnover() : 1.0;
        TaskRules.CheckContext ctx = new TaskRules.CheckContext(product.isRequiresCollateral(), coverage,
                documents.countByApplicationId(app.getId()) > 0, run.maxDelinquencyDays(), run.businessAgeMonths(),
                run.industryRisk());
        for (TaskRules.CheckDefinition def : TaskRules.checks(ctx)) {
            TaskCheck c = new TaskCheck();
            c.setTaskId(task.getId());
            c.setCheckCode(def.code());
            c.setTitle(def.title());
            c.setGoal(def.goal());
            c.setStatus(TaskCheck.PENDING);
            checks.save(c);
        }
        audit.log(app.getId(), "SYSTEM", null, "TASK_CREATED",
                "Задача " + task.getId() + ", приоритет " + task.getPriority() + (repeat ? ", повторная" : ""));
        return task;
    }

    @Transactional
    public BankTask takeNext(Long userId) {
        ensureNoTaskInProgress(userId, null);
        BankTask task = tasks.lockNextAvailable(userId)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "QUEUE_EMPTY",
                        "Новых задач в очереди нет"));
        return start(task, userId);
    }

    @Transactional
    public BankTask take(Long taskId, Long userId) {
        BankTask task = get(taskId);
        boolean freeNew = BankTask.NEW.equals(task.getStatus())
                && (task.getAssigneeUserId() == null || task.getAssigneeUserId().equals(userId));
        boolean ownPostponed = BankTask.POSTPONED.equals(task.getStatus()) && userId.equals(task.getAssigneeUserId());
        if (BankTask.IN_PROGRESS.equals(task.getStatus()) && userId.equals(task.getAssigneeUserId())) {
            return task;
        }
        if (!freeNew && !ownPostponed) {
            throw ApiException.conflict("TASK_NOT_AVAILABLE", "Задачу нельзя взять: она в работе у другого сотрудника или завершена");
        }
        ensureNoTaskInProgress(userId, taskId);
        return start(task, userId);
    }

    @Transactional
    public TaskCheck updateCheck(Long taskId, String code, String status, String comment, Long userId) {
        BankTask task = requireOwnInProgress(taskId, userId);
        if (!List.of(TaskCheck.OK, TaskCheck.NEGATIVE, TaskCheck.NEEDS_INFO, TaskCheck.PENDING).contains(status)) {
            throw ApiException.badRequest("BAD_CHECK_STATUS", "Недопустимый результат проверки");
        }
        if (TaskCheck.NEGATIVE.equals(status) && (comment == null || comment.isBlank())) {
            throw ApiException.unprocessable("COMMENT_REQUIRED", "Для отрицательного результата нужен комментарий");
        }
        TaskCheck check = checks.findByTaskIdAndCheckCode(task.getId(), code)
                .orElseThrow(() -> ApiException.notFound("Проверка не найдена"));
        check.setStatus(status);
        check.setComment(comment);
        check.setUpdatedAt(Instant.now());
        return check;
    }

    public BankTask get(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Задача не найдена"));
    }

    public BankTask requireOwnInProgress(Long taskId, Long userId) {
        BankTask task = get(taskId);
        if (!BankTask.IN_PROGRESS.equals(task.getStatus()) || !userId.equals(task.getAssigneeUserId())) {
            throw ApiException.conflict("TASK_NOT_IN_PROGRESS", "Сначала возьмите задачу в работу");
        }
        return task;
    }

    public List<TaskCheck> checks(Long taskId) {
        return checks.findByTaskIdOrderByIdAsc(taskId);
    }

    private BankTask start(BankTask task, Long userId) {
        task.setStatus(BankTask.IN_PROGRESS);
        task.setAssigneeUserId(userId);
        task.setTakenAt(Instant.now());
        tasks.saveAndFlush(task);
        audit.log(task.getApplicationId(), "BANK", userId, "TASK_TAKEN", "Задача " + task.getId());
        return task;
    }

    private void ensureNoTaskInProgress(Long userId, Long exceptTaskId) {
        tasks.findFirstByAssigneeUserIdAndStatus(userId, BankTask.IN_PROGRESS)
                .filter(t -> !t.getId().equals(exceptTaskId))
                .ifPresent(t -> {
                    throw ApiException.conflict("TASK_ALREADY_IN_PROGRESS",
                            "У вас уже есть задача в работе. Завершите или отложите её");
                });
    }

    private Optional<BankTask> previousRequestInfoTask(Long applicationId) {
        List<BankTask> previous = tasks.findByApplicationIdOrderByIdAsc(applicationId);
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        List<Long> ids = previous.stream().map(BankTask::getId).toList();
        return decisions.findByTaskIdInOrderByIdAsc(ids).stream()
                .filter(d -> "REQUEST_INFO".equals(d.getResult()))
                .reduce((a, b) -> b)
                .flatMap(d -> previous.stream().filter(t -> t.getId().equals(d.getTaskId())).findFirst());
    }
}
