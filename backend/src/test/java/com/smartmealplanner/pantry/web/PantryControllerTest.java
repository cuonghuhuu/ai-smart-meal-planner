package com.smartmealplanner.pantry.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
                        .with(jwt().jwt(jwt -> jwt.subject(
                                USER_PUBLIC_ID.toString())))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":-1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
