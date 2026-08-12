package org.dhu.shiguang_market.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.dhu.shiguang_market.common.api.CommonViews.AddressView;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CheckoutCouponSelection;

public final class CartDtos {
    private CartDtos() {
    }

    public record AddCartItemRequest(@NotBlank String skuId, @Min(1) @Max(999) int quantity) {
    }

    public static final class UpdateCartItemRequest {
        private Integer quantity;
        private Boolean selected;
        private boolean quantityPresent;
        private boolean selectedPresent;

        @JsonSetter("quantity")
        public void setQuantity(Integer quantity) { this.quantity = quantity; this.quantityPresent = true; }

        @JsonSetter("selected")
        public void setSelected(Boolean selected) { this.selected = selected; this.selectedPresent = true; }

        public Integer quantity() { return quantity; }
        public Boolean selected() { return selected; }
        public boolean hasQuantity() { return quantityPresent; }
        public boolean hasSelected() { return selectedPresent; }
    }

    public record UpdateCartSelectionRequest(@NotNull List<String> cartItemIds, @NotNull Boolean selected) {
    }

    public record CheckoutPreviewRequest(List<String> cartItemIds, String addressId, Map<String, String> shopRemarks,
                                         CheckoutCouponSelection couponSelection) {
        public CheckoutPreviewRequest(List<String> cartItemIds, String addressId, Map<String, String> shopRemarks) {
            this(cartItemIds, addressId, shopRemarks, null);
        }
    }

    public record CreateTradeRequest(List<String> cartItemIds, @NotBlank String addressId,
                                     Map<String, String> shopRemarks, String couponQuoteToken) {
        public CreateTradeRequest(List<String> cartItemIds, String addressId, Map<String, String> shopRemarks) {
            this(cartItemIds, addressId, shopRemarks, null);
        }
    }

    public record CartItemView(
            String id, String skuId, String spuId, String productName, String skuName,
            Map<String, String> spec, String imageUrl, int quantity, boolean selected,
            String currentSalePrice, int availableQuantity, boolean valid,
            String invalidReason, OffsetDateTime updatedAt) {
    }

    public record CartShopGroupView(ShopSummary shop, List<CartItemView> items) {
    }

    public record CartView(
            List<CartShopGroupView> shops, int selectedItemCount,
            int selectedQuantity, String selectedAmount) {
    }

    public record CheckoutItemView(
            String cartItemId, String skuId, String productName, String skuName,
            String unitPrice, int quantity, String originalAmount, String freightAmount,
            String payableAmount, String couponDiscountAmount, boolean valid, String invalidReason) {
        public CheckoutItemView(String cartItemId, String skuId, String productName, String skuName,
                String unitPrice, int quantity, String originalAmount, String freightAmount,
                String payableAmount, boolean valid, String invalidReason) {
            this(cartItemId, skuId, productName, skuName, unitPrice, quantity, originalAmount,
                    freightAmount, payableAmount, "0.00", valid, invalidReason);
        }
    }

    public record CheckoutShopGroupView(
            ShopSummary shop, List<CheckoutItemView> items, String itemAmount,
            String freightAmount, String payableAmount, String couponDiscountAmount,
            String buyerRemark) {
        public CheckoutShopGroupView(ShopSummary shop, List<CheckoutItemView> items, String itemAmount,
                String freightAmount, String payableAmount, String buyerRemark) {
            this(shop, items, itemAmount, freightAmount, payableAmount, "0.00", buyerRemark);
        }
    }

    public record InvalidCheckoutItemView(String cartItemId, String skuId, String reason, String message) {
    }

    public record CheckoutPreviewView(
            AddressView address, List<CheckoutShopGroupView> shops, String itemAmount,
            String freightAmount, String payableAmount, String grossAmount,
            String couponDiscountAmount, org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponQuoteView couponQuote,
            boolean submittable, List<InvalidCheckoutItemView> invalidItems) {
        public CheckoutPreviewView(AddressView address, List<CheckoutShopGroupView> shops, String itemAmount,
                String freightAmount, String payableAmount, boolean submittable,
                List<InvalidCheckoutItemView> invalidItems) {
            this(address, shops, itemAmount, freightAmount, payableAmount, payableAmount, "0.00", null,
                    submittable, invalidItems);
        }
    }
}
