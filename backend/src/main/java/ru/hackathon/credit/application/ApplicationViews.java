package ru.hackathon.credit.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ru.hackathon.credit.application.ApplicationDtos.ApplicationDetail;
import ru.hackathon.credit.application.ApplicationDtos.ApplicationSummary;
import ru.hackathon.credit.application.ApplicationDtos.DecisionView;
import ru.hackathon.credit.application.ApplicationDtos.DisbursementView;
import ru.hackathon.credit.application.ApplicationDtos.DocumentView;
import ru.hackathon.credit.application.ApplicationDtos.HistoryEntry;
import ru.hackathon.credit.application.ApplicationDtos.InfoRequestView;
import ru.hackathon.credit.application.ApplicationDtos.NextStep;
import ru.hackathon.credit.application.ApplicationDtos.OfferView;
import ru.hackathon.credit.application.ApplicationDtos.SigningView;
import ru.hackathon.credit.document.ApplicationDocument;
import ru.hackathon.credit.document.ApplicationDocumentRepository;
import ru.hackathon.credit.offer.Disbursement;
import ru.hackathon.credit.offer.DisbursementRepository;
import ru.hackathon.credit.offer.Offer;
import ru.hackathon.credit.offer.OfferExplanation;
import ru.hackathon.credit.offer.OfferRepository;
import ru.hackathon.credit.offer.OfferService;
import ru.hackathon.credit.offer.SigningSession;
import ru.hackathon.credit.offer.SigningSessionRepository;
import ru.hackathon.credit.product.Product;
import ru.hackathon.credit.product.ProductRepository;
import ru.hackathon.credit.product.Purpose;
import ru.hackathon.credit.scoring.ScoringRun;
import ru.hackathon.credit.scoring.ScoringRunRepository;
import ru.hackathon.credit.scoring.ScoringService;

/**
 * Сборка представлений заявки: статус простыми словами, следующий шаг, пояснение решения, история.
 */
@Component
public class ApplicationViews {

    private final ProductRepository products;
    private final StatusHistoryRepository history;
    private final InfoRequestRepository infoRequests;
    private final ApplicationDocumentRepository documents;
    private final OfferRepository offers;
    private final OfferService offerService;
    private final ScoringRunRepository scoringRuns;
    private final ScoringService scoring;
    private final SigningSessionRepository signingSessions;
    private final DisbursementRepository disbursements;

    public ApplicationViews(ProductRepository products, StatusHistoryRepository history,
                            InfoRequestRepository infoRequests, ApplicationDocumentRepository documents,
                            OfferRepository offers, OfferService offerService, ScoringRunRepository scoringRuns,
                            ScoringService scoring, SigningSessionRepository signingSessions,
                            DisbursementRepository disbursements) {
        this.products = products;
        this.history = history;
        this.infoRequests = infoRequests;
        this.documents = documents;
        this.offers = offers;
        this.offerService = offerService;
        this.scoringRuns = scoringRuns;
        this.scoring = scoring;
        this.signingSessions = signingSessions;
        this.disbursements = disbursements;
    }

