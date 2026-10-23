package ru.hackathon.credit.feed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Запись каталога новостей и подсказок. Пустое поле таргетинга означает «подходит всем». */
@Entity
@Table(name = "news_catalog")
public class NewsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String title;
    private String body;

    @Column(name = "target_legal_form")
    private String targetLegalForm;

    @Column(name = "target_industry_risk")
    private String targetIndustryRisk;

    @Column(name = "target_product_code")
    private String targetProductCode;

    @Column(name = "target_status")
    private String targetStatus;

    private int weight;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTargetLegalForm() {
        return targetLegalForm;
    }

    public void setTargetLegalForm(String targetLegalForm) {
        this.targetLegalForm = targetLegalForm;
    }

    public String getTargetIndustryRisk() {
        return targetIndustryRisk;
    }

    public void setTargetIndustryRisk(String targetIndustryRisk) {
        this.targetIndustryRisk = targetIndustryRisk;
    }

    public String getTargetProductCode() {
        return targetProductCode;
    }

    public void setTargetProductCode(String targetProductCode) {
        this.targetProductCode = targetProductCode;
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
