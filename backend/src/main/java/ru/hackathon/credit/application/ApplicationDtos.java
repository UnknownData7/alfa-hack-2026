package ru.hackathon.credit.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import ru.hackathon.credit.product.Purpose;

/** Запросы и ответы API заявки для клиента. */
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    /** Создание и автосохранение черновика. */
    public record DraftRequest(
            @NotBlank(message = "Выберите продукт") String productCode,
            @NotNull(message = "Укажите цель финансирования") Purpose purpose,
            @NotNull(message = "Укажите сумму") @Positive(message = "Сумма должна быть больше нуля") BigDecimal amount,
            @NotNull(message = "Укажите срок") @Min(value = 1, message = "Срок от 1 месяца") Integer termMonths,
            @PositiveOrZero(message = "Платежи не могут быть отрицательными") BigDecimal monthlyObligationsDeclared,
            @Size(max = 1000, message = "Описание залога не длиннее 1000 символов") String collateralDescription,
            Boolean consentDataProcessing,
            Boolean consentCreditBureau,
            Long parentApplicationId) {
    }

    public record AnswerRequest(
            @NotBlank(message = "Напишите ответ") @Size(max = 2000, message = "Ответ не длиннее 2000 символов") String answer,
            @Size(max = 1000, message = "Описание залога не длиннее 1000 символов") String collateralDescription) {
    }

    /** Подписание договора: клиент подтверждает, что ознакомился с условиями. */
    public record SignRequest(Boolean termsAccepted) {
    }

    public record SubmitResponse(Long id, String number, ApplicationStatus status, String statusLabel, String message) {
    }

    public record NextStep(String title, String text, String action) {
    }

    public record ApplicationSummary(Long id, String number, ApplicationStatus status, String statusLabel,
                                     String productCode, String productName, BigDecimal requestedAmount,
                                     int requestedTermMonths, Instant createdAt, Instant updatedAt,
                                     NextStep nextStep) {
    }

    public record HistoryEntry(ApplicationStatus fromStatus, ApplicationStatus toStatus, String toStatusLabel,
                               ActorType actorType, String actorLabel, String comment, Instant createdAt) {
    }

    public record InfoRequestView(Long id, String source, String text, String status, String clientAnswer,
                                  Instant createdAt, Instant answeredAt) {
    }

    public record DocumentView(Long id, String docType, String fileName, long sizeBytes, String uploadedByRole,
                               Instant uploadedAt, boolean newForBank) {
    }

    public record OfferView(Long id, String kind, BigDecimal amount, int termMonths, BigDecimal ratePct,
                            BigDecimal monthlyPayment, BigDecimal totalPayment, BigDecimal overpayment, String status,
                            String summary, List<String> reasons, List<String> changes, Instant createdAt) {
    }

    /** Предложение для клиента: условия и что изменилось относительно заявки, без внутренних причин решения. */
    public record ClientOfferView(Long id, String kind, BigDecimal amount, int termMonths, BigDecimal ratePct,
                                  BigDecimal monthlyPayment, BigDecimal totalPayment, BigDecimal overpayment,
                                  String status, String summary, List<String> changes, Instant createdAt) {
    }

    public record DisbursementView(BigDecimal amount, String accountMasked, Instant executedAt, boolean simulated) {
    }

    public record ApplicationDetail(
            Long id, String number, ApplicationStatus status, String statusLabel, boolean finalStatus,
            String productCode, String productName, boolean requiresCollateral,
            String purpose, String purposeLabel, BigDecimal requestedAmount, int requestedTermMonths,
            BigDecimal monthlyObligationsDeclared, String collateralDescription,
            boolean consentDataProcessing, boolean consentCreditBureau, Long parentApplicationId,
            Instant createdAt, Instant updatedAt, Instant submittedAt,
            NextStep nextStep, ClientOfferView offer, List<HistoryEntry> history,
            List<InfoRequestView> infoRequests, List<DocumentView> documents, DisbursementView disbursement) {
    }
}
