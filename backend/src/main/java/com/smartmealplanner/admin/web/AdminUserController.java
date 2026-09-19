package com.smartmealplanner.admin.web;

import java.util.UUID;

import com.smartmealplanner.admin.AdminUserService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    public AdminUserResponse.Page list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return AdminUserResponse.page(users.list(q, status, page, size));
    }

    @GetMapping("/{publicId}")
    public AdminUserResponse.Item get(@PathVariable UUID publicId) {
        return AdminUserResponse.item(users.get(publicId));
    }

    @PatchMapping("/{publicId}/status")
    public AdminUserResponse.Item changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId,
            @RequestBody AdminUserResponse.StatusRequest request) {

        return AdminUserResponse.item(users.changeStatus(
                SecurityPrincipals.authenticatedPublicId(jwt),
                publicId,
                request == null ? null : request.status()));
    }
}
