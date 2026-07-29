package org.dhu.shiguang_market.product.dto;

import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.model.MarketEnums.AttributeValueType;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;

public final class ProductDtos {
    private ProductDtos() {
    }

    public record CategoryNode(
            String id, String parentId, String categoryCode, String categoryName,
            int sortOrder, boolean leaf, List<CategoryNode> children) {
    }

    public record CategoryAttributeView(
            String id, String categoryId, String attributeName, AttributeValueType valueType,
            String unit, boolean required, boolean filterable, List<String> options,
            int sortOrder, EnabledStatus status) {
    }

    public record BrandView(
            String id, String brandCode, String brandName, String logoUrl, EnabledStatus status) {
    }

    public record PublicShopView(ShopSummary shop, String description, String contactName, String contactPhone) {
    }

    public record CategoryBrief(String id, String categoryCode, String categoryName) {
    }

    public record ProductCardView(
            String id, String spuNo, String productName, String subtitle, String coverUrl,
            ShopSummary shop, String categoryId, BrandView brand,
            String minimumSalePrice, String maximumSalePrice, boolean inStock) {
    }

    public record ProductAttributeDisplayView(
            String attributeId, String attributeName, String value, String unit) {
    }

    public record PublicSkuView(
            String id, String skuNo, String skuName, Map<String, String> spec,
            String salePrice, String marketPrice, String imageUrl, EnabledStatus status,
            int availableQuantity, boolean inStock, boolean purchasable, String unavailableReason) {
    }

    public record ProductDetailView(
            String id, String spuNo, String productName, String subtitle, String coverUrl,
            List<String> galleryUrls, String detailHtml, String packingList, String serviceNote,
            ShopSummary shop, CategoryBrief category, BrandView brand,
            List<ProductAttributeDisplayView> attributes, List<PublicSkuView> skus) {
    }
}
