package org.dhu.shiguang_market.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.cart.controller.CartController;
import org.dhu.shiguang_market.address.controller.AddressController;
import org.dhu.shiguang_market.identity.controller.AuthController;
import org.dhu.shiguang_market.inventory.controller.InventoryController;
import org.dhu.shiguang_market.order.controller.OrderController;
import org.dhu.shiguang_market.order.controller.TradeController;
import org.dhu.shiguang_market.payment.controller.PaymentController;
import org.dhu.shiguang_market.payment.controller.WalletController;
import org.dhu.shiguang_market.product.controller.PlatformCatalogController;
import org.dhu.shiguang_market.product.controller.ProductReviewController;
import org.dhu.shiguang_market.product.controller.PublicCatalogController;
import org.dhu.shiguang_market.product.controller.ShopProductController;
import org.dhu.shiguang_market.shop.controller.PlatformShopController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PhaseOneEndpointContractTests {
    private static final List<Class<?>> CONTROLLERS = List.of(
            AuthController.class, AddressController.class, PublicCatalogController.class,
            CartController.class, TradeController.class, WalletController.class,
            PaymentController.class, OrderController.class, ShopProductController.class,
            InventoryController.class, PlatformShopController.class,
            PlatformCatalogController.class, ProductReviewController.class);

    @Test
    void exposesEveryPhaseOneEndpoint() {
        Set<String> actual = CONTROLLERS.stream().flatMap(controller -> endpoints(controller).stream())
                .collect(Collectors.toSet());

        assertThat(actual).contains(
                "POST /api/auth/register", "POST /api/auth/login", "POST /api/auth/logout",
                "GET /api/auth/me", "PATCH /api/users/me",
                "GET /api/addresses", "POST /api/addresses", "PUT /api/addresses/{addressId}",
                "DELETE /api/addresses/{addressId}", "POST /api/addresses/{addressId}/default",
                "GET /api/categories/tree", "GET /api/categories/{categoryId}/attributes",
                "GET /api/brands", "GET /api/shops/{shopId}", "GET /api/products",
                "GET /api/products/{spuId}", "GET /api/cart", "POST /api/cart/items",
                "PATCH /api/cart/items/{cartItemId}", "DELETE /api/cart/items/{cartItemId}",
                "PUT /api/cart/selection", "POST /api/trades/preview", "POST /api/trades",
                "GET /api/trades/{tradeId}", "POST /api/trades/{tradeId}/cancel",
                "GET /api/wallet", "GET /api/wallet/transactions", "POST /api/wallet/recharges",
                "POST /api/trades/{tradeId}/payments", "POST /api/payments/{paymentId}/confirm",
                "GET /api/payments/{paymentId}", "GET /api/orders", "GET /api/orders/{orderId}",
                "POST /api/orders/{orderId}/complete", "GET /api/shops/{shopId}/products",
                "POST /api/shops/{shopId}/products", "GET /api/shops/{shopId}/products/{spuId}",
                "PUT /api/shops/{shopId}/products/{spuId}/content",
                "POST /api/shops/{shopId}/products/{spuId}/skus",
                "PATCH /api/shops/{shopId}/products/{spuId}/skus/{skuId}",
                "POST /api/shops/{shopId}/products/{spuId}/submit-review",
                "POST /api/shops/{shopId}/products/{spuId}/put-on-shelf",
                "POST /api/shops/{shopId}/products/{spuId}/take-off-shelf",
                "GET /api/shops/{shopId}/inventory", "GET /api/shops/{shopId}/inventory/{skuId}",
                "POST /api/shops/{shopId}/inventory/{skuId}/inbounds",
                "GET /api/shops/{shopId}/orders", "GET /api/shops/{shopId}/orders/{orderId}",
                "POST /api/shops/{shopId}/orders/{orderId}/ship",
                "GET /api/platform/shops", "POST /api/platform/shops", "GET /api/platform/shops/{shopId}",
                "PUT /api/platform/shops/{shopId}", "POST /api/platform/shops/{shopId}/status",
                "GET /api/platform/shops/{shopId}/members",
                "POST /api/platform/shops/{shopId}/members",
                "PUT /api/platform/shops/{shopId}/members/{userId}/role",
                "POST /api/platform/shops/{shopId}/members/{userId}/status",
                "DELETE /api/platform/shops/{shopId}/members/{userId}",
                "GET /api/platform/catalog/categories/tree", "POST /api/platform/catalog/categories",
                "PUT /api/platform/catalog/categories/{categoryId}",
                "POST /api/platform/catalog/categories/{categoryId}/status",
                "GET /api/platform/catalog/categories/{categoryId}/attributes",
                "POST /api/platform/catalog/categories/{categoryId}/attributes",
                "PUT /api/platform/catalog/categories/{categoryId}/attributes/{attributeId}",
                "POST /api/platform/catalog/categories/{categoryId}/attributes/{attributeId}/status",
                "GET /api/platform/catalog/brands", "POST /api/platform/catalog/brands",
                "PUT /api/platform/catalog/brands/{brandId}",
                "POST /api/platform/catalog/brands/{brandId}/status",
                "GET /api/platform/products/reviews", "GET /api/platform/products/reviews/{spuId}",
                "POST /api/platform/products/reviews/{spuId}/approve",
                "POST /api/platform/products/reviews/{spuId}/reject");
    }

    private static List<String> endpoints(Class<?> controller) {
        String prefix = controller.getAnnotation(RequestMapping.class).value()[0];
        List<String> endpoints = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            add(endpoints, "GET", prefix, values(method.getAnnotation(GetMapping.class)));
            add(endpoints, "POST", prefix, values(method.getAnnotation(PostMapping.class)));
            add(endpoints, "PUT", prefix, values(method.getAnnotation(PutMapping.class)));
            add(endpoints, "PATCH", prefix, values(method.getAnnotation(PatchMapping.class)));
            add(endpoints, "DELETE", prefix, values(method.getAnnotation(DeleteMapping.class)));
        }
        return endpoints;
    }

    private static String[] values(Object annotation) {
        if (annotation == null) return null;
        if (annotation instanceof GetMapping value) return value.value();
        if (annotation instanceof PostMapping value) return value.value();
        if (annotation instanceof PutMapping value) return value.value();
        if (annotation instanceof PatchMapping value) return value.value();
        return ((DeleteMapping) annotation).value();
    }

    private static void add(List<String> endpoints, String method, String prefix, String[] paths) {
        if (paths == null) return;
        if (paths.length == 0) {
            endpoints.add(method + " " + prefix);
            return;
        }
        for (String path : paths) endpoints.add(method + " " + prefix + path);
    }
}
