package ru.hackathon.credit.assistant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.assistant.AssistantDtos.ActionRequest;
import ru.hackathon.credit.assistant.AssistantDtos.ConfigDto;
import ru.hackathon.credit.assistant.AssistantDtos.EventRequest;
import ru.hackathon.credit.assistant.AssistantDtos.MessageRequest;
import ru.hackathon.credit.assistant.AssistantDtos.Reply;
import ru.hackathon.credit.audit.AuditService;
import ru.hackathon.credit.auth.CurrentUser;
import ru.hackathon.credit.common.ApiException;
import ru.hackathon.credit.common.Json;

/**
 * Помощник клиента. Разговор не хранится на сервере и не пишется в журнал: в аудит попадают только события виджета
 * и действия, которыми закончился диалог, без текста сообщений.
 */
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "Помощник")
public class AssistantController {

    /** Действия, которые считаются результатом диалога для продуктовых метрик. */
    private static final Set<String> AUDITED_ACTIONS = Set.of("FILL_FORM", "SELECT_PRODUCT");

    private final AssistantProperties properties;
    private final DialogOrchestrator orchestrator;
    private final AssistantRateLimiter limiter;
    private final AuditService audit;
    private final Json json;

    public AssistantController(AssistantProperties properties, DialogOrchestrator orchestrator,
                               AssistantRateLimiter limiter, AuditService audit, Json json) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.limiter = limiter;
        this.audit = audit;
        this.json = json;
    }

    @GetMapping("/config")
    @Operation(summary = "Включён ли помощник и пороги автоматических подсказок")
    public ConfigDto config() {
        AssistantProperties p = properties;
        return new ConfigDto(p.enabled(), p.enabled() && p.proactive(), p.idleSeconds(), p.purposeChanges(),
                p.valueChanges(), p.recommendationsSeconds(), p.wizardIdleSeconds(), p.validationRepeats(),
                p.maxOffersPerSession());
    }

    @PostMapping("/messages")
    @Operation(summary = "Сообщение помощнику в свободной форме")
    public Reply message(@Valid @RequestBody MessageRequest request) {
        guard(true);
        return orchestrator.message(request);
    }

    @PostMapping("/actions")
    @Operation(summary = "Нажатие кнопки в ответе помощника")
    public Reply action(@Valid @RequestBody ActionRequest request) {
        CurrentUser user = guard(true);
        Reply reply = orchestrator.action(request);
        if (AUDITED_ACTIONS.contains(request.action())) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("action", request.action());
            details.put("page", request.page() == null ? null : request.page().page());
            audit.log(null, "CLIENT", user.id(), "ASSISTANT_ACTION", json.write(details));
        }
        return reply;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Событие виджета для продуктовых метрик (без текста сообщений)")
    public void event(@Valid @RequestBody EventRequest request) {
        CurrentUser user = guard(false);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("page", request.page());
        details.put("signal", request.signal());
        details.put("entry", request.entry());
        audit.log(null, "CLIENT", user.id(), "ASSISTANT_" + request.type().name(), json.write(details));
    }

    private CurrentUser guard(boolean limited) {
        if (!properties.enabled()) {
            throw ApiException.notFound("Помощник выключен");
        }
        CurrentUser user = CurrentUser.get();
        user.requireCompanyId();
        if (limited) {
            limiter.check(user.id());
        }
        return user;
    }
}
