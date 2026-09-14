package com.smartmealplanner.nutrition.web;

import java.time.Instant;

import com.smartmealplanner.nutrition.application.NutritionTargetApplicationService;
import com.smartmealplanner.nutrition.application.NutritionTargetQueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NutritionTargetControllerTest {

    @Mock
    private NutritionTargetApplicationService targetApplicationService;

    @Mock
    private NutritionTargetQueryService targetQueryService;

    private NutritionTargetController controller;

    @BeforeEach
    void setUp() {
        controller = new NutritionTargetController(
                targetApplicationService,
                targetQueryService);
    }

    @Test
    void invalidJwtSubjectDoesNotReachNutritionApplicationServices() {

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("not-a-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThatThrownBy(() -> controller.getTargetHistory(jwt, 0, 20))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(
                targetApplicationService,
                targetQueryService);
    }
}
