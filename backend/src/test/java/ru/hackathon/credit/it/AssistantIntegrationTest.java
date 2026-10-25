package ru.hackathon.credit.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Помощник через HTTP: доступ только клиенту, путь до подстановки в форму, события без текста сообщений. */
class AssistantIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void clientGoesFromQuestionToFilledForm() throws Exception {
        String token = login("kim");
        JsonNode config = getJson(token, "/api/assistant/config", 200);
        assertTrue(config.get("enabled").asBoolean());
        assertEquals(20, config.get("idleSeconds").asInt());

        Map<String, Object> page = Map.of("page", "NEED");
        JsonNode r = postJson(token, "/api/assistant/messages", Map.of("page", page,
                "text", "нужно 400 тысяч, чтобы закрыть аренду, пока не пришла оплата от заказчика"), 200);
        assertEquals("TERM", r.get("state").asText());
        assertEquals("CASH_GAP", r.get("slots").get("purpose").asText());
        assertTrue(r.get("text").asText().contains("«Оборотный экспресс», ставка от 18% годовых"), r.toString());
        assertEquals(3, r.get("terms").size());

        JsonNode fill = null;
        for (JsonNode b : r.get("buttons")) {
            if (b.get("action").asText().equals("FILL_FORM") && b.get("params").get("term").asText().equals("6")) {
                fill = b;
            }
        }
        JsonNode done = postJson(token, "/api/assistant/actions", Map.of("state", r.get("state").asText(),
                "slots", r.get("slots"), "page", page, "action", "FILL_FORM", "params", fill.get("params")), 200);
        assertEquals("FILL_NEED_FORM", done.get("ui").get(0).get("type").asText());

        String details = jdbc.queryForObject(
                "select details from audit_event where event_type = 'ASSISTANT_ACTION' order by id desc limit 1", String.class);
        assertEquals("{\"action\":\"FILL_FORM\",\"page\":\"NEED\"}", details);
    }

    @Test
    void answersFromKnowledgeBaseAndRefusesOffTopic() throws Exception {
        String token = login("romashka");
        JsonNode r = postJson(token, "/api/assistant/messages", Map.of("text", "Зачем нужно согласие на запрос в бюро?"), 200);
        assertEquals("faq.bureau-consent", r.get("sources").get(0).asText());

        r = postJson(token, "/api/assistant/messages", Map.of("text", "Какой сейчас курс доллара?"), 200);
        assertEquals("policy.off-topic", r.get("sources").get(0).asText());
        assertTrue(r.get("text").asText().startsWith("Я помогаю только с подбором и оформлением кредита"));
    }

    @Test
    void widgetEventsAreAuditedWithoutMessageText() throws Exception {
        String token = login("kim");
        postJson(token, "/api/assistant/events", Map.of("type", "OFFER_SHOWN", "signal", "IDLE_NEED", "page", "NEED"), 204);
        String details = jdbc.queryForObject(
                "select details from audit_event where event_type = 'ASSISTANT_OFFER_SHOWN' order by id desc limit 1", String.class);
        assertEquals("{\"page\":\"NEED\",\"signal\":\"IDLE_NEED\"}", details);
        postJson(token, "/api/assistant/events", Map.of("type", "OFFER_SHOWN", "signal", "drop table"), 400);
    }

    @Test
    void onlyClientsCanUseAssistantAndInputIsLimited() throws Exception {
        String underwriter = login("uw1");
        postJson(underwriter, "/api/assistant/messages", Map.of("text", "привет"), 403);
        call(post("/api/assistant/messages").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"привет\"}"), 401);

        String token = login("orlova");
        postJson(token, "/api/assistant/messages", Map.of("text", "а".repeat(1001)), 400);
        postJson(token, "/api/assistant/actions", Map.of("action", "UNKNOWN"), 400);
    }

    @Test
    void tooManyMessagesAreRejected() throws Exception {
        String token = login("sokolov");
        int last = 0;
        for (int i = 0; i < 21; i++) {
            last = mvc.perform(post("/api/assistant/messages").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"что такое залог\"}"))
                    .andReturn().getResponse().getStatus();
        }
        assertEquals(429, last);
        assertFalse(getJson(token, "/api/assistant/config", 200).isEmpty());
    }
}
