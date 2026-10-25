package ru.hackathon.credit.application;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Запрос дополнительных данных у клиента: от системы (проверка полноты) или от андеррайтера. */
@Entity
@Table(name = "info_request")
public class InfoRequest {

    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_BANK = "BANK";
    public static final String OPEN = "OPEN";
    public static final String ANSWERED = "ANSWERED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "task_id")
    private Long taskId;

    private String source;
    private String text;
    private String status;

    @Column(name = "client_answer")
    private String clientAnswer;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "answered_at")
    private Instant answeredAt;

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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClientAnswer() {
        return clientAnswer;
    }

    public void setClientAnswer(String clientAnswer) {
        this.clientAnswer = clientAnswer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(Instant answeredAt) {
        this.answeredAt = answeredAt;
    }
}
