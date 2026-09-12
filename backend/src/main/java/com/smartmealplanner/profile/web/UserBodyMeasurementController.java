package com.smartmealplanner.profile.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.profile.application.UserBodyMeasurementService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/measurements")
public class UserBodyMeasurementController {

    private final UserBodyMeasurementService measurementService;

    public UserBodyMeasurementController(
            UserBodyMeasurementService measurementService) {

        this.measurementService =
                measurementService;
    }

    @GetMapping("/latest")
    public MeasurementResponse getLatest(
            @AuthenticationPrincipal
            Jwt jwt) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return measurementService.getLatestMeasurement(
                publicId);
    }

    @GetMapping
    public List<MeasurementResponse> getHistory(
            @AuthenticationPrincipal
            Jwt jwt,
            @RequestParam(
                    defaultValue = "0")
            int page,
            @RequestParam(
                    defaultValue = "20")
            int size,
            @RequestParam(
                    required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(
                    required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return measurementService.getMeasurementHistory(
                publicId,
                page,
                size,
                from,
                to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse record(
            @AuthenticationPrincipal
            Jwt jwt,
            @RequestBody
            RecordMeasurementRequest request) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return measurementService.recordMeasurement(
                publicId,
                request);
    }

    @PutMapping("/{measuredOn}")
    public MeasurementResponse update(
            @AuthenticationPrincipal
            Jwt jwt,
            @PathVariable
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate measuredOn,
            @RequestBody
            UpdateMeasurementRequest request) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return measurementService.correctMeasurement(
                publicId,
                measuredOn,
                request);
    }
}
