package ru.hackathon.credit.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Map;

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
}
