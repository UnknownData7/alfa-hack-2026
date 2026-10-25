package ru.hackathon.credit.task;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.application.ActorType;
import ru.hackathon.credit.application.ApplicationDtos.HistoryEntry;
import ru.hackathon.credit.application.ApplicationDtos.InfoRequestView;
import ru.hackathon.credit.application.ApplicationRepository;
import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.application.ApplicationViews;
import ru.hackathon.credit.application.InfoRequest;
import ru.hackathon.credit.application.InfoRequestRepository;
import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.application.StatusHistoryRepository;
import ru.hackathon.credit.application.StatusTransitionService;
import ru.hackathon.credit.audit.AuditService;
import ru.hackathon.credit.auth.AppUser;
import ru.hackathon.credit.auth.AppUserRepository;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.common.Json;
import ru.hackathon.credit.company.ClientProfileController;
import ru.hackathon.credit.company.Company;
import ru.hackathon.credit.company.CompanyRepository;
import ru.hackathon.credit.company.CompanyService;
import ru.hackathon.credit.decision.CreditDecisionEngine;
import ru.hackathon.credit.decision.DecisionInput;
import ru.hackathon.credit.decision.DecisionResult;
import ru.hackathon.credit.document.ApplicationDocumentRepository;
import ru.hackathon.credit.document.DocumentService;
import ru.hackathon.credit.offer.OfferExplanation;
import ru.hackathon.credit.offer.OfferRepository;
import ru.hackathon.credit.offer.OfferService;
import ru.hackathon.credit.pricing.PricingCalculator;
import ru.hackathon.credit.product.Product;
import ru.hackathon.credit.product.ProductRepository;
import ru.hackathon.credit.product.ProductService;
import ru.hackathon.credit.product.Purpose;
import ru.hackathon.credit.scoring.ExternalCheckResultRepository;
import ru.hackathon.credit.scoring.Grade;
import ru.hackathon.credit.scoring.ScoringRun;
import ru.hackathon.credit.scoring.ScoringRunRepository;
import ru.hackathon.credit.scoring.ScoringService;
import ru.hackathon.credit.task.BankDtos.ApplicationInfo;
import ru.hackathon.credit.task.BankDtos.CheckView;
import ru.hackathon.credit.task.BankDtos.DecisionRequest;
import ru.hackathon.credit.task.BankDtos.ExternalCheckView;
import ru.hackathon.credit.task.BankDtos.IndicativeTerms;
import ru.hackathon.credit.task.BankDtos.RecalculateRequest;
import ru.hackathon.credit.task.BankDtos.RecalculateResponse;
import ru.hackathon.credit.task.BankDtos.ScoringRunView;
import ru.hackathon.credit.task.BankDtos.TaskCard;
import ru.hackathon.credit.task.BankDtos.TaskListItem;

/**
 * Рабочее место андеррайтера: очередь, карточка заявки, пересчёт скоринга и решения по правилам docs/CREDIT_RULES.md, раздел 5.
 */
@Service
public class UnderwritingService {

    public static final String APPROVE = "APPROVE";
    public static final String APPROVE_WITH_CHANGES = "APPROVE_WITH_CHANGES";
    public static final String REQUEST_INFO = "REQUEST_INFO";
    public static final String DECLINE = "DECLINE";
    public static final String POSTPONE = "POSTPONE";

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final Set<String> ACTIVE_STATUSES = Set.of(BankTask.NEW, BankTask.IN_PROGRESS, BankTask.POSTPONED);

