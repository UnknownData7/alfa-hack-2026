package ru.hackathon.credit.application;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.application.ApplicationDtos.AnswerRequest;
import ru.hackathon.credit.application.ApplicationDtos.ApplicationDetail;
import ru.hackathon.credit.application.ApplicationDtos.ApplicationSummary;
import ru.hackathon.credit.application.ApplicationDtos.DocumentView;
import ru.hackathon.credit.application.ApplicationDtos.DraftRequest;
import ru.hackathon.credit.application.ApplicationDtos.SignRequest;
import ru.hackathon.credit.application.ApplicationDtos.SubmitResponse;
import ru.hackathon.credit.auth.CurrentUser;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Заявки клиента")
public class ApplicationController {

    private final ApplicationService service;
    private final ApplicationPipeline pipeline;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public ApplicationController(ApplicationService service, ApplicationPipeline pipeline,
                                 io.micrometer.core.instrument.MeterRegistry metrics) {
        this.service = service;
        this.pipeline = pipeline;
        this.metrics = metrics;
    }

    @GetMapping
    @Operation(summary = "Мои заявки")
    public List<ApplicationSummary> list() {
        return service.list(CurrentUser.get());
    }

    @PostMapping
    @Operation(summary = "Создать черновик заявки")
    public ResponseEntity<ApplicationDetail> create(@Valid @RequestBody DraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CurrentUser.get(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Заявка: статус, история, пояснение решения, предложение")
    public ApplicationDetail get(@PathVariable Long id) {
        return service.detail(CurrentUser.get(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить черновик (автосохранение)")
    public ApplicationDetail update(@PathVariable Long id, @Valid @RequestBody DraftRequest request) {
        return service.update(CurrentUser.get(), id, request);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Отправить заявку. Обработка идёт асинхронно, ответ 202")
    public ResponseEntity<SubmitResponse> submit(@PathVariable Long id) {
        LoanApplication app = service.submit(CurrentUser.get(), id);
        metrics.counter("credit.applications.submitted", "product", app.getProductCode()).increment();
        pipeline.process(app.getId());
        return ResponseEntity.accepted().body(new SubmitResponse(app.getId(), app.getNumber(), app.getStatus(),
                app.getStatus().clientLabel(), "Заявка отправлена. Статус обновится автоматически."));
    }

    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить документ (до 10 МБ: PDF, JPG, PNG, DOCX, XLSX, CSV, TXT)")
    public ResponseEntity<DocumentView> upload(@PathVariable Long id,
                                               @RequestParam(value = "docType", required = false) String docType,
                                               @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(CurrentUser.get(), id, docType, file));
    }

    @GetMapping("/{id}/documents/{docId}")
    @Operation(summary = "Скачать документ")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long docId) {
        return ApplicationControllerSupport.file(service.download(CurrentUser.get(), id, docId));
    }

    @PostMapping("/{id}/info-requests/{requestId}/answer")
    @Operation(summary = "Ответить на запрос банка")
    public ApplicationDetail answer(@PathVariable Long id, @PathVariable Long requestId,
                                    @Valid @RequestBody AnswerRequest request) {
        CurrentUser user = CurrentUser.get();
        if (service.answer(user, id, requestId, request)) {
            pipeline.process(id);
        }
        return service.detail(user, id);
    }

    @PostMapping("/{id}/offer/accept")
    @Operation(summary = "Принять предложение")
    public ApplicationDetail accept(@PathVariable Long id) {
        return service.acceptOffer(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/offer/decline")
    @Operation(summary = "Отклонить предложение")
    public ApplicationDetail decline(@PathVariable Long id) {
        return service.declineOffer(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/sign")
    @Operation(summary = "Подписать договор: выполняется моделируемая выдача на расчётный счёт")
    public ApplicationDetail sign(@PathVariable Long id, @RequestBody(required = false) SignRequest request) {
        boolean accepted = request != null && Boolean.TRUE.equals(request.termsAccepted());
        return service.sign(CurrentUser.get(), id, accepted);
    }
}
