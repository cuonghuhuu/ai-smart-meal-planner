package com.smartmealplanner.nutrition.web;

import java.util.UUID;

import com.smartmealplanner.nutrition.application.NutritionTargetApplicationService;
import com.smartmealplanner.nutrition.application.NutritionTargetQueryService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter for authenticated Nutrition target workflows. */
@RestController
@RequestMapping("/api/v1/me/nutrition-targets")
public class NutritionTargetController {

    private final NutritionTargetApplicationService targetApplicationService;
    private final NutritionTargetQueryService targetQueryService;

    public NutritionTargetController(
            NutritionTargetApplicationService targetApplicationService,
            NutritionTargetQueryService targetQueryService) {

        this.targetApplicationService = targetApplicationService;
        this.targetQueryService = targetQueryService;
    }

    @PostMapping("/calculate")
    public NutritionCalculationResponse previewCalculation(
            @AuthenticationPrincipal
            Jwt jwt,
            @Valid
            @RequestBody
            NutritionEffectiveDateRequest request) {

        UUID publicId = SecurityPrincipals.authenticatedPublicId(jwt);

        return NutritionWebMapper.toResponse(
                targetApplicationService.previewCalculation(
                        publicId,
                        request.effectiveFrom()));
    }

    @PostMapping("/calculated")
    @ResponseStatus(HttpStatus.CREATED)
    public NutritionTargetWriteResponse createCalculatedTarget(
            @AuthenticationPrincipal
            Jwt jwt,
            @Valid
            @RequestBody
            NutritionEffectiveDateRequest request) {

        UUID publicId = SecurityPrincipals.authenticatedPublicId(jwt);

        return NutritionWebMapper.toResponse(
                targetApplicationService.createCalculatedTarget(
                        publicId,
                        request.effectiveFrom()));
    }

    @PostMapping("/user-defined")
    @ResponseStatus(HttpStatus.CREATED)
    public NutritionTargetWriteResponse createUserDefinedTarget(
            @AuthenticationPrincipal
            Jwt jwt,
            @Valid
            @RequestBody
            UserDefinedNutritionTargetRequest request) {

        UUID publicId = SecurityPrincipals.authenticatedPublicId(jwt);

        return NutritionWebMapper.toResponse(
                targetApplicationService.createUserDefinedTarget(
                        publicId,
                        NutritionWebMapper.toCommand(request)));
    }

    @GetMapping("/current")
    public NutritionTargetResponse getCurrentTarget(
            @AuthenticationPrincipal
            Jwt jwt) {

        UUID publicId = SecurityPrincipals.authenticatedPublicId(jwt);

        return NutritionWebMapper.toResponse(
                targetQueryService.getCurrentTarget(publicId));
    }

    @GetMapping
    public NutritionTargetHistoryResponse getTargetHistory(
            @AuthenticationPrincipal
            Jwt jwt,
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "20")
            int size) {

        UUID publicId = SecurityPrincipals.authenticatedPublicId(jwt);

        return NutritionWebMapper.toResponse(
                targetQueryService.getTargetHistory(publicId, page, size));
    }
}