    private final BankTaskRepository tasks;
    private final TaskService taskService;
    private final BankDecisionRepository decisions;
    private final ApplicationRepository applications;
    private final CompanyRepository companyRepository;
    private final CompanyService companies;
    private final ProductRepository productRepository;
    private final ProductService products;
    private final AppUserRepository users;
    private final ScoringRunRepository runs;
    private final ScoringService scoring;
    private final ExternalCheckResultRepository externalChecks;
    private final ApplicationDocumentRepository documentRepository;
    private final DocumentService documents;
    private final StatusHistoryRepository history;
    private final InfoRequestRepository infoRequests;
    private final OfferRepository offerRepository;
    private final OfferService offers;
    private final ApplicationViews views;
    private final StatusTransitionService transitions;
    private final CreditDecisionEngine engine;
    private final PricingCalculator pricing;
    private final AuditService audit;
    private final Json json;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public UnderwritingService(BankTaskRepository tasks, TaskService taskService, BankDecisionRepository decisions,
                               ApplicationRepository applications, CompanyRepository companyRepository,
                               CompanyService companies, ProductRepository productRepository, ProductService products,
                               AppUserRepository users, ScoringRunRepository runs, ScoringService scoring,
                               ExternalCheckResultRepository externalChecks,
                               ApplicationDocumentRepository documentRepository, DocumentService documents,
                               StatusHistoryRepository history, InfoRequestRepository infoRequests,
                               OfferRepository offerRepository, OfferService offers, ApplicationViews views,
                               StatusTransitionService transitions, CreditDecisionEngine engine,
                               PricingCalculator pricing, AuditService audit, Json json,
                               io.micrometer.core.instrument.MeterRegistry metrics) {
        this.metrics = metrics;
        this.tasks = tasks;
        this.taskService = taskService;
        this.decisions = decisions;
        this.applications = applications;
        this.companyRepository = companyRepository;
        this.companies = companies;
        this.productRepository = productRepository;
        this.products = products;
        this.users = users;
        this.runs = runs;
        this.scoring = scoring;
        this.externalChecks = externalChecks;
        this.documentRepository = documentRepository;
        this.documents = documents;
        this.history = history;
        this.infoRequests = infoRequests;
        this.offerRepository = offerRepository;
        this.offers = offers;
        this.views = views;
        this.transitions = transitions;
        this.engine = engine;
        this.pricing = pricing;
        this.audit = audit;
        this.json = json;
    }

    // ---------- Очередь ----------

