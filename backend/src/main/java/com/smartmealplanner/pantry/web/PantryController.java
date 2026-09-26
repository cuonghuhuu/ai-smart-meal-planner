package com.smartmealplanner.pantry.web;

import java.util.List;
import java.util.UUID;

import com.smartmealplanner.pantry.PantryService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin authenticated HTTP adapter for the owner-scoped Pantry workflow. */
@RestController
@RequestMapping("/api/v1/me/pantry")
public class PantryController {

    private final PantryService pantry;

    public PantryController(PantryService pantry) {
        this.pantry = pantry;
    }

    @GetMapping
    public List<PantryResponse.Item> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean includeClosed) {

        return pantry.list(
                SecurityPrincipals.authenticatedPublicId(jwt),
                includeClosed);
    }

    @GetMapping("/{publicId}")
    public PantryResponse.Item get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId) {

        return pantry.get(
                SecurityPrincipals.authenticatedPublicId(jwt),
                publicId);
    }

    @PostMapping
    public PantryResponse.Item create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreatePantryItemRequest request) {

        return pantry.create(
                SecurityPrincipals.authenticatedPublicId(jwt),
                request);
    }

    @PutMapping("/{publicId}")
    public PantryResponse.Item updateMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId,
            @RequestBody UpdatePantryItemRequest request) {

        return pantry.updateMetadata(
                SecurityPrincipals.authenticatedPublicId(jwt),
                publicId,
                request);
    }

    @PostMapping("/{publicId}/adjust")
    public PantryResponse.Item adjust(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId,
            @RequestBody AdjustPantryItemRequest request) {

        return pantry.adjust(
                SecurityPrincipals.authenticatedPublicId(jwt),
                publicId,
                request);
    }

    @PostMapping("/{publicId}/consume")
    public PantryResponse.Item consume(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId,
            @RequestBody ConsumePantryItemRequest request) {

        return pantry.consume(
                SecurityPrincipals.authenticatedPublicId(jwt),
                publicId,
                request);
    }

    @PostMapping("/{publicId}/discard")
    public PantryResponse.Item discard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId,
            @RequestBody(required = false) PantryNoteRequest request) {

        return pantry.discard(
                SecurityPrincipals.authenticatedPublicId(jwt),
                publicId,
                request == null ? null : request.note());
    }
}
