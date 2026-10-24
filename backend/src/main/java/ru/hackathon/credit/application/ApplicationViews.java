package ru.hackathon.credit.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ru.hackathon.credit.application.ApplicationDtos.ApplicationDetail;
import ru.hackathon.credit.application.ApplicationDtos.ApplicationSummary;
import ru.hackathon.credit.application.ApplicationDtos.ClientOfferView;
import ru.hackathon.credit.application.ApplicationDtos.DisbursementView;
import ru.hackathon.credit.application.ApplicationDtos.DocumentView;
import ru.hackathon.credit.application.ApplicationDtos.HistoryEntry;
import ru.hackathon.credit.application.ApplicationDtos.InfoRequestView;
import ru.hackathon.credit.application.ApplicationDtos.NextStep;
import ru.hackathon.credit.application.ApplicationDtos.OfferView;
import ru.hackathon.credit.document.ApplicationDocument;
import ru.hackathon.credit.document.ApplicationDocumentRepository;
import ru.hackathon.credit.offer.Disbursement;
import ru.hackathon.credit.offer.DisbursementRepository;
import ru.hackathon.credit.offer.Offer;
import ru.hackathon.credit.offer.OfferExplanation;
import ru.hackathon.credit.offer.OfferRepository;
import ru.hackathon.credit.offer.OfferService;
import ru.hackathon.credit.product.Product;
import ru.hackathon.credit.product.ProductRepository;
import ru.hackathon.credit.product.Purpose;

/**
 * Представления заявки для клиента. Результаты внутренней оценки банка (баллы, грейд, факторы, причины решения)
 * клиенту не показываются: он видит статус, следующий шаг, условия предложения и нейтральную историю.
 * Полный разбор доступен только сотруднику банка в {@code UnderwritingService}.
 */
@Component
public class ApplicationViews {

    private static final Locale RU = Locale.forLanguageTag("ru");

    private final ProductRepository products;
    private final StatusHistoryRepository history;
    private final InfoRequestRepository infoRequests;
    private final ApplicationDocumentRepository documents;
    private final OfferRepository offers;
    private final OfferService offerService;
    private final DisbursementRepository disbursements;

    public ApplicationViews(ProductRepository products, StatusHistoryRepository history,
                            InfoRequestRepository infoRequests, ApplicationDocumentRepository documents,
                            OfferRepository offers, OfferService offerService, DisbursementRepository disbursements) {
        this.products = products;
        this.history = history;
        this.infoRequests = infoRequests;
        this.documents = documents;
        this.offers = offers;
        this.offerService = offerService;
        this.disbursements = disbursements;
    }

