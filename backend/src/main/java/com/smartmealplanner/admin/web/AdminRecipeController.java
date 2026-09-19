package com.smartmealplanner.admin.web;

import java.util.UUID;

import com.smartmealplanner.recipe.RecipeAdminService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

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
@RequestMapping("/api/v1/admin/recipes")
public class AdminRecipeController {

    private final RecipeAdminService recipes;

    public AdminRecipeController(RecipeAdminService recipes) {
        this.recipes = recipes;
    }

    @GetMapping
    public AdminRecipeResponse.Page list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return AdminRecipeResponse.page(recipes.list(q, status, page, size));
    }

    @GetMapping("/{publicId}")
    public AdminRecipeResponse.Detail get(@PathVariable UUID publicId) {
        return AdminRecipeResponse.detail(recipes.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRecipeResponse.Detail create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AdminRecipeRequest request) {

        return AdminRecipeResponse.detail(recipes.create(
                SecurityPrincipals.authenticatedPublicId(jwt),
                request == null ? null : request.toCommand()));
    }

    @PutMapping("/{publicId}")
    public AdminRecipeResponse.Detail update(
            @PathVariable UUID publicId,
            @RequestBody AdminRecipeRequest request) {

        return AdminRecipeResponse.detail(recipes.update(
                publicId,
                request == null ? null : request.toCommand()));
    }

    @PostMapping("/{publicId}/publish")
    public AdminRecipeResponse.Detail publish(@PathVariable UUID publicId) {
        return AdminRecipeResponse.detail(recipes.publish(publicId));
    }

    @PostMapping("/{publicId}/archive")
    public AdminRecipeResponse.Detail archive(@PathVariable UUID publicId) {
        return AdminRecipeResponse.detail(recipes.archive(publicId));
    }
}
