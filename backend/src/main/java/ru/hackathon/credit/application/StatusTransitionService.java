package ru.hackathon.credit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.audit.AuditService;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.feed.FeedService;

/**
 * Единственная точка смены статуса заявки. Переход проверяется по машине состояний и в той же транзакции
 * записывается в историю, журнал аудита и ленту клиента.
 */
@Service
public class StatusTransitionService {

    private final ApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final AuditService audit;
    private final FeedService feed;

    public StatusTransitionService(ApplicationRepository applications, StatusHistoryRepository history,
                                   AuditService audit, FeedService feed) {
        this.applications = applications;
        this.history = history;
        this.audit = audit;
        this.feed = feed;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LoanApplication transition(LoanApplication app, ApplicationStatus to, ActorType actor, Long actorId,
                                      String comment) {
        ApplicationStatus from = app.getStatus();
        if (from != null && !from.canMoveTo(to)) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "Заявка в статусе «" + from.label() + "» не может перейти в статус «" + to.label() + "»");
        }
        app.setStatus(to);
        LoanApplication saved = applications.saveAndFlush(app);

        StatusHistory h = new StatusHistory();
        h.setApplicationId(saved.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorType(actor);
        h.setActorId(actorId);
        h.setComment(comment);
        history.save(h);

        audit.log(saved.getId(), actor.name(), actorId, "STATUS_CHANGED",
                (from == null ? "—" : from.name()) + " -> " + to.name() + (comment == null ? "" : ": " + comment));
        feed.onStatusChanged(saved, from, to);
        return saved;
    }

    /** Первая запись истории для только что созданного черновика. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreation(LoanApplication app, ActorType actor, Long actorId) {
        StatusHistory h = new StatusHistory();
        h.setApplicationId(app.getId());
        h.setToStatus(app.getStatus());
        h.setActorType(actor);
        h.setActorId(actorId);
        h.setComment("Создан черновик");
        history.save(h);
        audit.log(app.getId(), actor.name(), actorId, "APPLICATION_CREATED", app.getNumber());
    }

    /** Проверка без перехода: заявка должна быть в одном из перечисленных статусов. */
    public static void requireStatus(LoanApplication app, ApplicationStatus... allowed) {
        for (ApplicationStatus s : allowed) {
            if (app.getStatus() == s) {
                return;
            }
        }
        throw ApiException.conflict("INVALID_STATUS",
                "Действие недоступно: заявка в статусе «" + app.getStatus().label() + "»");
    }
}
