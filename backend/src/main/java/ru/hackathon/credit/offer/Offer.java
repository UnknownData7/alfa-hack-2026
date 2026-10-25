package ru.hackathon.credit.offer;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.ColumnTransformer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "offer")
public class Offer {

    public static final String INITIAL = "INITIAL";
    public static final String COUNTER = "COUNTER";
    public static final String ACTIVE = "ACTIVE";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    private String kind;
    private BigDecimal amount;

    @Column(name = "term_months")
    private int termMonths;

    @Column(name = "rate_pct")
    private BigDecimal ratePct;

    @Column(name = "monthly_payment")
    private BigDecimal monthlyPayment;

    @Column(name = "total_payment")
    private BigDecimal totalPayment;

    private BigDecimal overpayment;
    private String status;

    @ColumnTransformer(write = "?::jsonb")
    private String explanation;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(int termMonths) {
        this.termMonths = termMonths;
    }

    public BigDecimal getRatePct() {
        return ratePct;
    }

    public void setRatePct(BigDecimal ratePct) {
        this.ratePct = ratePct;
    }

    public BigDecimal getMonthlyPayment() {
        return monthlyPayment;
    }

    public void setMonthlyPayment(BigDecimal monthlyPayment) {
        this.monthlyPayment = monthlyPayment;
    }

    public BigDecimal getTotalPayment() {
        return totalPayment;
    }

    public void setTotalPayment(BigDecimal totalPayment) {
        this.totalPayment = totalPayment;
    }

    public BigDecimal getOverpayment() {
        return overpayment;
    }

    public void setOverpayment(BigDecimal overpayment) {
        this.overpayment = overpayment;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
