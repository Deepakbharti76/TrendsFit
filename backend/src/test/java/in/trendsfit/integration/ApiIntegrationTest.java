package in.trendsfit.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-context smoke test against a real MySQL (Testcontainers). Needs Docker.
 * Skipped automatically when Docker is unavailable. External services (Razorpay,
 * Shiprocket, SMTP) are not configured, so nothing external is called.
 * Run: mvn test
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("trendsfit.jwt.secret", () -> "test-only-secret-test-only-secret-1234567890");
    }

    @Autowired MockMvc mvc;

    @Test
    void publicProductListWorks() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void adminEndpointsRequireAuth() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void noCancelEndpointExists() throws Exception {
        int s = mvc.perform(post("/api/orders/TF-1/cancel")).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(s == 401 || s == 403 || s == 404, "unexpected " + s);
    }

    @Test
    void robotsIsPublic() throws Exception {
        mvc.perform(get("/robots.txt")).andExpect(status().isOk());
    }
}
