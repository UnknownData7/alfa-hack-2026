package ru.hackathon.credit.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.auth.CurrentUser;
import ru.hackathon.credit.offer.DisbursementRepository;

/** История операций клиента: зачисления кредитов и события по заявкам в одной ленте. */
@RestController
@RequestMapping("/api/client")
@Tag(name = "Клиент")
public class ClientActivityController {

    private final ApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final DisbursementRepository disbursements;

    public ClientActivityController(ApplicationRepository applications, StatusHistoryRepository history,
                                    DisbursementRepository disbursements) {
        this.applications = applications;
        this.history = history;
        this.disbursements = disbursements;
    }

    public record Activity(String type, String title, String description, BigDecimal amount, Long applicationId,
                           String applicationNumber, Instant createdAt) {
    }

    @GetMapping("/activity")
    @Transactional(readOnly = true)
    @Operation(summary = "История операций: зачисления и события по заявкам")
    public List<Activity> activity() {
        Long companyId = CurrentUser.get().requireCompanyId();
        List<Activity> result = new ArrayList<>();
        for (LoanApplication a : applications.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            disbursements.findByApplicationId(a.getId()).ifPresent(d -> result.add(new Activity("CREDIT",
                    "Зачисление кредита", "Кредит по заявке " + a.getNumber() + " на счёт " + d.getAccountMasked(),
                    d.getAmount(), a.getId(), a.getNumber(), d.getExecutedAt())));
            for (ApplicationDtos.HistoryEntry h : ApplicationViews.clientHistory(
                    history.findByApplicationIdOrderByIdAsc(a.getId()))) {
                result.add(new Activity("STATUS", h.toStatusLabel(), h.comment(), null, a.getId(), a.getNumber(),
                        h.createdAt()));
            }
        }
        result.sort(Comparator.comparing(Activity::createdAt).reversed());
        return result;
    }
}
