package com.smartmealplanner.shared.web;

import java.util.UUID;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import com.smartmealplanner.auth.SecurityConfiguration;
import com.smartmealplanner.mealplanning.PlanEntryDateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(properties = "app.cors.allowed-origins=https://planner.example")
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, ApiProblems.class, ApiExceptionHandler.class,
        RequestIdFilter.class, ApiFoundationTest.ProbeController.class})
class ApiFoundationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;

    @Test
    void protectsRoutesAndReturnsSafe401() throws Exception {
        mvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/api/v1/probe").with(user("test"))).andExpect(status().isOk());
    }

    @Test
    void enforcesAdminRoleAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/admin/probe").with(user("test")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/admin/probe").with(user("test").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/probe").with(user("test"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesRequestAndMalformedJson() throws Exception {
        for (String body : new String[]{"{}", "{", "{\"planDate\":\"not-a-date\"}"}) {
            mvc.perform(post("/api/v1/probe").with(user("test")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        mvc.perform(post("/api/v1/probe").with(user("test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"planDate\":\"2026-09-01\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"/api/v1/probe/parameter", "/api/v1/probe/parameter?count=invalid",
            "/api/v1/probe/parameter?count=0", "/api/v1/probe/header"})
    void frameworkRequestValidationIsSafe400(String path) throws Exception {
        mvc.perform(get(path).with(user("test")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("The request is invalid."))
                .andExpect(jsonPath("$.requestId").isString());
    }

    @Test
    void preservesMethodAndMediaTypeErrors() throws Exception {
        mvc.perform(delete("/api/v1/probe").with(user("test")).with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/probe").with(user("test")).with(csrf())
                .contentType(MediaType.TEXT_PLAIN).content("private diagnostic"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private diagnostic"))));
    }

    @ParameterizedTest
    @CsvSource({"400,BAD_REQUEST", "401,UNAUTHORIZED", "403,FORBIDDEN", "404,NOT_FOUND", "409,CONFLICT", "500,INTERNAL_SERVER_ERROR"})
    void mapsErrorsWithoutLeakingDetails(int code, String name) throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/probe/error/" + code).with(user("test")).header("X-Request-ID", id))
                .andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(name))
                .andExpect(jsonPath("$.requestId").value(id))
                .andExpect(header().string("X-Request-ID", id))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private diagnostic"))));
    }

    @Test
    void unknownAuthenticatedRouteIs404() throws Exception {
        mvc.perform(get("/api/v1/absent").with(user("test")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void replacesUnsafeCorrelationId() throws Exception {
        var response = mvc.perform(get("/api/v1/probe").header("X-Request-ID", "untrusted value"))
                .andReturn().getResponse();
        assertThatCode(() -> UUID.fromString(response.getHeader("X-Request-ID"))).doesNotThrowAnyException();
    }

    @Test
    void allowsOnlyConfiguredCorsOrigin() throws Exception {
        mvc.perform(options("/api/v1/probe").header("Origin", "https://planner.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://planner.example"));
        mvc.perform(options("/api/v1/probe").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void passwordEncodingUsesSaltedStandardEncoder() {
        String disposable = UUID.randomUUID().toString();
        String first = encoder.encode(disposable);
        assertThat(first).startsWith("{bcrypt}").isNotEqualTo(disposable).isNotEqualTo(encoder.encode(disposable));
        assertThat(encoder.matches(disposable, first)).isTrue();
        assertThat(encoder.matches(UUID.randomUUID().toString(), first)).isFalse();
    }

    /** Test fixtures only: no probe/error routes ship in the application. */
    @RestController
    static class ProbeController {
        @GetMapping({"/api/v1/probe", "/api/v1/admin/probe"})
        String get() { return "ok"; }
        @PostMapping("/api/v1/probe")
        void post(@Valid @RequestBody PlanEntryDateRequest request) {}
        @GetMapping("/api/v1/probe/parameter")
        void parameter(@RequestParam @Min(1) int count) {}
        @GetMapping("/api/v1/probe/header")
        void header(@RequestHeader("X-Probe") String value) {}
        @GetMapping("/api/v1/probe/error/{status}")
        void error(@PathVariable int status) {
            throw switch (status) {
                case 400 -> new InvalidRequestException();
                case 401 -> new BadCredentialsException("private diagnostic");
                case 403 -> new AccessDeniedException("private diagnostic");
                case 404 -> new EntityNotFoundException("private diagnostic");
                case 409 -> new DataIntegrityViolationException("private diagnostic");
                default -> new IllegalStateException("private diagnostic");
            };
        }
    }
}
