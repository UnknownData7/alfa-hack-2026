package ru.hackathon.credit.application;

import java.time.Instant;
import java.time.Year;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.hackathon.credit.application.ApplicationDtos.AnswerRequest;
import ru.hackathon.credit.application.ApplicationDtos.ApplicationDetail;
import ru.hackathon.credit.application.ApplicationDtos.ApplicationSummary;
import ru.hackathon.credit.application.ApplicationDtos.DocumentView;
import ru.hackathon.credit.application.ApplicationDtos.DraftRequest;
import ru.hackathon.credit.auth.CurrentUser;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.document.DocumentService;
import ru.hackathon.credit.offer.OfferService;
import ru.hackathon.credit.offer.SigningService;
import ru.hackathon.credit.product.Product;
import ru.hackathon.credit.product.ProductService;

/**
 * Клиентская часть процесса: черновик, отправка, ответ на запрос банка, решение по предложению, подписание.
 * Клиент видит и меняет только заявки своей компании.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final InfoRequestRepository infoRequests;
    private final ProductService products;
    private final StatusTransitionService transitions;
    private final DocumentService documents;
    private final OfferService offers;
    private final SigningService signing;
    private final ApplicationViews views;

    public ApplicationService(ApplicationRepository applications, InfoRequestRepository infoRequests,
                              ProductService products, StatusTransitionService transitions, DocumentService documents,
                              OfferService offers, SigningService signing, ApplicationViews views) {
        this.applications = applications;
        this.infoRequests = infoRequests;
        this.products = products;
        this.transitions = transitions;
        this.documents = documents;
        this.offers = offers;
        this.signing = signing;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummary> list(CurrentUser user) {
        return views.summaries(applications.findByCompanyIdOrderByCreatedAtDesc(user.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public ApplicationDetail detail(CurrentUser user, Long id) {
        return views.detail(own(user, id));
    }

    @Transactional
    public ApplicationDetail create(CurrentUser user, DraftRequest r) {
        Long companyId = user.requireCompanyId();
        Product product = products.get(r.productCode());
        products.validateTerms(product, r.amount(), r.termMonths());
        if (r.parentApplicationId() != null) {
            LoanApplication parent = own(user, r.parentApplicationId());
            if (!parent.getStatus().isFinal()) {
                throw ApiException.conflict("PARENT_NOT_FINAL",
                        "Новую заявку на основе прежней можно создать только после её завершения");
            }
        }
        LoanApplication a = new LoanApplication();
        a.setCompanyId(companyId);
        a.setNumber(String.format("A-%d-%06d", Year.now().getValue(), applications.nextNumber()));
        a.setParentApplicationId(r.parentApplicationId());
        apply(a, r);
        a.setStatus(ApplicationStatus.DRAFT);
        a = applications.save(a);
        transitions.recordCreation(a, ActorType.CLIENT, user.id());
        return views.detail(a);
    }

    @Transactional
    public ApplicationDetail update(CurrentUser user, Long id, DraftRequest r) {
        LoanApplication a = own(user, id);
        StatusTransitionService.requireStatus(a, ApplicationStatus.DRAFT);
        Product product = products.get(r.productCode());
        products.validateTerms(product, r.amount(), r.termMonths());
        apply(a, r);
        return views.detail(applications.saveAndFlush(a));
    }

    /** Отправка проверяет согласия и границы продукта. Полнота данных проверяется уже конвейером. */
    @Transactional
    public LoanApplication submit(CurrentUser user, Long id) {
        LoanApplication a = own(user, id);
        StatusTransitionService.requireStatus(a, ApplicationStatus.DRAFT);
        if (!a.isConsentDataProcessing() || !a.isConsentCreditBureau()) {
            throw ApiException.unprocessable("CONSENTS_REQUIRED",
                    "Чтобы отправить заявку, нужно дать согласие на обработку данных и на запрос в бюро кредитных историй");
        }
        products.validateTerms(products.get(a.getProductCode()), a.getRequestedAmount(), a.getRequestedTermMonths());
        a.setSubmittedAt(Instant.now());
        return transitions.transition(a, ApplicationStatus.SUBMITTED, ActorType.CLIENT, user.id(), "Заявка отправлена");
    }

    /**
     * Ответ на запрос данных. Когда закрыты все открытые запросы, заявка возвращается в обработку.
     *
     * @return true, если заявку нужно отправить в конвейер
     */
    @Transactional
    public boolean answer(CurrentUser user, Long id, Long requestId, AnswerRequest r) {
        LoanApplication a = own(user, id);
        StatusTransitionService.requireStatus(a, ApplicationStatus.NEED_INFO);
        InfoRequest req = infoRequests.findByIdAndApplicationId(requestId, a.getId())
                .orElseThrow(() -> ApiException.notFound("Запрос не найден"));
        if (!InfoRequest.OPEN.equals(req.getStatus())) {
            throw ApiException.conflict("ALREADY_ANSWERED", "На этот запрос уже дан ответ");
        }
        req.setClientAnswer(r.answer().trim());
        req.setStatus(InfoRequest.ANSWERED);
        req.setAnsweredAt(Instant.now());
        if (r.collateralDescription() != null && !r.collateralDescription().isBlank()) {
            a.setCollateralDescription(r.collateralDescription().trim());
        }
        infoRequests.flush();
        boolean allAnswered = infoRequests.findByApplicationIdAndStatus(a.getId(), InfoRequest.OPEN).isEmpty();
        if (allAnswered) {
            transitions.transition(a, ApplicationStatus.SUBMITTED, ActorType.CLIENT, user.id(),
                    "Клиент ответил на запрос и дополнил данные");
        }
        return allAnswered;
    }

    @Transactional
    public DocumentView upload(CurrentUser user, Long id, String docType, MultipartFile file) {
        LoanApplication a = own(user, id);
        return ApplicationViews.documentView(documents.upload(a, docType, file, user.id()));
    }

    @Transactional(readOnly = true)
    public DocumentService.DownloadedFile download(CurrentUser user, Long id, Long documentId) {
        LoanApplication a = own(user, id);
        return documents.download(a.getId(), documentId);
    }

    @Transactional
    public ApplicationDetail acceptOffer(CurrentUser user, Long id) {
        LoanApplication a = own(user, id);
        offers.accept(a, user.id());
        return views.detail(a);
    }

    @Transactional
    public ApplicationDetail declineOffer(CurrentUser user, Long id) {
        LoanApplication a = own(user, id);
        offers.decline(a, user.id());
        return views.detail(a);
    }

    @Transactional
    public SigningService.CodeIssued sendCode(CurrentUser user, Long id) {
        return signing.sendCode(own(user, id), user.id());
    }

    @Transactional
    public SigningService.ConfirmResult confirmCode(CurrentUser user, Long id, String code) {
        return signing.confirm(own(user, id), code, user.id());
    }

    /** Заявка текущего клиента; чужая заявка неотличима от несуществующей. */
    LoanApplication own(CurrentUser user, Long id) {
        return applications.findByIdAndCompanyId(id, user.requireCompanyId())
                .orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
    }

    private static void apply(LoanApplication a, DraftRequest r) {
        a.setProductCode(r.productCode());
        a.setPurpose(r.purpose().name());
        a.setRequestedAmount(r.amount());
        a.setRequestedTermMonths(r.termMonths());
        a.setMonthlyObligationsDeclared(r.monthlyObligationsDeclared());
        a.setCollateralDescription(r.collateralDescription() == null ? null : r.collateralDescription().trim());
        a.setConsentDataProcessing(Boolean.TRUE.equals(r.consentDataProcessing()));
        a.setConsentCreditBureau(Boolean.TRUE.equals(r.consentCreditBureau()));
    }
}
