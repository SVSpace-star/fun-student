package com.example.student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("demo")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:account_api_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class AccountApiTest {
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String USERS = "/api/v1/users";
    private static final String STUDENTS = "/api/v1/student";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final JsonMapper JSON = new JsonMapper();

    @LocalServerPort
    private int port;

    @Test
    void servesInterfaceWithoutAuthenticationButProtectsData() throws Exception {
        HttpResponse<String> shell = request("GET", "/", null, null);
        assertThat(shell.statusCode()).isEqualTo(200);
        assertThat(shell.body()).contains("<html", "lang=\"ru\"");

        HttpResponse<String> unauthorized = request("GET", STUDENTS, null, null);
        assertError(unauthorized, 401);
        assertThat(unauthorized.headers().firstValue("WWW-Authenticate")).contains("Bearer");

        String teacher = login("teacher@example.com", "teacher");
        assertError(request("GET", USERS, teacher, null), 403);
        assertError(request("GET", STUDENTS, superAdmin(), null), 403);
    }

    @Test
    void changesAccountByIdAndAppliesCurrentRolesToExistingToken() throws Exception {
        String administrator = superAdmin();
        String email = "account-flow@example.com";
        String password = "Пароль123";
        JsonNode created = expect(request("POST", USERS + "/save", administrator,
                account(email, password, "TEACHER")), 200);
        long id = created.path("id").longValue();
        assertThat(id).isPositive();
        assertThat(created.has("password")).isFalse();
        String token = login(email, password);
        assertThat(request("GET", STUDENTS, token, null).statusCode()).isEqualTo(200);

        String path = USERS + "/" + id;
        expect(request("PUT", path, administrator, account(email, "", "SUPER_ADMIN")), 200);
        assertError(request("GET", STUDENTS, token, null), 403);
        assertThat(request("GET", USERS, token, null).statusCode()).isEqualTo(200);

        String newEmail = "account-renamed@example.com";
        JsonNode updated = expect(request("PUT", path, administrator,
                account(newEmail, null, "ADMIN", "SUPER_ADMIN")), 200);
        assertThat(updated.path("id").longValue()).isEqualTo(id);
        assertThat(updated.path("email").stringValue()).isEqualTo(newEmail);
        assertError(request("GET", USERS, token, null), 401);
        String renamedToken = login(newEmail, password);
        assertThat(request("GET", STUDENTS, renamedToken, null).statusCode()).isEqualTo(200);

        String newPassword = "Обновление123";
        expect(request("PUT", path, administrator, account(newEmail, newPassword, "ADMIN")), 200);
        assertError(request("POST", LOGIN, null, credentials(newEmail, password)), 401);
        String newToken = login(newEmail, newPassword);
        assertError(request("GET", USERS, newToken, null), 403);

        JsonNode list = expect(request("GET", USERS, administrator, null), 200);
        assertThat(list.path("totalCount").intValue()).isEqualTo(list.path("responses").size());
        assertThat(list.toString()).contains(newEmail).doesNotContain("password", newPassword);
        assertThat(request("DELETE", path, administrator, null).statusCode()).isEqualTo(200);
        assertError(request("GET", STUDENTS, newToken, null), 401);
        assertError(request("DELETE", path, administrator, null), 404);
        expect(request("POST", USERS + "/save", administrator, account(newEmail, newPassword, "ADMIN")), 200);
        assertError(request("GET", STUDENTS, newToken, null), 401);
    }

    @Test
    void detectsDuplicateEmailAndMissingAccount() throws Exception {
        String admin = superAdmin();
        assertError(request("POST", USERS + "/save", admin,
                account("teacher@example.com", "password123", "TEACHER")), 409);
        long id = expect(request("POST", USERS + "/save", admin,
                account("duplicate-check@example.com", "password123", "TEACHER")), 200).path("id").longValue();
        assertError(request("PUT", USERS + "/" + id, admin,
                account("teacher@example.com", null, "TEACHER")), 409);
        assertError(request("PUT", USERS + "/99999", admin,
                account("missing@example.com", null, "TEACHER")), 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"email\":\"bad\",\"password\":\"pass123\",\"roles\":[\"TEACHER\"]}",
            "{\"email\":\"a@example.com\",\"password\":\"pass123\",\"roles\":[]}",
            "{\"email\":\"a@example.com\",\"password\":\"pass123\",\"roles\":[null]}",
            "{\"email\":\"a@example.com\",\"password\":\"pass123\",\"roles\":[\"UNKNOWN\"]}",
            "{\"email\":\"a@example.com\",\"password\":\"short\",\"roles\":[\"TEACHER\"]}",
            "{\"email\":\"a@example.com\",\"roles\":[\"TEACHER\"]}"})
    void rejectsInvalidAccountPayloads(String payload) throws Exception {
        assertError(request("POST", USERS + "/save", superAdmin(), payload), 400);
    }

    @Test
    void handlesWrongCredentialsAndUnusableTokens() throws Exception {
        assertError(request("POST", LOGIN, null, credentials("teacher@example.com", "wrong")), 401);
        assertError(request("POST", LOGIN, null, credentials("missing@example.com", "wrong")), 401);
        assertError(request("POST", LOGIN, null, "{}"), 400);
        assertError(request("GET", STUDENTS, "invalid.jwt", null), 401);
        assertError(request("GET", STUDENTS, "", null), 401);

        String admin = superAdmin();
        assertError(request("POST", USERS + "/save", admin,
                account("long-password@example.com", "я".repeat(37), "TEACHER")), 400);
        assertError(request("GET", "/api/v1/unknown-path", admin, null), 404);
        assertError(request("DELETE", USERS + "/bad-id", admin, null), 400);
        assertError(request("POST", LOGIN, null, credentials("teacher@example.com", "я".repeat(37))), 401);
    }

    private String superAdmin() throws Exception {
        return login("superadmin@example.com", "superAdmin");
    }

    private String login(String email, String password) throws Exception {
        JsonNode session = expect(request("POST", LOGIN, null, credentials(email, password)), 200);
        assertThat(session.path("type").stringValue()).isEqualTo("Bearer");
        assertThat(session.path("expiresIn").intValue()).isEqualTo(3600);
        return session.path("token").stringValue();
    }

    private String credentials(String email, String password) {
        return JSON.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String account(String email, String password, String... roles) {
        return password == null
                ? JSON.writeValueAsString(Map.of("email", email, "roles", List.of(roles)))
                : JSON.writeValueAsString(Map.of("email", email, "password", password, "roles", List.of(roles)));
    }

    private void assertError(HttpResponse<String> response, int status) {
        JsonNode body = expect(response, status);
        assertThat(body.path("statusCode").intValue()).isEqualTo(status);
        assertThat(body.path("message").stringValue()).isNotBlank();
    }

    private JsonNode expect(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as("HTTP body: %s", response.body()).isEqualTo(status);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return CLIENT.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
