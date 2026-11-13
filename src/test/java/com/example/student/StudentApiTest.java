package com.example.student;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("demo")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:student_api_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class StudentApiTest {
    private static final String STUDENTS = "/api/v1/student";
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = new JsonMapper();

    @LocalServerPort
    private int port;

    @Test
    void searchesNamesAndIdBeforeSortingAndPaginating() throws Exception {
        String admin = login("admin@example.com", "admin");
        String teacher = login("teacher@example.com", "teacher");
        long firstId = create(admin, """
                {"name":"Анна","surname":"Проверкина"}
                """);
        long secondId = create(admin, """
                {"name":"Борис","surname":"Проверкина"}
                """);
        create(admin, """
                {"name":"Рита","surname":"Символ%Проверкина"}
                """);

        String query = STUDENTS + "?query=" + encode("ПРОВЕРКИНА") + "&sort=name&direction=asc&size=1";
        JsonNode firstPage = read(exchange("GET", query + "&page=0", teacher, null), 200);
        assertThat(firstPage.path("totalCount").intValue()).isEqualTo(3);
        assertThat(firstPage.path("response").size()).isEqualTo(1);
        assertThat(firstPage.path("response").get(0).path("id").longValue()).isEqualTo(firstId);
        JsonNode secondPage = read(exchange("GET", query + "&page=1", teacher, null), 200);
        assertThat(secondPage.path("response").get(0).path("id").longValue()).isEqualTo(secondId);
        JsonNode emptyPage = read(exchange("GET", query + "&page=10", teacher, null), 200);
        assertThat(emptyPage.path("response").size()).isZero();
        assertThat(emptyPage.path("totalCount").intValue()).isEqualTo(3);

        JsonNode byName = read(exchange("GET", STUDENTS + "?query=" + encode("проверкина анна"), teacher, null), 200);
        assertThat(byName.path("response").get(0).path("id").longValue()).isEqualTo(firstId);
        JsonNode byId = read(exchange("GET", STUDENTS + "?query=" + firstId, teacher, null), 200);
        assertThat(byId.path("totalCount").intValue()).isEqualTo(1);
        assertThat(byId.path("response").get(0).path("id").longValue()).isEqualTo(firstId);

        JsonNode literalPercent = read(exchange("GET", STUDENTS + "?query=%25", teacher, null), 200);
        assertThat(literalPercent.path("totalCount").intValue()).isEqualTo(1);
        assertThat(literalPercent.path("response").get(0).path("name").stringValue()).isEqualTo("Рита");
    }

    @Test
    void allowsAdminToManageStudentsAndTeacherToReadThem() throws Exception {
        String admin = login("admin@example.com", "admin");
        String teacher = login("teacher@example.com", "teacher");
        String createRequest = """
                {"name":"Леонид","surname":"Петров","phone_number":"+7 900 123-45-67","email":"petrov@example.com"}
                """;
        assertThat(exchange("POST", STUDENTS + "/save", teacher, createRequest).statusCode()).isEqualTo(403);
        long studentId = create(admin, createRequest);
        String studentPath = STUDENTS + "/" + studentId;

        JsonNode details = read(exchange("GET", STUDENTS + "/info/" + studentId, teacher, null), 200);
        assertThat(details.path("phoneNumber").stringValue()).isEqualTo("+7 900 123-45-67");

        JsonNode contactUpdate = read(exchange("PUT", studentPath, admin, """
                {"email":"updated@example.com"}
                """), 200);
        assertThat(contactUpdate.path("name").stringValue()).isEqualTo("Леонид");
        details = read(exchange("GET", studentPath, teacher, null), 200);
        assertThat(details.path("email").stringValue()).isEqualTo("updated@example.com");
        assertThat(details.path("phoneNumber").stringValue()).isEqualTo("+7 900 123-45-67");

        read(exchange("PUT", studentPath, admin, """
                {"name":"Лев","surname":"Орлов","phoneNumber":"","email":""}
                """), 200);
        details = read(exchange("GET", studentPath, teacher, null), 200);
        assertThat(details.path("name").stringValue()).isEqualTo("Лев");
        assertThat(details.path("surname").stringValue()).isEqualTo("Орлов");
        assertThat(details.path("phoneNumber").isNull()).isTrue();
        assertThat(details.path("email").isNull()).isTrue();

        assertThat(exchange("DELETE", studentPath, teacher, null).statusCode()).isEqualTo(403);
        assertThat(exchange("DELETE", studentPath, admin, null).statusCode()).isEqualTo(200);
        assertThat(exchange("DELETE", studentPath, admin, null).statusCode()).isEqualTo(404);
        assertThat(exchange("GET", studentPath, teacher, null).statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsInvalidStudentDataAndListOptions() throws Exception {
        String admin = login("admin@example.com", "admin");

        assertThat(exchange("POST", STUDENTS + "/save", admin, """
                {"name":"","surname":"Петров","email":"invalid"}
                """).statusCode()).isEqualTo(400);
        assertThat(exchange("GET", STUDENTS + "?size=0", admin, null).statusCode()).isEqualTo(400);
        assertThat(exchange("GET", STUDENTS + "?page=-1", admin, null).statusCode()).isEqualTo(400);
        assertThat(exchange("GET", STUDENTS + "?sort=email", admin, null).statusCode()).isEqualTo(400);
        assertThat(exchange("GET", STUDENTS + "?direction=other", admin, null).statusCode()).isEqualTo(400);
    }

    private String login(String email, String password) throws Exception {
        String credentials = json.writeValueAsString(Map.of("email", email, "password", password));
        return read(exchange("POST", "/api/v1/auth/login", null, credentials), 200).path("token").stringValue();
    }

    private long create(String token, String body) throws Exception {
        return read(exchange("POST", STUDENTS + "/save", token, body), 200).path("id").longValue();
    }

    private JsonNode read(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as("response body: %s", response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }

    private HttpResponse<String> exchange(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String query) {
        return URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
