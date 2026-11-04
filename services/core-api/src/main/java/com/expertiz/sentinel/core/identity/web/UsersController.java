package com.expertiz.sentinel.core.identity.web;

import com.expertiz.sentinel.core.identity.internal.admin.PatchUserCommand;
import com.expertiz.sentinel.core.identity.internal.admin.UserAdminService;
import com.expertiz.sentinel.core.identity.internal.admin.UserAdminService.CreatedUser;
import com.expertiz.sentinel.core.identity.internal.admin.UserAdminService.IssuedTemporaryPassword;
import com.expertiz.sentinel.core.identity.internal.admin.UserQueryService;
import com.expertiz.sentinel.core.identity.internal.model.RoleCode;
import com.expertiz.sentinel.core.shared.security.Permissions;
import com.expertiz.sentinel.core.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administration des utilisateurs (spec §10.1) : lecture avec {@code user:read}, écritures avec {@code user:manage}. */
@RestController
@RequestMapping("/api/v1/users")
class UsersController {

    private static final String READ = "hasAuthority('" + Permissions.USER_READ + "')";
    private static final String MANAGE = "hasAuthority('" + Permissions.USER_MANAGE + "')";

    private final UserQueryService queries;
    private final UserAdminService admin;

    UsersController(UserQueryService queries, UserAdminService admin) {
        this.queries = queries;
        this.admin = admin;
    }

    @GetMapping
    @PreAuthorize(READ)
    PageResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(queries.list(q, role, status, page, size, sort).map(UserResponse::of));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    UserResponse get(@PathVariable UUID id) {
        return UserResponse.of(queries.get(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(MANAGE)
    ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        CreatedUser created = admin.create(request.email(), request.fullName(), RoleCode.valueOf(request.role()));
        UserResponse user = UserResponse.of(created.user());
        IssuedTemporaryPassword temporary = created.temporaryPassword();
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id()))
                .body(new CreatedUserResponse(user, temporary.value(), temporary.expiresAt()));
    }

    /** Champs absents inchangés, {@code null} refusé, {@code version} obligatoire (spec §10.2). */
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(MANAGE)
    @Operation(requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PatchUserRequest.class))))
    UserResponse patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return UserResponse.of(admin.patch(UUID.fromString(jwt.getSubject()), id, PatchUserCommand.parse(body)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        admin.delete(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize(MANAGE)
    TemporaryPasswordResponse resetPassword(@PathVariable UUID id) {
        IssuedTemporaryPassword temporary = admin.resetPassword(id);
        return new TemporaryPasswordResponse(temporary.value(), temporary.expiresAt());
    }
}
