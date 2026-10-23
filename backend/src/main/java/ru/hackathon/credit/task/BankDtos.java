package ru.hackathon.credit.task;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import ru.hackathon.credit.application.ApplicationDtos.DocumentView;
import ru.hackathon.credit.application.ApplicationDtos.HistoryEntry;
import ru.hackathon.credit.application.ApplicationDtos.InfoRequestView;
import ru.hackathon.credit.application.ApplicationDtos.OfferView;
import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.company.ClientProfileController.CompanyProfileDto;
import ru.hackathon.credit.scoring.Factor;

/** Запросы и ответы рабочего места андеррайтера. */
public final class BankDtos {

    private BankDtos() {
    }

    public record TaskListItem(Long id, Long applicationId, String applicationNumber, String companyName,
                               String legalForm, String productCode, String productName, BigDecimal amount,
                               int termMonths, int priority, String status, String statusLabel, Long assigneeId,
                               String assigneeName, boolean mine, Instant slaDueAt, boolean overdue,
                               Instant createdAt, boolean repeat, String reasonCodes, String grade,
                               ApplicationStatus applicationStatus) {
    }

    public record CheckView(String code, String title, String goal, String status, String comment, Instant updatedAt) {
    }

    public record ScoringRunView(int runNo, int totalScore, String grade, boolean hardStop, String hardStopReason,
                                 BigDecimal paymentLoad, String trigger, String createdBy, Instant createdAt,
                                 List<Factor> factors, Map<String, Object> inputs) {
    }

    public record DecisionView(String result, String resultLabel, String comment, String decidedByName,
                               Instant createdAt, Map<String, Object> counterOffer) {
    }

    public record ExternalCheckView(String source, String sourceLabel, String status, Map<String, Object> payload,
                                    Instant receivedAt) {
    }

    public record ApplicationInfo(Long id, String number, ApplicationStatus status, String statusLabel,
                                  String productCode, String productName, boolean requiresCollateral,
                                  BigDecimal productMinAmount, BigDecimal productMaxAmount, int productMinTerm,
                                  int productMaxTerm, String purposeLabel, BigDecimal requestedAmount,
                                  int requestedTermMonths, BigDecimal monthlyObligationsDeclared,
                                  String collateralDescription, Instant submittedAt, String decisionSummary) {
    }

    public record TaskCard(TaskListItem task, ApplicationInfo application, CompanyProfileDto company,
                           List<DocumentView> documents, List<ScoringRunView> scoringRuns, List<CheckView> checks,
                           List<DecisionView> decisions, List<HistoryEntry> history,
                           List<InfoRequestView> infoRequests, List<ExternalCheckView> externalChecks,
                           OfferView currentOffer, boolean canAct, List<String> allowedResults) {
    }

    public record CheckUpdateRequest(
            @NotBlank(message = "Укажите результат проверки") String status,
            @Size(max = 1000, message = "Комментарий не длиннее 1000 символов") String comment) {
    }

    public record RecalculateRequest(
            @Positive(message = "Оборот должен быть больше нуля") BigDecimal confirmedAnnualTurnover,
            @Positive(message = "Сумма должна быть больше нуля") BigDecimal amount,
            @Min(value = 1, message = "Срок от 1 месяца") Integer termMonths) {
    }

    public record IndicativeTerms(String outcome, String outcomeLabel, String summary, BigDecimal amount,
                                  Integer termMonths, BigDecimal ratePct, BigDecimal monthlyPayment) {
    }

    public record RecalculateResponse(ScoringRunView run, IndicativeTerms indicative) {
    }

    public record CounterOffer(
            @NotNull(message = "Укажите сумму") @Positive(message = "Сумма должна быть больше нуля") BigDecimal amount,
            @NotNull(message = "Укажите срок") @Min(value = 1, message = "Срок от 1 месяца") Integer termMonths) {
    }

    public record DecisionRequest(
            @NotBlank(message = "Выберите решение") String result,
            @Size(max = 1000, message = "Комментарий не длиннее 1000 символов") String comment,
            @Valid CounterOffer counterOffer,
            @Size(max = 1000, message = "Запрос не длиннее 1000 символов") String requestText) {
    }
}
