package ru.hackathon.credit.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.decision.DecisionService;
import ru.hackathon.credit.document.ApplicationDocumentRepository;
import ru.hackathon.credit.product.Product;
import ru.hackathon.credit.product.ProductService;
import ru.hackathon.credit.scoring.ScoringService;

/**
 * Шаги автоматической обработки. Каждый шаг выполняется в своей транзакции и зависит только от текущего статуса,
 * поэтому обработку можно безопасно перезапустить после сбоя.
 */
@Service
public class PipelineSteps {

    private final ApplicationRepository applications;
    private final ApplicationDocumentRepository documents;
    private final InfoRequestRepository infoRequests;
    private final ProductService products;
    private final ScoringService scoring;
    private final DecisionService decisions;
    private final StatusTransitionService transitions;

    public PipelineSteps(ApplicationRepository applications, ApplicationDocumentRepository documents,
                         InfoRequestRepository infoRequests, ProductService products, ScoringService scoring,
                         DecisionService decisions, StatusTransitionService transitions) {
        this.applications = applications;
        this.documents = documents;
        this.infoRequests = infoRequests;
        this.products = products;
        this.scoring = scoring;
        this.decisions = decisions;
        this.transitions = transitions;
    }

    @Transactional
    public ApplicationStatus advance(Long applicationId) {
        LoanApplication app = applications.findById(applicationId).orElseThrow();
        return switch (app.getStatus()) {
            case SUBMITTED -> checkCompleteness(app);
            case SCORING -> scoreAndDecide(app);
            default -> app.getStatus();
        };
    }

    private ApplicationStatus checkCompleteness(LoanApplication app) {
        Product product = products.get(app.getProductCode());
        List<String> missing = missingItems(app, product);
        if (!missing.isEmpty()) {
            InfoRequest r = new InfoRequest();
            r.setApplicationId(app.getId());
            r.setSource(InfoRequest.SOURCE_SYSTEM);
            r.setStatus(InfoRequest.OPEN);
            r.setText("Чтобы продолжить рассмотрение, добавьте: " + String.join("; ", missing) + ".");
            infoRequests.save(r);
            transitions.transition(app, ApplicationStatus.NEED_INFO, ActorType.SYSTEM, null,
                    "Не хватает данных: " + String.join("; ", missing));
            return ApplicationStatus.NEED_INFO;
        }
        transitions.transition(app, ApplicationStatus.SCORING, ActorType.SYSTEM, null,
                "Данные приняты, запрошены сведения из реестра, БКИ и ФНС");
        return ApplicationStatus.SCORING;
    }

    private ApplicationStatus scoreAndDecide(LoanApplication app) {
        Product product = products.get(app.getProductCode());
        ScoringService.Run run = scoring.run(app, product, ScoringService.INITIAL, ScoringService.Overrides.NONE, "SYSTEM");
        decisions.decideAndApply(app, product, run);
        return app.getStatus();
    }

    /** Проверка полноты: что ещё нужно от клиента, чтобы банк мог оценить заявку. */
    List<String> missingItems(LoanApplication app, Product product) {
        List<String> missing = new ArrayList<>();
        if (documents.countByApplicationId(app.getId()) == 0) {
            missing.add("выписку по расчётному счёту или иной документ, подтверждающий обороты");
        }
        if (product.isRequiresCollateral()
                && (app.getCollateralDescription() == null || app.getCollateralDescription().isBlank())) {
            missing.add("описание залога");
        }
        return missing;
    }
}
