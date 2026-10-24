package ru.hackathon.credit.offer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.application.ActorType;
import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.application.StatusTransitionService;
import ru.hackathon.credit.audit.AuditService;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.company.Company;
import ru.hackathon.credit.company.CompanyService;
import ru.hackathon.credit.integration.DisbursementProvider;

/**
 * Подписание договора кнопкой в кабинете и моделируемая выдача на расчётный счёт.
 * В промышленной версии подтверждение идёт через электронную подпись в интернет-банке.
 */
@Service
public class SigningService {

    private final DisbursementRepository disbursements;
    private final DisbursementProvider disbursementProvider;
    private final OfferRepository offerRepository;
    private final CompanyService companies;
    private final StatusTransitionService transitions;
    private final AuditService audit;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public SigningService(DisbursementRepository disbursements, DisbursementProvider disbursementProvider,
                          OfferRepository offerRepository, CompanyService companies,
                          StatusTransitionService transitions, AuditService audit,
                          io.micrometer.core.instrument.MeterRegistry metrics) {
        this.metrics = metrics;
        this.disbursements = disbursements;
        this.disbursementProvider = disbursementProvider;
        this.offerRepository = offerRepository;
        this.companies = companies;
        this.transitions = transitions;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sign(LoanApplication app, boolean termsAccepted, Long clientUserId) {
        StatusTransitionService.requireStatus(app, ApplicationStatus.SIGNING);
        if (!termsAccepted) {
            throw ApiException.unprocessable("TERMS_NOT_ACCEPTED",
                    "Подтвердите, что ознакомились с условиями договора");
        }
        Offer offer = offerRepository.findById(app.getCurrentOfferId())
                .orElseThrow(() -> ApiException.conflict("NO_OFFER", "Предложение не найдено"));
        Company company = companies.get(app.getCompanyId());
        DisbursementProvider.DisbursementReceipt receipt =
                disbursementProvider.disburse(app.getNumber(), offer.getAmount(), company.getAccountMasked());

        Disbursement d = new Disbursement();
        d.setApplicationId(app.getId());
        d.setAmount(offer.getAmount());
        d.setAccountMasked(company.getAccountMasked());
        d.setSimulated(receipt.simulated());
        disbursements.save(d);
        company.setAccountBalance(company.getAccountBalance().add(offer.getAmount()));
        metrics.counter("credit.loans.issued", "product", app.getProductCode()).increment();
        metrics.summary("credit.loans.issued.amount", "product", app.getProductCode())
                .record(offer.getAmount().doubleValue());
        audit.log(app.getId(), ActorType.CLIENT.name(), clientUserId, "CONTRACT_SIGNED", null);
        audit.log(app.getId(), ActorType.SYSTEM.name(), null, "DISBURSED", "Операция " + receipt.reference());

        transitions.transition(app, ApplicationStatus.ISSUED, ActorType.CLIENT, clientUserId,
                "Договор подписан, средства зачислены на расчётный счёт");
    }
}
