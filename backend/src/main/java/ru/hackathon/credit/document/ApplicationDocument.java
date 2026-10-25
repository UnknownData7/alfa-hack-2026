package ru.hackathon.credit.document;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_document")
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "doc_type")
    private String docType;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "size_bytes")
    private long sizeBytes;

    @Column(name = "uploaded_by_role")
    private String uploadedByRole;

    @Column(name = "uploaded_at")
    private Instant uploadedAt = Instant.now();

    /** Документ загружен после отправки заявки и ещё не просмотрен банком. */
    @Column(name = "is_new_for_bank")
    private boolean newForBank;

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

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getUploadedByRole() {
        return uploadedByRole;
    }

    public void setUploadedByRole(String uploadedByRole) {
        this.uploadedByRole = uploadedByRole;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public boolean isNewForBank() {
        return newForBank;
    }

    public void setNewForBank(boolean newForBank) {
        this.newForBank = newForBank;
    }
}
