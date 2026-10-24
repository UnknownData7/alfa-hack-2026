package ru.hackathon.credit.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ru.hackathon.credit.scoring.ScoringRun;
import ru.hackathon.credit.scoring.ScoringRunRepository;

/** Сквозные сценарии из docs/TEST_SCENARIOS.md. */
class ScenarioIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ScoringRunRepository scoringRuns;

    /** Внутренняя оценка банка проверяется по базе: клиентскому API она недоступна. */
    private ScoringRun lastRun(long id) {
        return scoringRuns.findTopByApplicationIdOrderByRunNoDesc(id).orElseThrow();
    }

    /** В ответе клиенту нет баллов, грейда, факторов и причин решения. */
    private void assertNoInternalScoring(JsonNode app) {
        String json = app.toString();
        for (String field : new String[] {"\"decision\"", "\"grade\"", "\"score\"", "\"totalScore\"",
                "\"factors\"", "\"reasons\"", "\"reasonCode\"", "\"hardStop\""}) {
            assertFalse(json.contains(field), "Клиенту не должно уходить поле " + field + ": " + json);
        }
    }

    @Test
    void s1AutoApprovalToDisbursement() throws Exception {
        String token = login("romashka");
        JsonNode recommendations = postJson(token, "/api/products/recommend",
                Map.of("purpose", "WORKING_CAPITAL", "amount", 1_200_000, "termMonths", 9), 200);
        assertEquals("WORKING_EXPRESS", recommendations.get(0).get("code").asText());

        long id = draft(token, "WORKING_EXPRESS", "WORKING_CAPITAL", 1_200_000, 9, null);
        postJson(token, "/api/applications/" + id + "/submit", null, 202);
        JsonNode app = awaitStatus(token, id, "OFFER_MADE", "DECLINED", "MANUAL_REVIEW");

        assertEquals("OFFER_MADE", app.get("status").asText());
        assertEquals("A", lastRun(id).getGrade());
        assertNoInternalScoring(app);
        assertEquals("INITIAL", app.get("offer").get("kind").asText());
        assertEquals(0, app.get("offer").get("amount").decimalValue().compareTo(new java.math.BigDecimal("1200000")));

        postJson(token, "/api/applications/" + id + "/offer/accept", null, 200);
        // Без подтверждения ознакомления с условиями договор не подписывается
        postJson(token, "/api/applications/" + id + "/sign", Map.of("termsAccepted", false), 422);
        java.math.BigDecimal before = getJson(token, "/api/client/profile", 200).get("accountBalance").decimalValue();
        JsonNode issued = postJson(token, "/api/applications/" + id + "/sign", Map.of("termsAccepted", true), 200);

        assertEquals("ISSUED", issued.get("status").asText());
        assertTrue(issued.get("disbursement").get("simulated").asBoolean());
        java.math.BigDecimal after = getJson(token, "/api/client/profile", 200).get("accountBalance").decimalValue();
        assertEquals(0, after.subtract(before).compareTo(new java.math.BigDecimal("1200000")));
        // Повторное подписание невозможно
        postJson(token, "/api/applications/" + id + "/sign", Map.of("termsAccepted", true), 409);

        JsonNode activity = getJson(token, "/api/client/activity", 200);
        assertEquals("CREDIT", activity.get(0).get("type").asText());
        assertNoInternalScoring(activity);
        JsonNode feed = getJson(token, "/api/feed", 200);
        assertTrue(feed.toString().contains("Кредит выдан"));
    }

    @Test
    void s2AutomaticCounterOffer() throws Exception {
        String token = login("sokolov");
        long id = draft(token, "WORKING_EXPRESS", "WORKING_CAPITAL", 1_400_000, 6, null);
        postJson(token, "/api/applications/" + id + "/submit", null, 202);
        JsonNode app = awaitStatus(token, id, "OFFER_MADE", "DECLINED", "MANUAL_REVIEW");

        assertEquals("OFFER_MADE", app.get("status").asText());
        assertEquals("B", lastRun(id).getGrade());
        assertNoInternalScoring(app);
        assertEquals("COUNTER", app.get("offer").get("kind").asText());
        assertEquals(0, app.get("offer").get("amount").decimalValue().compareTo(new java.math.BigDecimal("850000")));
        assertFalse(app.get("offer").get("changes").isEmpty());
    }

    @Test
    void s3ManualReviewWithCollateralApproved() throws Exception {
        String client = login("sever-stroy");
        long id = draft(client, "EQUIPMENT_GROWTH", "EQUIPMENT", 6_000_000, 36,
                "Приобретаемая строительная техника, оценочная стоимость 7,5 млн ₽");
        postJson(client, "/api/applications/" + id + "/submit", null, 202);
        awaitStatus(client, id, "MANUAL_REVIEW");

        String uw = login("uw1");
        JsonNode card = postJson(uw, "/api/bank/tasks/take-next", null, 200);
        assertEquals(2, card.get("task").get("priority").asInt());
        long taskId = card.get("task").get("id").asLong();

        // Решение без заполненных проверок запрещено
        postJson(uw, "/api/bank/tasks/" + taskId + "/decision", Map.of("result", "APPROVE"), 422);
        for (JsonNode check : card.get("checks")) {
            putJson(uw, "/api/bank/tasks/" + taskId + "/checks/" + check.get("code").asText(),
                    Map.of("status", "OK", "comment", "Проверено"), 200);
        }
        postJson(uw, "/api/bank/tasks/" + taskId + "/decision", Map.of("result", "APPROVE"), 200);
        JsonNode app = awaitStatus(client, id, "OFFER_MADE");
        assertEquals("INITIAL", app.get("offer").get("kind").asText());
    }

    @Test
    void s4RequestInfoReturnsToSameUnderwriter() throws Exception {
        String client = login("kim");
        long id = draft(client, "WORKING_EXPRESS", "WORKING_CAPITAL", 800_000, 6, null);
        postJson(client, "/api/applications/" + id + "/submit", null, 202);
        JsonNode app = awaitStatus(client, id, "MANUAL_REVIEW", "OFFER_MADE", "DECLINED");
        assertEquals("MANUAL_REVIEW", app.get("status").asText());
        assertEquals("C", lastRun(id).getGrade());
        assertNoInternalScoring(app);
        assertEquals("Заявку рассматривает специалист банка",
                app.get("history").get(app.get("history").size() - 1).get("comment").asText());

        String uw1 = login("uw1");
        JsonNode card = postJson(uw1, "/api/bank/tasks/take-next", null, 200);
        assertEquals(app.get("number").asText(), card.get("application").get("number").asText());
        long taskId = card.get("task").get("id").asLong();
        for (JsonNode check : card.get("checks")) {
            putJson(uw1, "/api/bank/tasks/" + taskId + "/checks/" + check.get("code").asText(),
                    Map.of("status", "NEEDS_INFO", "comment", "Нужна выписка за 12 месяцев"), 200);
        }
        postJson(uw1, "/api/bank/tasks/" + taskId + "/decision",
                Map.of("result", "REQUEST_INFO", "requestText", "Приложите выписку по счёту за 12 месяцев"), 200);

        JsonNode needInfo = awaitStatus(client, id, "NEED_INFO");
        long requestId = needInfo.get("infoRequests").get(0).get("id").asLong();
        upload(client, id);
        postJson(client, "/api/applications/" + id + "/info-requests/" + requestId + "/answer",
                Map.of("answer", "Выписка приложена, выручка сезонная"), 200);
        awaitStatus(client, id, "MANUAL_REVIEW");

        // Повторная задача достаётся тому же андеррайтеру с наивысшим приоритетом
        String uw2 = login("uw2");
        JsonNode other = postJson(uw2, "/api/bank/tasks/take-next", null, 200);
        assertFalse(app.get("number").asText().equals(other.get("application").get("number").asText()));

        JsonNode mine = getJson(uw1, "/api/bank/tasks?filter=new", 200);
        JsonNode repeat = null;
        for (JsonNode t : mine) {
            if (t.get("applicationId").asLong() == id) {
                repeat = t;
            }
        }
        assertEquals(1, repeat.get("priority").asInt());
        assertTrue(repeat.get("repeat").asBoolean());

        long repeatId = repeat.get("id").asLong();
        JsonNode repeatCard = postJson(uw1, "/api/bank/tasks/" + repeatId + "/take", null, 200);
        assertTrue(repeatCard.get("documents").toString().contains("\"newForBank\":true"));
        JsonNode recalc = postJson(uw1, "/api/bank/tasks/" + repeatId + "/recalculate",
                Map.of("confirmedAnnualTurnover", 4_800_000), 200);
        // Запуски: первичный, повторный после ответа клиента и пересчёт андеррайтера
        assertEquals(3, recalc.get("run").get("runNo").asInt());
        assertEquals("RECALC", recalc.get("run").get("trigger").asText());
        for (JsonNode check : repeatCard.get("checks")) {
            putJson(uw1, "/api/bank/tasks/" + repeatId + "/checks/" + check.get("code").asText(),
                    Map.of("status", "OK", "comment", "Подтверждено выпиской"), 200);
        }
        postJson(uw1, "/api/bank/tasks/" + repeatId + "/decision", Map.of("result", "APPROVE_WITH_CHANGES",
                "comment", "Сумма снижена с учётом сезонности", "counterOffer",
                Map.of("amount", 600_000, "termMonths", 6)), 200);

        JsonNode offer = awaitStatus(client, id, "OFFER_MADE");
        assertEquals("COUNTER", offer.get("offer").get("kind").asText());
        // Комментарий андеррайтера остаётся внутри банка
        assertFalse(offer.toString().contains("сезонности"));
        assertNoInternalScoring(offer);
    }

    @Test
    void s5AutoDeclineWithoutReasonsForClient() throws Exception {
        String token = login("vector-opt");
        long id = draft(token, "WORKING_EXPRESS", "WORKING_CAPITAL", 2_000_000, 12, null);
        postJson(token, "/api/applications/" + id + "/submit", null, 202);
        JsonNode app = awaitStatus(token, id, "DECLINED", "OFFER_MADE", "MANUAL_REVIEW");

        assertEquals("DECLINED", app.get("status").asText());
        assertEquals("D", lastRun(id).getGrade());
        assertNoInternalScoring(app);
        assertEquals("NEW_APPLICATION", app.get("nextStep").get("action").asText());
    }

    @Test
    void s6HardStopDecline() throws Exception {
        String token = login("zarya");
        long id = draft(token, "WORKING_EXPRESS", "CASH_GAP", 500_000, 6, null);
        postJson(token, "/api/applications/" + id + "/submit", null, 202);
        JsonNode app = awaitStatus(token, id, "DECLINED", "OFFER_MADE", "MANUAL_REVIEW");

        assertEquals("DECLINED", app.get("status").asText());
        assertTrue(lastRun(id).isHardStop());
        assertNoInternalScoring(app);
    }

    @Test
    void missingDocumentsLeadToNeedInfoAndRepeatedSubmitIsRejected() throws Exception {
        String token = login("orlova");
        long id = postJson(token, "/api/applications", Map.of("productCode", "BUSINESS_OVERDRAFT",
                "purpose", "CASH_GAP", "amount", 300_000, "termMonths", 12, "consentDataProcessing", true,
                "consentCreditBureau", true), 201).get("id").asLong();
        postJson(token, "/api/applications/" + id + "/submit", null, 202);
        postJson(token, "/api/applications/" + id + "/submit", null, 409);
        JsonNode app = awaitStatus(token, id, "NEED_INFO");
        assertEquals("SYSTEM", app.get("infoRequests").get(0).get("source").asText());
    }

    @Test
    void overdraftLimitIsCappedByHalfOfMonthlyTurnover() throws Exception {
        String token = login("orlova");
        JsonNode error = postJson(token, "/api/applications", Map.of("productCode", "BUSINESS_OVERDRAFT",
                "purpose", "CASH_GAP", "amount", 500_000, "termMonths", 12, "consentDataProcessing", true,
                "consentCreditBureau", true), 422);
        assertEquals("TURNOVER_LIMIT_EXCEEDED", error.get("code").asText());

        JsonNode recommendations = postJson(token, "/api/products/recommend",
                Map.of("purpose", "CASH_GAP", "amount", 500_000, "termMonths", 12), 200);
        for (JsonNode r : recommendations) {
            if (r.get("code").asText().equals("BUSINESS_OVERDRAFT")) {
                assertFalse(r.get("available").asBoolean());
            }
        }
    }
}
