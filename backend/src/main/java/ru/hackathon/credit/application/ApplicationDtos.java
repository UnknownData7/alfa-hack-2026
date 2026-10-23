package ru.hackathon.credit.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import ru.hackathon.credit.product.Purpose;
import ru.hackathon.credit.scoring.Factor;

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

    public record ConfirmCodeRequest(
            @NotBlank(message = "Введите код") @Pattern(regexp = "\\d{6}", message = "Код состоит из 6 цифр") String code) {
    }

    public record SubmitResponse(Long id, String number, ApplicationStatus status, String statusLabel, String message) {
    }

    public record SendCodeResponse(int attemptsLeft, String demoCode, String hint) {
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

    /** Пояснение решения для клиента: вывод, грейд и вклад факторов. */
    public record DecisionView(String summary, String reasonCode, String grade, Integer score,
                               String hardStopReason, List<Factor> factors) {
    }

    public record SigningView(boolean codeRequested, int attemptsLeft, boolean blocked, boolean confirmed) {
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
            NextStep nextStep, DecisionView decision, OfferView offer, List<HistoryEntry> history,
            List<InfoRequestView> infoRequests, List<DocumentView> documents, SigningView signing,
            DisbursementView disbursement) {
    }
}
