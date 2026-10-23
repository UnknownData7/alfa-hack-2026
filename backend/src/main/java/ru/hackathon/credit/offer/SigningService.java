package ru.hackathon.credit.offer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.hackathon.credit.application.ActorType;
import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.application.StatusTransitionService;
import ru.hackathon.credit.audit.AuditService;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.company.CompanyService;
import ru.hackathon.credit.integration.DisbursementProvider;
import ru.hackathon.credit.integration.SigningProvider;

/**
 * Подписание договора одноразовым кодом и моделируемая выдача. В БД хранится только хеш кода, попыток три.
 */
@Service
public class SigningService {

    public static final int MAX_ATTEMPTS = 3;

    private final SigningSessionRepository sessions;
    private final DisbursementRepository disbursements;
    private final SigningProvider signingProvider;
    private final DisbursementProvider disbursementProvider;
    private final OfferService offers;
    private final OfferRepository offerRepository;
    private final CompanyService companies;
    private final StatusTransitionService transitions;
    private final AuditService audit;
    private final boolean showCode;

    public SigningService(SigningSessionRepository sessions, DisbursementRepository disbursements,
                          SigningProvider signingProvider, DisbursementProvider disbursementProvider,
                          OfferService offers, OfferRepository offerRepository, CompanyService companies,
                          StatusTransitionService transitions, AuditService audit,
                          @Value("${app.demo.show-signing-code}") boolean showCode) {
        this.sessions = sessions;
        this.disbursements = disbursements;
        this.signingProvider = signingProvider;
        this.disbursementProvider = disbursementProvider;
        this.offers = offers;
        this.offerRepository = offerRepository;
        this.companies = companies;
        this.transitions = transitions;
        this.audit = audit;
        this.showCode = showCode;
    }

    public record CodeIssued(int attemptsLeft, String demoCode) {
    }

    public record ConfirmResult(boolean confirmed, int attemptsLeft, boolean blocked) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CodeIssued sendCode(LoanApplication app, Long clientUserId) {
        StatusTransitionService.requireStatus(app, ApplicationStatus.SIGNING);
        for (SigningSession s : sessions.findByApplicationIdAndStatus(app.getId(), SigningSession.ACTIVE)) {
            s.setStatus(SigningSession.REPLACED);
        }
        String code = signingProvider.issueCode();
        SigningSession session = new SigningSession();
        session.setApplicationId(app.getId());
        session.setCodeHash(hash(app.getId(), code));
        session.setAttemptsLeft(MAX_ATTEMPTS);
        session.setStatus(SigningSession.ACTIVE);
        sessions.save(session);
        audit.log(app.getId(), ActorType.CLIENT.name(), clientUserId, "SIGNING_CODE_ISSUED", null);
        // В промышленной версии код уходит через ДБО/Госключ; в демо он показывается в интерфейсе
        return new CodeIssued(MAX_ATTEMPTS, showCode ? code : null);
    }

    /**
     * Неверный код уменьшает число попыток; после трёх ошибок сессия блокируется и нужен новый код.
     * Неудачная попытка фиксируется даже при ответе с ошибкой, поэтому метод не бросает исключение на неверный код.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConfirmResult confirm(LoanApplication app, String code, Long clientUserId) {
        StatusTransitionService.requireStatus(app, ApplicationStatus.SIGNING);
        SigningSession session = sessions.findByApplicationIdAndStatus(app.getId(), SigningSession.ACTIVE).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("NO_ACTIVE_CODE", "Сначала запросите код подтверждения"));

        if (!session.getCodeHash().equals(hash(app.getId(), code))) {
            session.setAttemptsLeft(session.getAttemptsLeft() - 1);
            if (session.getAttemptsLeft() <= 0) {
                session.setStatus(SigningSession.BLOCKED);
                audit.log(app.getId(), ActorType.CLIENT.name(), clientUserId, "SIGNING_BLOCKED", null);
                return new ConfirmResult(false, 0, true);
            }
            return new ConfirmResult(false, session.getAttemptsLeft(), false);
        }

        session.setStatus(SigningSession.CONFIRMED);
        Offer offer = offerRepository.findById(app.getCurrentOfferId())
                .orElseThrow(() -> ApiException.conflict("NO_OFFER", "Предложение не найдено"));
        String account = companies.get(app.getCompanyId()).getAccountMasked();
        DisbursementProvider.DisbursementReceipt receipt =
                disbursementProvider.disburse(app.getNumber(), offer.getAmount(), account);

        Disbursement d = new Disbursement();
        d.setApplicationId(app.getId());
        d.setAmount(offer.getAmount());
        d.setAccountMasked(account);
        d.setSimulated(receipt.simulated());
        disbursements.save(d);
        audit.log(app.getId(), ActorType.SYSTEM.name(), null, "DISBURSED", "Операция " + receipt.reference());

        transitions.transition(app, ApplicationStatus.ISSUED, ActorType.CLIENT, clientUserId,
                "Договор подписан, средства зачислены (симуляция)");
        return new ConfirmResult(true, session.getAttemptsLeft(), false);
    }

    public SigningSession latestSession(Long applicationId) {
        return sessions.findTopByApplicationIdOrderByIdDesc(applicationId).orElse(null);
    }

    static String hash(Long applicationId, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((applicationId + ":" + (code == null ? "" : code.trim()))
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
