package org.dhu.shiguang_market.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;

/** 平台角色与权限接口 DTO，字段与 phase-2-api 契约保持一致。 */
public final class PlatformRbacDtos {
    private PlatformRbacDtos() {
    }

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$") String roleCode,
            @NotBlank @Size(max = 64) String roleName,
            @NotNull ScopeType scopeType,
            @Size(max = 255) String description,
            @NotNull @Size(max = 200) List<@NotBlank String> permissionIds) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 64) String roleName,
            @Size(max = 255) String description) {
    }

    public record StatusRequest(@NotNull ActiveStatus targetStatus) {
    }

    public record AssignPermissionsRequest(
            @NotNull @Size(max = 200) List<@NotBlank String> permissionIds) {
    }

    public record PermissionView(
            String id, String permissionCode, String permissionName,
            ScopeType scopeType, String resource, String httpMethod,
            ActiveStatus status) {
    }

    public record RoleDetailView(
            String id, String roleCode, String roleName, ScopeType scopeType,
            String description, ActiveStatus status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<PermissionView> permissions) {
    }
}
