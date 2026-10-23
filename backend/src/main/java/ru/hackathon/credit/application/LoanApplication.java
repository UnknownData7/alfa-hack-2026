package ru.hackathon.credit.application;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "loan_application")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "product_code")
    private String productCode;

    private String purpose;

    @Column(name = "requested_amount")
    private BigDecimal requestedAmount;

    @Column(name = "requested_term_months")
    private int requestedTermMonths;

    @Column(name = "monthly_obligations_declared")
    private BigDecimal monthlyObligationsDeclared;

    @Column(name = "collateral_description")
    private String collateralDescription;

    @Column(name = "consent_data_processing")
    private boolean consentDataProcessing;

    @Column(name = "consent_credit_bureau")
    private boolean consentCreditBureau;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    @Column(name = "decision_reason_code")
    private String decisionReasonCode;

    @Column(name = "decision_summary")
    private String decisionSummary;

    @Column(name = "current_offer_id")
    private Long currentOfferId;

    @Column(name = "parent_application_id")
    private Long parentApplicationId;

    @Version
    private int version;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public int getRequestedTermMonths() {
        return requestedTermMonths;
    }

    public void setRequestedTermMonths(int requestedTermMonths) {
        this.requestedTermMonths = requestedTermMonths;
    }

    public BigDecimal getMonthlyObligationsDeclared() {
        return monthlyObligationsDeclared;
    }

    public void setMonthlyObligationsDeclared(BigDecimal monthlyObligationsDeclared) {
        this.monthlyObligationsDeclared = monthlyObligationsDeclared;
    }

    public String getCollateralDescription() {
        return collateralDescription;
    }

    public void setCollateralDescription(String collateralDescription) {
        this.collateralDescription = collateralDescription;
    }

    public boolean isConsentDataProcessing() {
        return consentDataProcessing;
    }

    public void setConsentDataProcessing(boolean consentDataProcessing) {
        this.consentDataProcessing = consentDataProcessing;
    }

    public boolean isConsentCreditBureau() {
        return consentCreditBureau;
    }

    public void setConsentCreditBureau(boolean consentCreditBureau) {
        this.consentCreditBureau = consentCreditBureau;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getDecisionReasonCode() {
        return decisionReasonCode;
    }

    public void setDecisionReasonCode(String decisionReasonCode) {
        this.decisionReasonCode = decisionReasonCode;
    }

    public String getDecisionSummary() {
        return decisionSummary;
    }

    public void setDecisionSummary(String decisionSummary) {
        this.decisionSummary = decisionSummary;
    }

    public Long getCurrentOfferId() {
        return currentOfferId;
    }

    public void setCurrentOfferId(Long currentOfferId) {
        this.currentOfferId = currentOfferId;
    }

    public Long getParentApplicationId() {
        return parentApplicationId;
    }

    public void setParentApplicationId(Long parentApplicationId) {
        this.parentApplicationId = parentApplicationId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