    @Transactional(readOnly = true)
    public List<TaskListItem> list(String filter, Long userId) {
        String f = filter == null ? "active" : filter;
        List<BankTask> source = switch (f) {
            case "new" -> tasks.findByStatusInOrderByPriorityAscCreatedAtAsc(Set.of(BankTask.NEW));
            case "postponed" -> tasks.findByStatusInOrderByPriorityAscCreatedAtAsc(Set.of(BankTask.POSTPONED));
            case "done" -> tasks.findByStatusInOrderByPriorityAscCreatedAtAsc(Set.of(BankTask.DONE));
            default -> tasks.findByStatusInOrderByPriorityAscCreatedAtAsc(ACTIVE_STATUSES);
        };
        if ("mine".equals(f)) {
            source = source.stream().filter(t -> userId.equals(t.getAssigneeUserId())).toList();
        }
        Map<Long, AppUser> userById = users.findAll().stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<String, Product> productByCode = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity()));
        List<TaskListItem> result = new ArrayList<>();
        for (BankTask t : source) {
            LoanApplication app = applications.findById(t.getApplicationId()).orElseThrow();
            Company company = companyRepository.findById(app.getCompanyId()).orElseThrow();
            result.add(item(t, app, company, productByCode.get(app.getProductCode()), userById, userId));
        }
        return result;
    }

    @Transactional
    public TaskCard takeNext(Long userId) {
        BankTask task = taskService.takeNext(userId);
        return card(task.getId(), userId);
    }

    @Transactional
    public TaskCard take(Long taskId, Long userId) {
        taskService.take(taskId, userId);
        return card(taskId, userId);
    }

    @Transactional
    public TaskCard updateCheck(Long taskId, String code, String status, String comment, Long userId) {
        taskService.updateCheck(taskId, code, status, comment, userId);
        return card(taskId, userId);
    }

    // ---------- Карточка ----------

    @Transactional(readOnly = true)
    public TaskCard card(Long taskId, Long userId) {
        BankTask task = taskService.get(taskId);
        LoanApplication app = applications.findById(task.getApplicationId()).orElseThrow();
        Company company = companies.get(app.getCompanyId());
        Product product = products.get(app.getProductCode());
        Map<Long, AppUser> userById = users.findAll().stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));

        List<CheckView> checks = taskService.checks(taskId).stream()
                .map(c -> new CheckView(c.getCheckCode(), c.getTitle(), c.getGoal(), c.getStatus(), c.getComment(),
                        c.getUpdatedAt()))
                .toList();
        List<ScoringRunView> runViews = runs.findByApplicationIdOrderByRunNoAsc(app.getId()).stream()
                .map(this::runView).toList();
        List<Long> taskIds = tasks.findByApplicationIdOrderByIdAsc(app.getId()).stream().map(BankTask::getId).toList();
        List<BankDtos.DecisionView> decisionViews = decisions.findByTaskIdInOrderByIdAsc(taskIds).stream()
                .map(d -> new BankDtos.DecisionView(d.getResult(), resultLabel(d.getResult()), d.getComment(),
                        userById.containsKey(d.getDecidedBy()) ? userById.get(d.getDecidedBy()).getFullName() : null,
                        d.getCreatedAt(), d.getCounterOffer() == null ? null : readMap(d.getCounterOffer())))
                .toList();
        List<HistoryEntry> hist = history.findByApplicationIdOrderByIdAsc(app.getId()).stream()
                .map(h -> new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getToStatus().label(), h.getActorType(),
                        bankActorLabel(h.getActorType(), h.getActorId(), userById), h.getComment(), h.getCreatedAt()))
                .toList();
        List<InfoRequestView> requests = infoRequests.findByApplicationIdOrderByIdAsc(app.getId()).stream()
                .map(r -> new InfoRequestView(r.getId(), r.getSource(), r.getText(), r.getStatus(), r.getClientAnswer(),
                        r.getCreatedAt(), r.getAnsweredAt()))
                .toList();
        List<ExternalCheckView> external = externalChecks.findByApplicationIdOrderByIdAsc(app.getId()).stream()
                .map(e -> new ExternalCheckView(e.getSource(), sourceLabel(e.getSource()), e.getStatus(),
                        readMap(e.getPayload()), e.getReceivedAt()))
                .toList();
        // Показываем только последние ответы источников, чтобы карточка не разрасталась после пересчётов
        Map<String, ExternalCheckView> latestBySource = new LinkedHashMap<>();
        external.forEach(e -> latestBySource.put(e.source(), e));

        boolean canAct = BankTask.IN_PROGRESS.equals(task.getStatus()) && userId.equals(task.getAssigneeUserId())
                && app.getStatus() == ApplicationStatus.MANUAL_REVIEW;
        List<TaskCheck> rawChecks = taskService.checks(taskId);

        ApplicationInfo info = new ApplicationInfo(app.getId(), app.getNumber(), app.getStatus(), app.getStatus().label(),
                product.getCode(), product.getName(), product.isRequiresCollateral(), product.getMinAmount(),
                product.getMaxAmount(), product.getMinTermMonths(), product.getMaxTermMonths(),
                Purpose.valueOf(app.getPurpose()).label(), app.getRequestedAmount(), app.getRequestedTermMonths(),
                app.getMonthlyObligationsDeclared(), app.getCollateralDescription(), app.getSubmittedAt(),
                app.getDecisionSummary());

        return new TaskCard(item(task, app, company, product, userById, userId), info,
                ClientProfileController.toDto(company, companies.financials(company.getId())),
                documentRepository.findByApplicationIdOrderByIdAsc(app.getId()).stream()
                        .map(ApplicationViews::documentView).toList(),
                runViews, checks, decisionViews, hist, requests, new ArrayList<>(latestBySource.values()),
                app.getCurrentOfferId() == null ? null
                        : offerRepository.findById(app.getCurrentOfferId()).map(views::offerView).orElse(null),
                canAct, canAct ? allowedResults(rawChecks) : List.of());
    }

    @Transactional(readOnly = true)
    public DocumentService.DownloadedFile download(Long applicationId, Long documentId) {
        return documents.download(applicationId, documentId);
    }

    // ---------- Пересчёт ----------

    @Transactional
    public RecalculateResponse recalculate(Long taskId, RecalculateRequest r, Long userId) {
        BankTask task = taskService.requireOwnInProgress(taskId, userId);
        LoanApplication app = applications.findById(task.getApplicationId()).orElseThrow();
        StatusTransitionService.requireStatus(app, ApplicationStatus.MANUAL_REVIEW);
        Product product = products.get(app.getProductCode());
        BigDecimal amount = r.amount() != null ? r.amount() : app.getRequestedAmount();
        int term = r.termMonths() != null ? r.termMonths() : app.getRequestedTermMonths();
        products.validateForCompany(product, amount, term, app.getCompanyId());

        ScoringService.Run run = scoring.run(app, product, ScoringService.RECALC,
                new ScoringService.Overrides(r.confirmedAnnualTurnover(), r.amount(), r.termMonths()), "BANK:" + userId);
        audit.log(app.getId(), "BANK", userId, "SCORING_RECALCULATED", "Запуск " + run.entity().getRunNo());

        DecisionResult indicative = engine.decide(new DecisionInput(run.outcome(), product.getName(),
                product.getSpreadPct().doubleValue(), false, null, product.getMinAmount().doubleValue(), run.amount(),
                run.termMonths(), run.monthlyObligations(), run.avgMonthlyTurnover(), run.unavailableSources(), null));
        IndicativeTerms terms;
        if (indicative.outcome() == DecisionResult.Outcome.DECLINE) {
            terms = new IndicativeTerms(indicative.outcome().name(), "Рекомендован отказ", indicative.summary(),
                    null, null, null, null);
        } else {
            double offerAmount = indicative.offerAmount() != null ? indicative.offerAmount() : run.amount();
            PricingCalculator.Quote q = pricing.quote(offerAmount, run.termMonths(),
                    product.getSpreadPct().doubleValue(), run.outcome().grade());
            terms = new IndicativeTerms(indicative.outcome().name(), outcomeLabel(indicative.outcome()),
                    indicative.summary(), q.amount(), q.termMonths(), q.ratePct(), q.monthlyPayment());
        }
        return new RecalculateResponse(runView(run.entity()), terms);
    }

    // ---------- Решение ----------

    @Transactional
    public TaskCard decide(Long taskId, DecisionRequest r, Long userId) {
        BankTask task = taskService.requireOwnInProgress(taskId, userId);
        LoanApplication app = applications.findById(task.getApplicationId()).orElseThrow();
        StatusTransitionService.requireStatus(app, ApplicationStatus.MANUAL_REVIEW);
        List<TaskCheck> checks = taskService.checks(taskId);
        String result = r.result();
        validateDecision(result, checks);
        metrics.counter("credit.bank.decisions", "result", result).increment();

        Product product = products.get(app.getProductCode());
        String comment = r.comment() == null ? null : r.comment().trim();
        BankDecision decision = new BankDecision();
        decision.setTaskId(taskId);
        decision.setResult(result);
        decision.setComment(comment);
        decision.setDecidedBy(userId);

        switch (result) {
            case APPROVE -> {
                ScoringRun run = latestRun(app);
                Map<String, Object> inputs = scoring.inputs(run);
                // Сумма и срок берутся из последнего запуска скоринга: андеррайтер мог изменить их при пересчёте
                double amount = inputs.get("requestedAmount") instanceof Number n
                        ? n.doubleValue() : app.getRequestedAmount().doubleValue();
                int term = inputs.get("termMonths") instanceof Number n2 ? n2.intValue() : app.getRequestedTermMonths();
                List<String> reasons = new ArrayList<>();
                reasons.add("Оценка риска: грейд " + run.getGrade());
                if (comment != null && !comment.isBlank()) {
                    reasons.add("Комментарий специалиста: " + comment);
                }
                List<String> changes = changes(app, amount, term);
                offers.create(app, amount, term, product.getSpreadPct().doubleValue(), Grade.valueOf(run.getGrade()),
                        new OfferExplanation("Одобрено специалистом банка", reasons, changes), "BANK");
                app.setDecisionReasonCode("BANK_APPROVE");
                app.setDecisionSummary("Одобрено специалистом банка");
                transitions.transition(app, ApplicationStatus.OFFER_MADE, ActorType.BANK, userId,
                        "Специалист банка одобрил заявку");
            }
            case APPROVE_WITH_CHANGES -> {
                if (r.counterOffer() == null) {
                    throw ApiException.unprocessable("COUNTER_OFFER_REQUIRED", "Укажите сумму и срок встречного предложения");
                }
                products.validateForCompany(product, r.counterOffer().amount(), r.counterOffer().termMonths(),
                        app.getCompanyId());
                ScoringRun run = latestRun(app);
                double amount = r.counterOffer().amount().doubleValue();
                int term = r.counterOffer().termMonths();
                List<String> reasons = new ArrayList<>();
                reasons.add("Оценка риска: грейд " + run.getGrade());
                if (comment != null && !comment.isBlank()) {
                    reasons.add("Почему изменены условия: " + comment);
                }
                offers.create(app, amount, term, product.getSpreadPct().doubleValue(), Grade.valueOf(run.getGrade()),
                        new OfferExplanation("Специалист банка предложил другие условия", reasons,
                                changes(app, amount, term)), "BANK");
                decision.setCounterOffer(json.write(Map.of("amount", amount, "termMonths", term)));
                app.setDecisionReasonCode("BANK_COUNTER");
                app.setDecisionSummary("Специалист банка предложил другие условия");
                transitions.transition(app, ApplicationStatus.OFFER_MADE, ActorType.BANK, userId,
                        "Специалист банка одобрил заявку с изменёнными условиями");
            }
            case REQUEST_INFO -> {
                String text = r.requestText() != null && !r.requestText().isBlank() ? r.requestText().trim() : comment;
                if (text == null || text.isBlank()) {
                    throw ApiException.unprocessable("REQUEST_TEXT_REQUIRED", "Напишите, какие данные нужны от клиента");
                }
                InfoRequest req = new InfoRequest();
                req.setApplicationId(app.getId());
                req.setTaskId(taskId);
                req.setSource(InfoRequest.SOURCE_BANK);
                req.setStatus(InfoRequest.OPEN);
                req.setText(text);
                infoRequests.save(req);
                transitions.transition(app, ApplicationStatus.NEED_INFO, ActorType.BANK, userId,
                        "Специалист банка запросил дополнительные данные");
            }
            case DECLINE -> {
                if (comment == null || comment.isBlank()) {
                    throw ApiException.unprocessable("DECLINE_REASON_REQUIRED", "Укажите причину отказа");
                }
                app.setDecisionReasonCode("BANK_DECLINE");
                app.setDecisionSummary("Отказ по решению специалиста банка: " + comment);
                transitions.transition(app, ApplicationStatus.DECLINED, ActorType.BANK, userId,
                        "Отказ специалиста банка: " + comment);
            }
            case POSTPONE -> {
                task.setStatus(BankTask.POSTPONED);
                decisions.save(decision);
                transitions.transition(app, ApplicationStatus.MANUAL_REVIEW, ActorType.BANK, userId,
                        "Задача отложена" + (comment == null || comment.isBlank() ? "" : ": " + comment));
                return card(taskId, userId);
            }
            default -> throw ApiException.badRequest("UNKNOWN_RESULT", "Неизвестное решение");
        }

        decisions.save(decision);
        task.setStatus(BankTask.DONE);
        task.setFinishedAt(Instant.now());
        documents.markSeenByBank(app.getId());
        audit.log(app.getId(), "BANK", userId, "BANK_DECISION", result + (comment == null ? "" : ": " + comment));
        return card(taskId, userId);
    }

    /** Правила завершения задачи (docs/CREDIT_RULES.md, раздел 5). */
    static List<String> allowedResults(List<TaskCheck> checks) {
        boolean pending = checks.stream().anyMatch(c -> TaskCheck.PENDING.equals(c.getStatus()));
        boolean negative = checks.stream().anyMatch(c -> TaskCheck.NEGATIVE.equals(c.getStatus()));
        boolean needsInfo = checks.stream().anyMatch(c -> TaskCheck.NEEDS_INFO.equals(c.getStatus()));
        if (negative) {
            return List.of(DECLINE, APPROVE_WITH_CHANGES, POSTPONE);
        }
        if (pending) {
            return List.of(POSTPONE);
        }
        if (needsInfo) {
            return List.of(REQUEST_INFO, DECLINE, POSTPONE);
        }
        return List.of(APPROVE, APPROVE_WITH_CHANGES, REQUEST_INFO, DECLINE, POSTPONE);
    }

    static void validateDecision(String result, List<TaskCheck> checks) {
        if (!Set.of(APPROVE, APPROVE_WITH_CHANGES, REQUEST_INFO, DECLINE, POSTPONE).contains(result)) {
            throw ApiException.badRequest("UNKNOWN_RESULT", "Неизвестное решение");
        }
        if (allowedResults(checks).contains(result)) {
            return;
        }
        boolean negative = checks.stream().anyMatch(c -> TaskCheck.NEGATIVE.equals(c.getStatus()));
        boolean pending = checks.stream().anyMatch(c -> TaskCheck.PENDING.equals(c.getStatus()));
        if (negative) {
            throw ApiException.unprocessable("NEGATIVE_CHECK",
                    "Есть отрицательная проверка: доступны только отказ, встречное предложение или отложить");
        }
        if (pending) {
            throw ApiException.unprocessable("CHECKS_INCOMPLETE",
                    "Заполните все проверки, прежде чем принять решение. Отложить задачу можно без проверок");
        }
        throw ApiException.unprocessable("CHECK_NEEDS_INFO",
                "Есть проверка с результатом «нужны данные»: запросите данные у клиента или измените результат проверки");
    }

    private List<String> changes(LoanApplication app, double amount, int term) {
        List<String> changes = new ArrayList<>();
        if (app.getRequestedAmount().compareTo(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)) != 0) {
            changes.add(String.format(RU, "Сумма: %,.0f ₽ вместо запрошенных %,.0f ₽", amount, app.getRequestedAmount()));
        }
        if (app.getRequestedTermMonths() != term) {
            changes.add("Срок: " + term + " мес. вместо запрошенных " + app.getRequestedTermMonths() + " мес.");
        }
        return changes;
    }

    private ScoringRun latestRun(LoanApplication app) {
        return runs.findTopByApplicationIdOrderByRunNoDesc(app.getId())
                .orElseThrow(() -> ApiException.conflict("NO_SCORING", "По заявке нет результата скоринга"));
    }

    private ScoringRunView runView(ScoringRun r) {
        return new ScoringRunView(r.getRunNo(), r.getTotalScore(), r.getGrade(), r.isHardStop(), r.getHardStopReason(),
                r.getPaymentLoad(), r.getTrigger(), r.getCreatedBy(), r.getCreatedAt(), scoring.factors(r),
                scoring.inputs(r));
    }

    private TaskListItem item(BankTask t, LoanApplication app, Company company, Product product,
                              Map<Long, AppUser> userById, Long userId) {
        AppUser assignee = t.getAssigneeUserId() == null ? null : userById.get(t.getAssigneeUserId());
        String grade = runs.findTopByApplicationIdOrderByRunNoDesc(app.getId()).map(ScoringRun::getGrade).orElse(null);
        boolean overdue = !BankTask.DONE.equals(t.getStatus()) && t.getSlaDueAt().isBefore(Instant.now());
        return new TaskListItem(t.getId(), app.getId(), app.getNumber(), company.getName(), company.getLegalForm(),
                product.getCode(), product.getName(), app.getRequestedAmount(), app.getRequestedTermMonths(),
                t.getPriority(), t.getStatus(), taskStatusLabel(t.getStatus()), t.getAssigneeUserId(),
                assignee == null ? null : assignee.getFullName(), userId.equals(t.getAssigneeUserId()),
                t.getSlaDueAt(), overdue, t.getCreatedAt(), t.isRepeat(), t.getReasonCodes(), grade, app.getStatus());
    }

    private Map<String, Object> readMap(String jsonValue) {
        return json.read(jsonValue, new TypeReference<Map<String, Object>>() { });
    }

    static String bankActorLabel(ActorType type, Long actorId, Map<Long, AppUser> users) {
        return switch (type) {
            case CLIENT -> "Клиент";
            case SYSTEM -> "Система";
            case BANK -> actorId != null && users.containsKey(actorId) ? users.get(actorId).getFullName() : "Банк";
        };
    }

    static String taskStatusLabel(String status) {
        return switch (status) {
            case BankTask.NEW -> "Новая";
            case BankTask.IN_PROGRESS -> "В работе";
            case BankTask.POSTPONED -> "Отложена";
            case BankTask.DONE -> "Завершена";
            default -> status;
        };
    }

    static String resultLabel(String result) {
        return switch (result) {
            case APPROVE -> "Одобрить";
            case APPROVE_WITH_CHANGES -> "Одобрить с изменениями";
            case REQUEST_INFO -> "Запросить данные";
            case DECLINE -> "Отказать";
            case POSTPONE -> "Отложить";
            default -> result;
        };
    }

    static String outcomeLabel(DecisionResult.Outcome o) {
        return switch (o) {
            case AUTO_APPROVE -> "Можно одобрить на этих условиях";
            case AUTO_COUNTER -> "Можно одобрить на меньшую сумму";
            case DECLINE -> "Рекомендован отказ";
            case MANUAL_REVIEW -> "Нужна оценка специалиста";
        };
    }

    static String sourceLabel(String source) {
        return switch (source) {
            case "REGISTRY" -> "Реестр компаний";
            case "BUREAU" -> "Бюро кредитных историй";
            case "TAX" -> "ФНС";
            default -> source;
        };
    }
}
