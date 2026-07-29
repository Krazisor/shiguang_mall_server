package org.dhu.shiguang_market.shop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.model.MarketEnums.AttributeValueType;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;

public final class PlatformDtos {
    private PlatformDtos() {
    }

    public record CreateShopRequest(
            @NotBlank @Size(max = 128) String shopName,
            @Size(max = 1024) String logoUrl,
            @Size(max = 500) String description,
            @Size(max = 64) String contactName,
            @Size(max = 32) String contactPhone,
            @NotBlank String adminUsername) {
    }

    public record UpdateShopRequest(
            @NotBlank @Size(max = 128) String shopName,
            @Size(max = 1024) String logoUrl,
            @Size(max = 500) String description,
            @Size(max = 64) String contactName,
            @Size(max = 32) String contactPhone) {
    }

    public record ChangeShopStatusRequest(@NotNull ShopStatus targetStatus, @NotBlank @Size(max = 500) String reason) {
    }

    public record PlatformShopView(
            ShopSummary shop, String description, String contactName, String contactPhone,
            int membersCount, int activeMembersCount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record CategoryUpsertRequest(
            String parentId, @NotBlank @Size(max = 64) String categoryName,
            @NotBlank String categoryCode, int sortOrder) {
    }

    public record CategoryAttributeRequest(
            @NotBlank @Size(max = 64) String attributeName,
            @NotNull AttributeValueType valueType,
            @Size(max = 32) String unit,
            boolean required, boolean filterable,
            List<@NotBlank @Size(max = 64) String> options,
            int sortOrder) {
    }

    public record BrandRequest(
            @NotBlank @Size(max = 128) String brandName,
            @NotBlank String brandCode,
            @Size(max = 1024) String logoUrl) {
    }

    public record StatusRequest(@NotNull EnabledStatus targetStatus, @Size(max = 500) String reason) {
    }

    public record PlatformCategoryView(
            String id, String parentId, String categoryCode, String categoryName,
            int sortOrder, EnabledStatus status, boolean leaf, int childrenCount,
            int attributeCount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record PlatformCategoryNode(
            String id, String parentId, String categoryCode, String categoryName,
            int sortOrder, EnabledStatus status, boolean leaf, int childrenCount,
            int attributeCount, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<PlatformCategoryNode> children) {
    }
}
