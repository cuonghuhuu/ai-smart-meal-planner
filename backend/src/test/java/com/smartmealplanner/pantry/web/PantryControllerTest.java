package com.smartmealplanner.pantry.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.servlet.http.Cookie;

import com.smartmealplanner.auth.SecurityConfiguration;
import com.smartmealplanner.pantry.PantryService;
import com.smartmealplanner.shared.web.ApiExceptionHandler;
import com.smartmealplanner.shared.web.ApiProblems;
import com.smartmealplanner.shared.web.RequestIdFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PantryController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfiguration.class,
        ApiProblems.class,
        ApiExceptionHandler.class,
        PantryExceptionHandler.class,
        RequestIdFilter.class
})
class PantryControllerTest {

    private static final UUID USER_PUBLIC_ID = UUID.randomUUID();
    private static final UUID PANTRY_ITEM_PUBLIC_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PantryService pantry;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void configureTestBearerTokens() throws Exception {
        when(jwtDecoder.decode("native-access-token"))
                .thenReturn(jwtFor("native-access-token"));
        when(jwtDecoder.decode("browser-access-token"))
                .thenReturn(jwtFor("browser-access-token"));
        when(jwtDecoder.decode("fake-token"))
                .thenThrow(new BadJwtException("invalid test token"));
    }

    private Jwt jwtFor(String token) {
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(USER_PUBLIC_ID.toString())
                .build();
    }

    @Test
    void cookieFreeBearerCreateAndUpdateDoNotRequireCsrf() throws Exception {
        mvc.perform(post("/api/v1/me/pantry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer native-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/me/pantry/{publicId}", PANTRY_ITEM_PUBLIC_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer native-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void bearerPantryMutationsRemainCsrfExemptWithRefreshCookie() throws Exception {
        Cookie refreshCookie = new Cookie("__Host-smartmeal_refresh", "opaque");

        mvc.perform(post("/api/v1/me/pantry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer browser-access-token")
                        .cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/me/pantry/{publicId}", PANTRY_ITEM_PUBLIC_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer browser-access-token")
                        .cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void cookieFreeMutationWithoutBearerStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/me/pantry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pantry);
    }

    @Test
    void validCsrfWithoutAuthenticationCannotReachPantry() throws Exception {
        mvc.perform(post("/api/v1/me/pantry")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(pantry);
    }

    @Test
    void invalidAuthorizationSchemesDoNotBypassCsrf() throws Exception {
        for (String authorization : new String[]{
                "Basic abc123", "Token abc123", "Bearer", "Bearer   "
        }) {
            mvc.perform(post("/api/v1/me/pantry")
                            .header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void bearerHeaderWithoutAuthenticationRemainsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/me/pantry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer fake-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(pantry);
    }

    @Test
    void unrelatedNonBearerUnsafeEndpointStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/me/meal-plans/generate")
                        .with(user("test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cookieFreeBearerActionEndpointsDoNotRequireCsrf() throws Exception {
        for (String action : new String[]{"adjust", "consume", "discard"}) {
            mvc.perform(post("/api/v1/me/pantry/{publicId}/{action}",
                            PANTRY_ITEM_PUBLIC_ID, action)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer native-access-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void mapsStalePantryMutationToTheStandardConflictResponse()
            throws Exception {
        when(pantry.adjust(
                eq(USER_PUBLIC_ID),
                eq(PANTRY_ITEM_PUBLIC_ID),
                any(AdjustPantryItemRequest.class)))
                .thenThrow(new OptimisticLockingFailureException(
                        "stale pantry item"));

        mvc.perform(post("/api/v1/me/pantry/{publicId}/adjust", PANTRY_ITEM_PUBLIC_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer native-access-token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":-1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
