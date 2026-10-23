package ru.hackathon.credit.document;

import java.util.List;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.hackathon.credit.application.ApplicationStatus;
import ru.hackathon.credit.application.LoanApplication;
import ru.hackathon.credit.application.StatusTransitionService;
import ru.hackathon.credit.audit.AuditService;
import ru.hackathon.credit.common.ApiException;

@Service
public class DocumentService {

    public static final Set<String> DOC_TYPES = Set.of("BANK_STATEMENT", "FINANCIAL_REPORT", "COLLATERAL", "OTHER");

    private final ApplicationDocumentRepository documents;
    private final DocumentStorage storage;
    private final AuditService audit;

    public DocumentService(ApplicationDocumentRepository documents, DocumentStorage storage, AuditService audit) {
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
    }

    public record DownloadedFile(String fileName, String contentType, Resource resource) {
    }

    /** Клиент загружает документы в черновик или в ответ на запрос банка. */
    @Transactional
    public ApplicationDocument upload(LoanApplication app, String docType, MultipartFile file, Long userId) {
        StatusTransitionService.requireStatus(app, ApplicationStatus.DRAFT, ApplicationStatus.NEED_INFO);
        String type = docType == null || docType.isBlank() ? "OTHER" : docType;
        if (!DOC_TYPES.contains(type)) {
            throw ApiException.badRequest("BAD_DOC_TYPE", "Неизвестный тип документа");
        }
        DocumentStorage.Stored stored = storage.save(app.getId(), file);
        ApplicationDocument d = new ApplicationDocument();
        d.setApplicationId(app.getId());
        d.setDocType(type);
        d.setFileName(stored.safeName());
        d.setContentType(stored.contentType());
        d.setStoragePath(stored.storagePath());
        d.setSizeBytes(stored.size());
        d.setUploadedByRole("CLIENT");
        d.setNewForBank(app.getStatus() != ApplicationStatus.DRAFT);
        documents.save(d);
        audit.log(app.getId(), "CLIENT", userId, "DOCUMENT_UPLOADED", stored.safeName());
        return d;
    }

    public List<ApplicationDocument> list(Long applicationId) {
        return documents.findByApplicationIdOrderByIdAsc(applicationId);
    }

    public DownloadedFile download(Long applicationId, Long documentId) {
        ApplicationDocument d = documents.findByIdAndApplicationId(documentId, applicationId)
                .orElseThrow(() -> ApiException.notFound("Документ не найден"));
        return new DownloadedFile(d.getFileName(), d.getContentType(), storage.load(d.getStoragePath()));
    }

    /** После решения андеррайтера новые документы считаются просмотренными. */
    @Transactional
    public void markSeenByBank(Long applicationId) {
        documents.findByApplicationIdOrderByIdAsc(applicationId).forEach(d -> d.setNewForBank(false));
    }
}