    public List<ApplicationSummary> summaries(List<LoanApplication> apps) {
        Map<String, Product> byCode = products.findAll().stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity()));
        return apps.stream().map(a -> new ApplicationSummary(a.getId(), a.getNumber(), a.getStatus(),
                a.getStatus().label(), a.getProductCode(), byCode.get(a.getProductCode()).getName(),
                a.getRequestedAmount(), a.getRequestedTermMonths(), a.getCreatedAt(), a.getUpdatedAt(),
                nextStep(a.getStatus()))).toList();
    }

    public ApplicationDetail detail(LoanApplication a) {
        Product product = products.findById(a.getProductCode()).orElseThrow();
        Purpose purpose = Purpose.valueOf(a.getPurpose());

        List<HistoryEntry> hist = history.findByApplicationIdOrderByIdAsc(a.getId()).stream()
                .map(h -> new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getToStatus().label(),
                        h.getActorType(), actorLabel(h.getActorType()), h.getComment(), h.getCreatedAt()))
                .toList();

        List<InfoRequestView> requests = infoRequests.findByApplicationIdOrderByIdAsc(a.getId()).stream()
                .map(r -> new InfoRequestView(r.getId(), r.getSource(), r.getText(), r.getStatus(),
                        r.getClientAnswer(), r.getCreatedAt(), r.getAnsweredAt()))
                .toList();

        List<DocumentView> docs = documents.findByApplicationIdOrderByIdAsc(a.getId()).stream()
                .map(ApplicationViews::documentView)
                .toList();

        OfferView offer = a.getCurrentOfferId() == null ? null
                : offers.findById(a.getCurrentOfferId()).map(this::offerView).orElse(null);

        return new ApplicationDetail(a.getId(), a.getNumber(), a.getStatus(), a.getStatus().label(),
                a.getStatus().isFinal(), a.getProductCode(), product.getName(), product.isRequiresCollateral(),
                a.getPurpose(), purpose.label(), a.getRequestedAmount(), a.getRequestedTermMonths(),
                a.getMonthlyObligationsDeclared(), a.getCollateralDescription(), a.isConsentDataProcessing(),
                a.isConsentCreditBureau(), a.getParentApplicationId(), a.getCreatedAt(), a.getUpdatedAt(),
                a.getSubmittedAt(), nextStep(a.getStatus()), decisionView(a), offer, hist, requests, docs,
                signingView(a), disbursementView(a));
    }

    public OfferView offerView(Offer o) {
        OfferExplanation e = offerService.explanation(o);
        return new OfferView(o.getId(), o.getKind(), o.getAmount(), o.getTermMonths(), o.getRatePct(),
                o.getMonthlyPayment(), o.getTotalPayment(), o.getOverpayment(), o.getStatus(), e.summary(),
                e.reasons(), e.changes(), o.getCreatedAt());
    }

    public static DocumentView documentView(ApplicationDocument d) {
        return new DocumentView(d.getId(), d.getDocType(), d.getFileName(), d.getSizeBytes(), d.getUploadedByRole(),
                d.getUploadedAt(), d.isNewForBank());
    }

    private DecisionView decisionView(LoanApplication a) {
        if (a.getDecisionSummary() == null) {
            return null;
        }
        ScoringRun run = scoringRuns.findTopByApplicationIdOrderByRunNoDesc(a.getId()).orElse(null);
        if (run == null) {
            return new DecisionView(a.getDecisionSummary(), a.getDecisionReasonCode(), null, null, null, List.of());
        }
        return new DecisionView(a.getDecisionSummary(), a.getDecisionReasonCode(), run.getGrade(), run.getTotalScore(),
                run.getHardStopReason(), scoring.factors(run));
    }

    private SigningView signingView(LoanApplication a) {
        if (a.getStatus() != ApplicationStatus.SIGNING && a.getStatus() != ApplicationStatus.ISSUED) {
            return null;
        }
        SigningSession s = signingSessions.findTopByApplicationIdOrderByIdDesc(a.getId()).orElse(null);
        if (s == null) {
            return new SigningView(false, 0, false, false);
        }
        return new SigningView(true, s.getAttemptsLeft(), SigningSession.BLOCKED.equals(s.getStatus()),
                SigningSession.CONFIRMED.equals(s.getStatus()));
    }

    private DisbursementView disbursementView(LoanApplication a) {
        Disbursement d = disbursements.findByApplicationId(a.getId()).orElse(null);
        return d == null ? null
                : new DisbursementView(d.getAmount(), d.getAccountMasked(), d.getExecutedAt(), d.isSimulated());
    }

    static String actorLabel(ActorType t) {
        return switch (t) {
            case CLIENT -> "Вы";
            case BANK -> "Специалист банка";
            case SYSTEM -> "Система";
        };
    }

    static NextStep nextStep(ApplicationStatus s) {
        return switch (s) {
            case DRAFT -> new NextStep("Заполните и отправьте заявку",
                    "Черновик сохраняется автоматически, можно вернуться к нему позже.", "CONTINUE_DRAFT");
            case SUBMITTED, SCORING -> new NextStep("Проверяем заявку",
                    "Банк проверяет данные и оценивает заявку. Обычно это занимает меньше минуты, страница обновится сама.",
                    "WAIT");
            case NEED_INFO -> new NextStep("Ответьте на запрос банка",
                    "Без этих данных банк не может продолжить рассмотрение. Ответьте ниже и при необходимости приложите документы.",
                    "ANSWER_REQUEST");
            case MANUAL_REVIEW -> new NextStep("Заявку проверяет специалист",
                    "Обычно это занимает несколько часов. Если понадобятся данные, запрос появится здесь.", "WAIT");
            case OFFER_MADE -> new NextStep("Посмотрите предложение",
                    "Банк готов выдать кредит. Проверьте условия и примите решение.", "REVIEW_OFFER");
            case SIGNING -> new NextStep("Подпишите договор",
                    "Запросите код подтверждения и введите его, чтобы подписать договор.", "SIGN");
            case ISSUED -> new NextStep("Кредит выдан", "Средства зачислены на расчётный счёт (симуляция).", "NONE");
            case DECLINED -> new NextStep("Можно подать новую заявку",
                    "Причины решения указаны ниже. Новую заявку можно подать, когда ситуация изменится.", "NEW_APPLICATION");
            case CLOSED -> new NextStep("Заявка закрыта", "Вы отказались от предложения. Можно подать новую заявку.",
                    "NEW_APPLICATION");
        };
    }
}
