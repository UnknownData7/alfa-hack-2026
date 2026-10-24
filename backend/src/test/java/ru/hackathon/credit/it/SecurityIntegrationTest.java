package ru.hackathon.credit.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

/** Проверка прав на сервере: роли, чужие заявки, вход. */
class SecurityIntegrationTest extends IntegrationTestBase {

    @Test
    void healthIsPublic() throws Exception {
        call(get("/actuator/health"), 200);
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        call(get("/api/applications"), 401);
        call(get("/api/bank/tasks"), 401);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json(Map.of("login", "romashka", "password", "wrong"))), 401);
    }

    @Test
    void meReturnsRole() throws Exception {
        String token = login("uw1");
        org.junit.jupiter.api.Assertions.assertEquals("UNDERWRITER", getJson(token, "/api/auth/me", 200).get("role").asText());
    }

    @Test
    void clientCannotUseBankEndpointsAndUnderwriterCannotUseClientEndpoints() throws Exception {
        getJson(login("romashka"), "/api/bank/tasks", 403);
        getJson(login("uw1"), "/api/applications", 403);
    }

    @Test
    void clientCannotSeeAnotherCompanyApplication() throws Exception {
        String owner = login("romashka");
        long id = draft(owner, "WORKING_EXPRESS", "WORKING_CAPITAL", 1_200_000, 9, null);
        getJson(login("sokolov"), "/api/applications/" + id, 404);
    }

    @Test
    void loginIsBlockedAfterTooManyFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(json(Map.of("login", "nobody-rate-limit", "password", "wrong" + i))), 401);
        }
        org.springframework.test.web.servlet.MvcResult blocked = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", "nobody-rate-limit", "password", "wrong")))).andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(429, blocked.getResponse().getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(blocked.getResponse().getHeader("Retry-After"));
        // Блокировка касается только этого логина
        login("romashka");
    }

    @Test
    void responseCarriesCorrelationId() throws Exception {
        String header = mvc.perform(get("/actuator/health").header("X-Correlation-Id", "test-trace-1"))
                .andReturn().getResponse().getHeader("X-Correlation-Id");
        org.junit.jupiter.api.Assertions.assertEquals("test-trace-1", header);
    }

    @Test
    void fileWithWrongContentIsRejected() throws Exception {
        String token = login("romashka");
        long id = draft(token, "WORKING_EXPRESS", "WORKING_CAPITAL", 1_200_000, 9, null);
        org.springframework.mock.web.MockMultipartFile fake = new org.springframework.mock.web.MockMultipartFile(
                "file", "statement.pdf", "application/pdf", "MZ not a pdf".getBytes());
        JsonNode error = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/applications/{id}/documents", id).file(fake).param("docType", "BANK_STATEMENT")
                .header("Authorization", "Bearer " + token), 400);
        org.junit.jupiter.api.Assertions.assertEquals("FILE_CONTENT_MISMATCH", error.get("code").asText());
    }
}
