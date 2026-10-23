package ru.hackathon.credit.dev;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.document.DocumentStorage;

/** Служебные действия для демонстрации. Доступны только в профиле demo. */
@RestController
@RequestMapping("/api/dev")
@Profile("demo")
@Tag(name = "Демо")
public class DevController {

    private final Flyway flyway;
    private final DocumentStorage storage;

    public DevController(Flyway flyway, DocumentStorage storage) {
        this.flyway = flyway;
        this.storage = storage;
    }

    @PostMapping("/reset-demo")
    @Operation(summary = "Вернуть демо-данные к исходному состоянию")
    public Map<String, String> reset() {
        flyway.clean();
        flyway.migrate();
        storage.clear();
        return Map.of("status", "ok", "message", "Демо-данные восстановлены");
    }
}