    public List<ApplicationSummary> summaries(List<LoanApplication> apps) {
        Map<String, Product> byCode = products.findAll().stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity()));
        return apps.stream().map(a -> new ApplicationSummary(a.getId(), a.getNumber(), a.getStatus(),
                a.getStatus().clientLabel(), a.getProductCode(), byCode.get(a.getProductCode()).getName(),
                a.getRequestedAmount(), a.getRequestedTermMonths(), a.getCreatedAt(), a.getUpdatedAt(),
                nextStep(a.getStatus()))).toList();
    }

    public ApplicationDetail detail(LoanApplication a) {
        Product product = products.findById(a.getProductCode()).orElseThrow();
        Purpose purpose = Purpose.valueOf(a.getPurpose());

        List<HistoryEntry> hist = clientHistory(history.findByApplicationIdOrderByIdAsc(a.getId()));

        List<InfoRequestView> requests = infoRequests.findByApplicationIdOrderByIdAsc(a.getId()).stream()
                .map(r -> new InfoRequestView(r.getId(), r.getSource(), r.getText(), r.getStatus(),
                        r.getClientAnswer(), r.getCreatedAt(), r.getAnsweredAt()))
                .toList();

        List<DocumentView> docs = documents.findByApplicationIdOrderByIdAsc(a.getId()).stream()
                .map(ApplicationViews::documentView)
                .toList();

        ClientOfferView offer = a.getCurrentOfferId() == null ? null
                : offers.findById(a.getCurrentOfferId()).map(o -> clientOfferView(o, a)).orElse(null);

        return new ApplicationDetail(a.getId(), a.getNumber(), a.getStatus(), a.getStatus().clientLabel(),
                a.getStatus().isFinal(), a.getProductCode(), product.getName(), product.isRequiresCollateral(),
                a.getPurpose(), purpose.label(), a.getRequestedAmount(), a.getRequestedTermMonths(),
                a.getMonthlyObligationsDeclared(), a.getCollateralDescription(), a.isConsentDataProcessing(),
                a.isConsentCreditBureau(), a.getParentApplicationId(), a.getCreatedAt(), a.getUpdatedAt(),
                a.getSubmittedAt(), nextStep(a.getStatus()), offer, hist, requests, docs, disbursementView(a));
    }

    /** Предложение для сотрудника банка: с внутренними причинами решения. */
    public OfferView offerView(Offer o) {
        OfferExplanation e = offerService.explanation(o);
        return new OfferView(o.getId(), o.getKind(), o.getAmount(), o.getTermMonths(), o.getRatePct(),
                o.getMonthlyPayment(), o.getTotalPayment(), o.getOverpayment(), o.getStatus(), e.summary(),
                e.reasons(), e.changes(), o.getCreatedAt());
    }

    /** Предложение для клиента: только условия и что изменилось относительно заявки, без причин. */
    static ClientOfferView clientOfferView(Offer o, LoanApplication a) {
        boolean counter = Offer.COUNTER.equals(o.getKind());
        return new ClientOfferView(o.getId(), o.getKind(), o.getAmount(), o.getTermMonths(), o.getRatePct(),
                o.getMonthlyPayment(), o.getTotalPayment(), o.getOverpayment(), o.getStatus(),
                counter ? "Банк готов выдать кредит на других условиях" : "Банк одобрил заявку на запрошенных условиях",
                changes(a, o), o.getCreatedAt());
    }

    static List<String> changes(LoanApplication a, Offer o) {
        List<String> changes = new ArrayList<>();
        if (a.getRequestedAmount().compareTo(o.getAmount()) != 0) {
            changes.add(String.format(RU, "Сумма: %,.0f ₽ вместо %,.0f ₽", o.getAmount(), a.getRequestedAmount()));
        }
        if (a.getRequestedTermMonths() != o.getTermMonths()) {
            changes.add("Срок: " + o.getTermMonths() + " мес. вместо " + a.getRequestedTermMonths() + " мес");
        }
        return changes;
    }

    /** История для клиента: внутренние комментарии банка заменяются нейтральными формулировками. */
    static List<HistoryEntry> clientHistory(List<StatusHistory> entries) {
        List<HistoryEntry> result = new ArrayList<>();
        ApplicationStatus previous = null;
        for (StatusHistory h : entries) {
            if (h.getFromStatus() != null && h.getFromStatus() == h.getToStatus()) {
                continue;
            }
            result.add(new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getToStatus().clientLabel(),
                    h.getActorType(), actorLabel(h.getActorType()), clientComment(h, previous), h.getCreatedAt()));
            previous = h.getToStatus();
        }
        return result;
    }

    static String clientComment(StatusHistory h, ApplicationStatus previous) {
        return switch (h.getToStatus()) {
            case DRAFT -> "Создан черновик";
            case SUBMITTED -> previous == ApplicationStatus.NEED_INFO ? "Вы дополнили заявку" : "Заявка отправлена";
            case NEED_INFO -> "Банк запросил дополнительные данные";
            case SCORING -> "Банк проверяет заявку";
            case MANUAL_REVIEW -> "Заявку рассматривает специалист банка";
            case OFFER_MADE -> "Банк подготовил предложение";
            case SIGNING -> "Вы приняли предложение";
            case ISSUED -> "Договор подписан, средства зачислены на счёт";
            case DECLINED -> "Банк принял решение по заявке";
            case CLOSED -> "Вы отказались от предложения";
        };
    }

    public static DocumentView documentView(ApplicationDocument d) {
        return new DocumentView(d.getId(), d.getDocType(), d.getFileName(), d.getSizeBytes(), d.getUploadedByRole(),
                d.getUploadedAt(), d.isNewForBank());
    }

    private DisbursementView disbursementView(LoanApplication a) {
        Disbursement d = disbursements.findByApplicationId(a.getId()).orElse(null);
        return d == null ? null
                : new DisbursementView(d.getAmount(), d.getAccountMasked(), d.getExecutedAt(), d.isSimulated());
    }

    static String actorLabel(ActorType t) {
        return switch (t) {
            case CLIENT -> "Вы";
            case BANK, SYSTEM -> "Банк";
        };
    }

    static NextStep nextStep(ApplicationStatus s) {
        return switch (s) {
            case DRAFT -> new NextStep("Заполните и отправьте заявку",
                    "Черновик сохраняется автоматически, можно вернуться к нему позже", "CONTINUE_DRAFT");
            case SUBMITTED, SCORING -> new NextStep("Проверяем заявку",
                    "Обычно это занимает меньше минуты, страница обновится сама", "WAIT");
            case NEED_INFO -> new NextStep("Ответьте на запрос банка",
                    "Без этих данных банк не может продолжить рассмотрение. Ответьте ниже и при необходимости приложите документы.",
                    "ANSWER_REQUEST");
            case MANUAL_REVIEW -> new NextStep("Заявку рассматривает специалист",
                    "Обычно это занимает несколько часов. Если понадобятся данные, запрос появится здесь.", "WAIT");
            case OFFER_MADE -> new NextStep("Посмотрите предложение",
                    "Банк готов выдать кредит. Проверьте условия и примите решение.", "REVIEW_OFFER");
            case SIGNING -> new NextStep("Подпишите договор",
                    "Проверьте условия договора и подтвердите подписание. Деньги поступят на расчётный счёт сразу.", "SIGN");
            case ISSUED -> new NextStep("Кредит выдан", "Средства зачислены на расчётный счёт", "NONE");
            case DECLINED -> new NextStep("Банк не может одобрить заявку сейчас",
                    "Вы можете подать новую заявку позже, например с другой суммой или сроком", "NEW_APPLICATION");
            case CLOSED -> new NextStep("Заявка закрыта", "Вы отказались от предложения. Можно подать новую заявку.",
                    "NEW_APPLICATION");
        };
    }
}
