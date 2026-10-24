package ru.hackathon.credit.decision;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.application.ActorType;
import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.application.StatusTransitionService;
import ru.hackathon.credit.offer.OfferExplanation;
import ru.hackathon.credit.offer.OfferService;
import ru.hackathon.credit.product.Product;
import ru.hackathon.credit.scoring.ScoringService;
import ru.hackathon.credit.task.BankTaskRepository;
import ru.hackathon.credit.task.TaskService;

/**
 * Применяет кредитную логику к результату скоринга: предложение, отказ или задача андеррайтеру.
 */
@Service
public class DecisionService {

    private final CreditDecisionEngine engine;
    private final OfferService offers;
    private final TaskService tasks;
    private final BankTaskRepository taskRepository;
    private final StatusTransitionService transitions;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public DecisionService(CreditDecisionEngine engine, OfferService offers, TaskService tasks,
                           BankTaskRepository taskRepository, StatusTransitionService transitions,
                           io.micrometer.core.instrument.MeterRegistry metrics) {
        this.metrics = metrics;
        this.engine = engine;
        this.offers = offers;
        this.tasks = tasks;
        this.taskRepository = taskRepository;
        this.transitions = transitions;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DecisionResult decideAndApply(LoanApplication app, Product product, ScoringService.Run run) {
        // Если заявку уже смотрел специалист, после ответа клиента она возвращается к нему, а не решается автоматически
        String forceManual = taskRepository.findByApplicationIdOrderByIdAsc(app.getId()).isEmpty()
                ? null : "Клиент ответил на запрос специалиста, заявка возвращена на ручную проверку";

        DecisionResult d = engine.decide(new DecisionInput(run.outcome(), product.getName(),
                product.getSpreadPct().doubleValue(), product.alwaysManual(),
                product.getAutoLimitAmount() == null ? null : product.getAutoLimitAmount().doubleValue(),
                product.getMinAmount().doubleValue(), run.amount(), run.termMonths(), run.monthlyObligations(),
                run.avgMonthlyTurnover(), run.unavailableSources(), forceManual));

        metrics.counter("credit.decisions", "outcome", d.outcome().name(), "reason", d.reasonCode()).increment();
        app.setDecisionReasonCode(d.reasonCode());
        app.setDecisionSummary(d.summary());
        switch (d.outcome()) {
            case AUTO_APPROVE, AUTO_COUNTER -> {
                offers.create(app, d.offerAmount(), d.offerTermMonths(), product.getSpreadPct().doubleValue(),
                        run.outcome().grade(), new OfferExplanation(d.summary(), d.reasons(), d.changes()), "SYSTEM");
                transitions.transition(app, ApplicationStatus.OFFER_MADE, ActorType.SYSTEM, null, d.summary());
            }
            case DECLINE -> transitions.transition(app, ApplicationStatus.DECLINED, ActorType.SYSTEM, null, d.summary());
            case MANUAL_REVIEW -> {
                tasks.create(app, product, run, d.reasonCode());
                transitions.transition(app, ApplicationStatus.MANUAL_REVIEW, ActorType.SYSTEM, null, d.summary());
            }
        }
        return d;
    }

    public static List<String> reasonsOrEmpty(DecisionResult d) {
        return d.reasons() == null ? List.of() : d.reasons();
    }
}
