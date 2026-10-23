package ru.hackathon.credit.task;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.auth.CurrentUser;
import ru.hackathon.credit.task.BankDtos.CheckUpdateRequest;
import ru.hackathon.credit.task.BankDtos.DecisionRequest;
import ru.hackathon.credit.task.BankDtos.RecalculateRequest;
import ru.hackathon.credit.task.BankDtos.RecalculateResponse;
import ru.hackathon.credit.task.BankDtos.TaskCard;
import ru.hackathon.credit.task.BankDtos.TaskListItem;

@RestController
@RequestMapping("/api/bank")
@Tag(name = "Рабочее место банка")
public class BankController {

    private final UnderwritingService service;

    public BankController(UnderwritingService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    @Operation(summary = "Очередь задач. Фильтр: active (по умолчанию), mine, new, postponed, done")
    public List<TaskListItem> tasks(@RequestParam(value = "filter", required = false) String filter) {
        return service.list(filter, CurrentUser.get().id());
    }

    @PostMapping("/tasks/take-next")
    @Operation(summary = "Взять следующую задачу по правилам очереди")
    public TaskCard takeNext() {
        return service.takeNext(CurrentUser.get().id());
    }

    @PostMapping("/tasks/{id}/take")
    @Operation(summary = "Взять конкретную задачу или вернуться к отложенной")
    public TaskCard take(@PathVariable Long id) {
        return service.take(id, CurrentUser.get().id());
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Карточка задачи: заявка, компания, документы, скоринг, проверки, история")
    public TaskCard card(@PathVariable Long id) {
        return service.card(id, CurrentUser.get().id());
    }

    @PutMapping("/tasks/{id}/checks/{code}")
    @Operation(summary = "Результат проверки: OK, NEGATIVE, NEEDS_INFO")
    public TaskCard updateCheck(@PathVariable Long id, @PathVariable String code,
                                @Valid @RequestBody CheckUpdateRequest request) {
        return service.updateCheck(id, code, request.status(), request.comment(), CurrentUser.get().id());
    }

    @PostMapping("/tasks/{id}/recalculate")
    @Operation(summary = "Пересчёт скоринга с подтверждённым оборотом, суммой или сроком")
    public RecalculateResponse recalculate(@PathVariable Long id, @Valid @RequestBody RecalculateRequest request) {
        return service.recalculate(id, request, CurrentUser.get().id());
    }

    @PostMapping("/tasks/{id}/decision")
    @Operation(summary = "Решение: APPROVE, APPROVE_WITH_CHANGES, REQUEST_INFO, DECLINE, POSTPONE")
    public TaskCard decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        return service.decide(id, request, CurrentUser.get().id());
    }

    @GetMapping("/applications/{applicationId}/documents/{docId}")
    @Operation(summary = "Скачать документ заявки")
    public ResponseEntity<Resource> download(@PathVariable Long applicationId, @PathVariable Long docId) {
        return ru.hackathon.credit.application.ApplicationControllerSupport.file(service.download(applicationId, docId));
    }
}
