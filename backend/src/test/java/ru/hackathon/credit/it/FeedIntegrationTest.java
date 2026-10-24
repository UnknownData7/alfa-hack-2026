package ru.hackathon.credit.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

/** Лента «Для вас»: у карточек есть дата, «Прочитано» убирает карточку полностью. */
class FeedIntegrationTest extends IntegrationTestBase {

    @Test
    void cardsHaveDatesAndDismissRemovesThem() throws Exception {
        String token = login("romashka");
        long id = draft(token, "WORKING_EXPRESS", "WORKING_CAPITAL", 1_200_000, 9, null);
        postJson(token, "/api/applications/" + id + "/submit", null, 202);
        awaitStatus(token, id, "OFFER_MADE");

        JsonNode feed = getJson(token, "/api/feed", 200);
        assertFalse(feed.isEmpty());
        String eventId = null;
        String newsId = null;
        for (JsonNode card : feed) {
            assertFalse(card.get("createdAt").isNull(), "У карточки нет даты: " + card);
            String cardId = card.get("id").asText();
            if (cardId.startsWith("event-") && eventId == null) {
                eventId = cardId;
            }
            if (cardId.startsWith("news-") && newsId == null) {
                newsId = cardId;
            }
        }
        assertTrue(eventId != null && newsId != null, "Ожидались событие и новость: " + feed);

        postJson(token, "/api/feed/" + eventId + "/read", null, 204);
        postJson(token, "/api/feed/" + newsId + "/read", null, 204);

        String after = getJson(token, "/api/feed", 200).toString();
        assertFalse(after.contains("\"" + eventId + "\""));
        assertFalse(after.contains("\"" + newsId + "\""));
    }

    @Test
    void foreignOrUnknownCardCannotBeDismissed() throws Exception {
        String owner = login("romashka");
        long id = draft(owner, "WORKING_EXPRESS", "WORKING_CAPITAL", 1_200_000, 9, null);
        postJson(owner, "/api/applications/" + id + "/submit", null, 202);
        awaitStatus(owner, id, "OFFER_MADE");
        String eventId = null;
        for (JsonNode card : getJson(owner, "/api/feed", 200)) {
            if (card.get("id").asText().startsWith("event-")) {
                eventId = card.get("id").asText();
            }
        }
        String other = login("sokolov");
        postJson(other, "/api/feed/" + eventId + "/read", null, 404);
        postJson(other, "/api/feed/garbage/read", null, 404);
        assertTrue(getJson(owner, "/api/feed", 200).toString().contains(eventId));
    }
}
