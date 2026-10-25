package ru.hackathon.credit.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * База интеграционных тестов (нужен Docker): настоящий PostgreSQL в контейнере, профиль demo с сид-данными,
 * конвейер без пауз. Перед каждым тестом база возвращается к исходному состоянию.
 */
@SpringBootTest(properties = {
        "app.pipeline.step-delay-ms=0",
        "app.mocks.latency-ms=0",
        "app.uploads.dir=target/test-uploads"})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Demo#2026";

    /** Один контейнер на все тестовые классы, чтобы кэшированный контекст Spring не терял базу. */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.10-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
    }

    protected String login(String login) throws Exception {
        JsonNode body = call(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login", login, "password", PASSWORD))), 200);
        return body.get("token").asText();
    }

    protected JsonNode call(RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        int status = result.getResponse().getStatus();
        String content = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if (status != expectedStatus) {
            throw new AssertionError("Ожидался статус " + expectedStatus + ", получен " + status + ": " + content);
        }
        return content.isEmpty() ? mapper.createObjectNode() : mapper.readTree(content);
    }

    protected JsonNode getJson(String token, String url, int expected) throws Exception {
        return call(get(url).header("Authorization", "Bearer " + token), expected);
    }

    protected JsonNode postJson(String token, String url, Object body, int expected) throws Exception {
        return call(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : json(body)), expected);
    }

    protected JsonNode putJson(String token, String url, Object body, int expected) throws Exception {
        return call(put(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json(body)), expected);
    }

    /** Черновик с согласиями и выпиской, готовый к отправке. */
    protected long draft(String token, String product, String purpose, long amount, int term, String collateral)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("productCode", product);
        body.put("purpose", purpose);
        body.put("amount", amount);
        body.put("termMonths", term);
        body.put("consentDataProcessing", true);
        body.put("consentCreditBureau", true);
        if (collateral != null) {
            body.put("collateralDescription", collateral);
        }
        long id = postJson(token, "/api/applications", body, 201).get("id").asLong();
        upload(token, id);
        return id;
    }

    protected void upload(String token, long id) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf",
                "%PDF-1.4 synthetic statement".getBytes());
        call(multipart("/api/applications/{id}/documents", id).file(file).param("docType", "BANK_STATEMENT")
                .header("Authorization", "Bearer " + token), 201);
    }

    /** Ждёт, пока асинхронный конвейер переведёт заявку в один из ожидаемых статусов. */
    protected JsonNode awaitStatus(String token, long id, String... statuses) throws Exception {
        Set<String> expected = Set.of(statuses);
        JsonNode last = null;
        for (int i = 0; i < 100; i++) {
            last = getJson(token, "/api/applications/" + id, 200);
            if (expected.contains(last.get("status").asText())) {
                return last;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Заявка не перешла в " + expected + ", текущий статус " + last.get("status").asText());
    }

    protected String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }
}
