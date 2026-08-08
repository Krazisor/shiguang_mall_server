package org.dhu.shiguang_market.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.RoleView;

/** 店铺成员管理接口 DTO，字段与 phase-2-api 契约保持一致。 */
public final class ShopMemberDtos {
    private ShopMemberDtos() {
    }

    public record AddShopMemberRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank String roleId) {
    }

    public record ChangeShopMemberRoleRequest(@NotBlank String roleId) {
    }

    public record StatusRequest(@NotNull ActiveStatus targetStatus) {
    }

    public record ShopMemberView(
            String shopId, UserSummary user, RoleView role, ActiveStatus status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
