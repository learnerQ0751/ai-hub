package com.qtua.aihub;

import com.qtua.aihub.console.ConsoleApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 控制台会话端点验收：登录、会话探测、退出、cookie 属性、失败锁定与未登录 401。
 */
@SpringBootTest(classes = ConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsoleSessionIntegrationTest {

    private static final String MASTER_KEY = "session-master";
    private static final String USERNAME = "console-admin";
    private static final String PASSWORD = "s3cret-pass";
    /** PBKDF2-SHA256, 120000 次迭代，固定盐（测试专用）。 */
    private static final String PASSWORD_HASH = "pbkdf2-sha256$120000$"
            + "MDEyMzQ1Njc4OWFiY2RlZg==$5pjJn9GdzAuKQu2Ks3J/UnvuQ2OILgzERQsEUK+vSOQ=";
    private static final String COOKIE_NAME = "aihub_admin_session";

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.master-key", () -> MASTER_KEY);
        registry.add("console.session.enabled", () -> "true");
        registry.add("console.session.username", () -> USERNAME);
        registry.add("console.session.password-hash", () -> PASSWORD_HASH);
        registry.add("console.session.cookie-secure", () -> "false");
        registry.add("console.session.timeout", () -> "1h");
        registry.add("console.session.max-failed-attempts", () -> "5");
        registry.add("console.session.lock-duration", () -> "1s");
    }

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                })
                .build();
    }

    @Test
    @Order(1)
    void login_sets_hardened_cookie_and_probe_returns_account() {
        ResponseEntity<String> login = login(PASSWORD);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains(USERNAME).contains("expires_at")
                .doesNotContain("token");
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().contains(COOKIE_NAME + "=")
                .contains("HttpOnly").contains("SameSite=Lax").contains("Path=/v1/admin")
                .doesNotContain("Secure");

        ResponseEntity<String> probe = getWithCookie("/v1/admin/session", cookieValue(setCookie));
        assertThat(probe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(probe.getBody()).contains(USERNAME).contains("expires_at");
    }

    @Test
    @Order(2)
    void session_cookie_authorizes_admin_endpoints() {
        ResponseEntity<String> login = login(PASSWORD);
        String cookie = cookieValue(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        ResponseEntity<String> keys = getWithCookie("/v1/admin/keys?limit=1", cookie);

        assertThat(keys.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(keys.getBody()).contains("\"items\"");
    }

    @Test
    @Order(3)
    void missing_or_invalid_session_is_401() {
        ResponseEntity<String> anonymous = restClient.get()
                .uri("/v1/admin/keys")
                .retrieve().toEntity(String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> forged = getWithCookie("/v1/admin/session", "not-a-real-token");
        assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(4)
    void logout_clears_cookie_and_invalidates_session() {
        ResponseEntity<String> login = login(PASSWORD);
        String cookie = cookieValue(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        ResponseEntity<String> logout = restClient.delete()
                .uri("/v1/admin/session")
                .header(HttpHeaders.COOKIE, COOKIE_NAME + "=" + cookie)
                .retrieve().toEntity(String.class);
        assertThat(logout.getStatusCode().is2xxSuccessful()).isTrue();
        String cleared = logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cleared).isNotNull().contains("Max-Age=0");

        ResponseEntity<String> probe = getWithCookie("/v1/admin/session", cookie);
        assertThat(probe.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(5)
    void wrong_password_is_401_and_success_resets_failures() {
        ResponseEntity<String> wrong = login("wrong-password");
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).contains("auth_error");

        ResponseEntity<String> success = login(PASSWORD);
        assertThat(success.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(6)
    void five_failures_lock_account_with_429() throws InterruptedException {
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(login("wrong-" + attempt).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<String> fifth = login("wrong-5");
        assertThat(fifth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(fifth.getBody()).contains("rate_limit_error");

        assertThat(login(PASSWORD).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        Thread.sleep(1200L);
        assertThat(login(PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> login(String password) {
        String body = "{\"username\":\"" + USERNAME + "\",\"password\":\"" + password + "\"}";
        return restClient.post().uri("/v1/admin/session")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> getWithCookie(String uri, String cookie) {
        return restClient.get().uri(uri)
                .header(HttpHeaders.COOKIE, COOKIE_NAME + "=" + cookie)
                .retrieve().toEntity(String.class);
    }

    private String cookieValue(String setCookie) {
        assertThat(setCookie).isNotNull();
        String pair = setCookie.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }
}
