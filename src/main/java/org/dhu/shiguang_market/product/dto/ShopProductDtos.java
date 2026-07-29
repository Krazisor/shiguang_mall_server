package org.dhu.shiguang_market.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.product.dto.ProductDtos.BrandView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryBrief;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductDetailView;

public final class ShopProductDtos {
    private ShopProductDtos() {
    }

    public record ProductAttributeInput(@NotBlank String attributeId, @NotBlank @Size(max = 1000) String value) {
    }

    public record SkuCreateInput(
            @NotBlank @Size(max = 255) String skuName,
            @NotEmpty @Size(max = 10) Map<String, String> spec,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String salePrice,
            @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String marketPrice,
            @Size(max = 64) String barcode,
            @Size(max = 1024) String imageUrl) {
    }

    public record CreateProductRequest(
            @NotBlank String categoryId,
            String brandId,
            @NotBlank @Size(max = 255) String productName,
            @Size(max = 500) String subtitle,
            @Size(max = 1024) String coverUrl,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 1024) String> galleryUrls,
            String detailHtml,
            @Size(max = 65_535) String packingList,
            @Size(max = 65_535) String serviceNote,
            @NotNull @Valid List<ProductAttributeInput> attributes,
            @NotEmpty @Valid List<SkuCreateInput> skus) {
    }

    public record SkuContentInput(
            @NotBlank String skuId, @NotBlank @Size(max = 255) String skuName,
            @Size(max = 1024) String imageUrl, @Min(0) int version) {
    }

    public record UpdateProductContentRequest(
            @NotBlank String categoryId,
            String brandId,
            @NotBlank @Size(max = 255) String productName,
            @Size(max = 500) String subtitle,
            @Size(max = 1024) String coverUrl,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 1024) String> galleryUrls,
            String detailHtml,
            @Size(max = 65_535) String packingList,
            @Size(max = 65_535) String serviceNote,
            @NotNull @Valid List<ProductAttributeInput> attributes,
            @Min(0) int contentVersion,
            @NotNull @Valid List<SkuContentInput> skuContents) {
    }

    public record CreateSkuRequest(
            @NotBlank @Size(max = 255) String skuName,
            @NotEmpty @Size(max = 10) Map<String, String> spec,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String salePrice,
            @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String marketPrice,
            @Size(max = 64) String barcode,
            @Size(max = 1024) String imageUrl,
            @Min(0) int contentVersion) {
        public SkuCreateInput sku() {
            return new SkuCreateInput(skuName, spec, salePrice, marketPrice, barcode, imageUrl);
        }
    }

    public static final class UpdateSkuRequest {
        private String salePrice;
        private String marketPrice;
        private String barcode;
        private EnabledStatus status;
        private Integer version;
        private boolean salePricePresent;
        private boolean marketPricePresent;
        private boolean barcodePresent;
        private boolean statusPresent;

        @JsonSetter("salePrice") public void setSalePrice(String value) { salePrice = value; salePricePresent = true; }
        @JsonSetter("marketPrice") public void setMarketPrice(String value) { marketPrice = value; marketPricePresent = true; }
        @JsonSetter("barcode") public void setBarcode(String value) { barcode = value; barcodePresent = true; }
        @JsonSetter("status") public void setStatus(EnabledStatus value) { status = value; statusPresent = true; }
        @JsonSetter("version") public void setVersion(Integer value) { version = value; }

        public String salePrice() { return salePrice; }
        public String marketPrice() { return marketPrice; }
        public String barcode() { return barcode; }
        public EnabledStatus status() { return status; }
        public int version() {
            if (version == null || version < 0) throw new IllegalArgumentException("version is required");
            return version;
        }
        public boolean hasSalePrice() { return salePricePresent; }
        public boolean hasMarketPrice() { return marketPricePresent; }
        public boolean hasBarcode() { return barcodePresent; }
        public boolean hasStatus() { return statusPresent; }
    }

    public record ReasonRequest(@Size(min = 1, max = 500) String reason) {
    }

    public record StockView(
            String skuId, int availableQuantity, int lockedQuantity,
            int version, OffsetDateTime updatedAt) {
    }

    public record ShopSkuView(
            String id, String skuNo, String skuName, Map<String, String> spec,
            String salePrice, String marketPrice, String barcode, String imageUrl,
            EnabledStatus status, int version, StockView stock,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record OperatorBrief(String id, String username, String nickname) {
    }

    public record ProductStatusHistoryView(
            String id, String spuId, ProductStatus fromStatus, ProductStatus toStatus,
            ProductOperationType operationType, int contentVersion, OperatorType operatorType,
            OperatorBrief operator, String reason, OffsetDateTime createdAt) {
    }

    public record ShopProductSummaryView(
            String id, String spuNo, String productName, String coverUrl,
            CategoryBrief category, BrandView brand, ProductStatus status, int contentVersion,
            int skuCount, int enabledSkuCount, int availableQuantity, int lockedQuantity,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record ShopProductDetailView(
            String id, String spuNo, String productName, String subtitle, String coverUrl,
            List<String> galleryUrls, String detailHtml, String packingList, String serviceNote,
            org.dhu.shiguang_market.common.api.CommonViews.ShopSummary shop,
            CategoryBrief category, BrandView brand,
            List<ProductDtos.ProductAttributeDisplayView> attributes,
            ProductStatus status, int contentVersion,
            UserSummary createdBy, UserSummary updatedBy, List<ShopSkuView> skus,
            List<ProductStatusHistoryView> history, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record ProductReviewSummaryView(
            String spuId, String spuNo, String productName, String coverUrl,
            org.dhu.shiguang_market.common.api.CommonViews.ShopSummary shop,
            CategoryBrief category, int contentVersion, OffsetDateTime submittedAt) {
    }

    public record ProductReviewSkuView(
            String id, String skuNo, String skuName, Map<String, String> spec,
            String salePrice, String marketPrice, String barcode, String imageUrl,
            EnabledStatus status, int version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record ProductReviewDetailView(
            String id, String spuNo, String productName, String subtitle, String coverUrl,
            List<String> galleryUrls, String detailHtml, String packingList, String serviceNote,
            org.dhu.shiguang_market.common.api.CommonViews.ShopSummary shop,
            CategoryBrief category, BrandView brand,
            List<ProductDtos.ProductAttributeDisplayView> attributes,
            List<ProductReviewSkuView> skus, ProductStatus status, int contentVersion,
            UserSummary createdBy, UserSummary updatedBy, List<ProductStatusHistoryView> history,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record ReviewDecisionRequest(@Min(0) int contentVersion, @Size(max = 500) String reason) {
    }
}
