package com.solitaire.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameApiIT {

    @Container
    @SuppressWarnings("resource")
    static MSSQLServerContainer<?> sql = new MSSQLServerContainer<>(
                    DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-CU12-ubuntu-22.04"))
            .acceptLicense()
            .withPassword("Test_Password_123");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", sql::getJdbcUrl);
        r.add("spring.datasource.username", sql::getUsername);
        r.add("spring.datasource.password", sql::getPassword);
        r.add("solitaire.jwt.access-secret", () -> "access-secret-key-32-bytes-min!!");
        r.add("solitaire.jwt.refresh-secret", () -> "refresh-secret-key-32-bytes-min!");
    }

    @Autowired
    TestRestTemplate http;

    @Test
    void fullFlow() {
        ResponseEntity<Map> unauth = http.getForEntity("/api/v1/games/current", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauth.getStatusCode());

        Map register = http.postForObject(
                "/api/v1/auth/register",
                Map.of("email", "player@example.com", "password", "password1"),
                Map.class);
        assertNotNull(register.get("accessToken"));
        String token = register.get("accessToken").toString();

        ResponseEntity<Map> dup = http.postForEntity(
                "/api/v1/auth/register",
                Map.of("email", "player@example.com", "password", "password1"),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, dup.getStatusCode());
        assertEquals("EMAIL_TAKEN", dup.getBody().get("code"));

        ResponseEntity<Map> badLogin = http.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "missing@example.com", "password", "password1"),
                Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, badLogin.getStatusCode());

        HttpHeaders headers = bearer(token);
        Map created = http.exchange(
                        "/api/v1/games",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("abandonExisting", false), headers),
                        Map.class)
                .getBody();
        assertEquals("IN_PROGRESS", created.get("status"));
        String gameId = created.get("gameId").toString();
        int version = ((Number) created.get("version")).intValue();
        Map board = (Map) created.get("board");
        assertEquals(7, ((java.util.List<?>) board.get("tableau")).size());
        assertEquals(24, ((java.util.List<?>) board.get("stock")).size());
        assertTrue(created.containsKey("stalemate"));

        Map drawn = http.exchange(
                        "/api/v1/games/" + gameId + "/moves",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("version", version, "move", Map.of("type", "DRAW")), headers),
                        Map.class)
                .getBody();
        assertEquals(1, ((Number) drawn.get("moveCount")).intValue());
        int v1 = ((Number) drawn.get("version")).intValue();
        Map boardAfter = (Map) drawn.get("board");
        int stockAfter = ((java.util.List<?>) boardAfter.get("stock")).size();
        assertEquals(23, stockAfter);

        ResponseEntity<Map> conflict = http.exchange(
                "/api/v1/games/" + gameId + "/moves",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("version", version, "move", Map.of("type", "DRAW")), headers),
                Map.class);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());

        ResponseEntity<Map> illegal = http.exchange(
                "/api/v1/games/" + gameId + "/moves",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "version",
                                v1,
                                "move",
                                Map.of(
                                        "type",
                                        "MOVE",
                                        "from",
                                        Map.of("pile", "STOCK", "index", 0),
                                        "to",
                                        Map.of("pile", "TABLEAU", "index", 0),
                                        "count",
                                        1)),
                        headers),
                Map.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, illegal.getStatusCode());
        Map still = http.exchange("/api/v1/games/current", HttpMethod.GET, new HttpEntity<>(headers), Map.class)
                .getBody();
        assertEquals(1, ((Number) still.get("moveCount")).intValue());
        assertEquals(23, ((java.util.List<?>) ((Map) still.get("board")).get("stock")).size());

        Map undone = http.exchange(
                        "/api/v1/games/" + gameId + "/undo",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("version", v1), headers),
                        Map.class)
                .getBody();
        assertEquals(0, ((Number) undone.get("moveCount")).intValue());
        int vUndo = ((Number) undone.get("version")).intValue();

        Map restarted = http.exchange(
                        "/api/v1/games",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("abandonExisting", true), headers),
                        Map.class)
                .getBody();
        assertNotEquals(gameId, restarted.get("gameId"));
        Map current = http.exchange("/api/v1/games/current", HttpMethod.GET, new HttpEntity<>(headers), Map.class)
                .getBody();
        assertEquals(restarted.get("gameId"), current.get("gameId"));
        assertTrue(vUndo >= 1);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
