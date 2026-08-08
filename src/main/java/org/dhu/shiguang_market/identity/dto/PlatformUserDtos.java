package org.dhu.shiguang_market.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;

/** 平台用户管理接口 DTO，字段与 phase-2-api 契约保持一致。 */
public final class PlatformUserDtos {
    private PlatformUserDtos() {
    }

    public record ChangeUserStatusRequest(
            @NotNull UserStatus targetStatus,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record AssignPlatformRolesRequest(
            @NotNull @Size(max = 50) List<@NotBlank String> roleIds) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record RoleView(
            String id, String roleCode, String roleName, ScopeType scopeType,
            String description, ActiveStatus status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record PlatformUserView(
            String id, String username, String nickname, String phoneMasked,
            String emailMasked, UserStatus status, List<RoleView> platformRoles,
            OffsetDateTime lastLoginAt, OffsetDateTime createdAt) {
    }

    public record PlatformUserDetailView(
            String id, String username, String nickname, String phoneMasked,
            String emailMasked, UserStatus status, List<RoleView> platformRoles,
            OffsetDateTime lastLoginAt, OffsetDateTime createdAt,
            String avatarUrl, OffsetDateTime updatedAt) {
    }
}
