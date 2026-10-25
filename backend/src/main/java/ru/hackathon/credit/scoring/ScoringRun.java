package ru.hackathon.credit.scoring;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.ColumnTransformer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Запуск скоринга. История запусков не удаляется, каждый хранит снимок входных данных и разбор по факторам. */
@Entity
@Table(name = "scoring_result")
public class ScoringRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "run_no")
    private int runNo;

    @Column(name = "total_score")
    private int totalScore;

    private String grade;

    @Column(name = "hard_stop")
    private boolean hardStop;

    @Column(name = "hard_stop_reason")
    private String hardStopReason;

    @Column(name = "payment_load")
    private BigDecimal paymentLoad;

    @ColumnTransformer(write = "?::jsonb")
    private String factors;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "inputs_snapshot")
    private String inputsSnapshot;

    private String trigger;

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

    public int getRunNo() {
        return runNo;
    }

    public void setRunNo(int runNo) {
        this.runNo = runNo;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public boolean isHardStop() {
        return hardStop;
    }

    public void setHardStop(boolean hardStop) {
        this.hardStop = hardStop;
    }

    public String getHardStopReason() {
        return hardStopReason;
    }

    public void setHardStopReason(String hardStopReason) {
        this.hardStopReason = hardStopReason;
    }

    public BigDecimal getPaymentLoad() {
        return paymentLoad;
    }

    public void setPaymentLoad(BigDecimal paymentLoad) {
        this.paymentLoad = paymentLoad;
    }

    public String getFactors() {
        return factors;
    }

    public void setFactors(String factors) {
        this.factors = factors;
    }

    public String getInputsSnapshot() {
        return inputsSnapshot;
    }

    public void setInputsSnapshot(String inputsSnapshot) {
        this.inputsSnapshot = inputsSnapshot;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
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
