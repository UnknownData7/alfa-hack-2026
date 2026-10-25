package ru.hackathon.credit.task;

import java.time.Instant;

import jakarta.persistence.Version;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Задача андеррайтера в очереди ручной проверки. */
@Entity
@Table(name = "bank_task")
public class BankTask {

    public static final String NEW = "NEW";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String POSTPONED = "POSTPONED";
    public static final String DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    private String queue = "UNDERWRITING";
    private int priority;
    private String status;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "reason_codes")
    private String reasonCodes;

    @Column(name = "is_repeat")
    private boolean repeat;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "taken_at")
    private Instant takenAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    private int version;

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

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public String getReasonCodes() {
        return reasonCodes;
    }

    public void setReasonCodes(String reasonCodes) {
        this.reasonCodes = reasonCodes;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(Instant slaDueAt) {
        this.slaDueAt = slaDueAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public void setTakenAt(Instant takenAt) {
        this.takenAt = takenAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
