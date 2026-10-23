package ru.hackathon.credit.feed;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import ru.hackathon.credit.auth.CurrentUser;

@RestController
@RequestMapping("/api/feed")
@Tag(name = "Лента «Для вас»")
public class FeedController {

    private final FeedService feed;

    public FeedController(FeedService feed) {
        this.feed = feed;
    }

    @GetMapping
    @Operation(summary = "Лента: события по заявкам, новости и подсказки с объяснением, почему показаны")
    public List<FeedService.FeedCard> list() {
        return feed.feed(CurrentUser.get().requireCompanyId());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Отметить событие прочитанным")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        feed.markRead(CurrentUser.get().requireCompanyId(), id);
        return ResponseEntity.noContent().build();
    }
}
