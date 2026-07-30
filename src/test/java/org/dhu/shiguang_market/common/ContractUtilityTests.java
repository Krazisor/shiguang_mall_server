package org.dhu.shiguang_market.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.dhu.shiguang_market.cart.dto.CartDtos.UpdateCartItemRequest;
import org.dhu.shiguang_market.common.api.ApiErrorResponse;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.security.PasswordService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.common.util.RequestContext;
import org.dhu.shiguang_market.common.util.SpecNormalizer;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.UpdateProfileRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.UpdateSkuRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ContractUtilityTests {

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }

    @Test
    void formatsIdsMoneyAndShanghaiTimestampsForTheApiContract() {
        assertThat(Formatters.id(9_007_199_254_740_993L)).isEqualTo("9007199254740993");
        assertThat(Formatters.money(new BigDecimal("99"))).isEqualTo("99.00");
        assertThat(Formatters.time(LocalDateTime.of(2026, 7, 27, 16, 0)))
                .hasToString("2026-07-27T16:00+08:00");
    }

    @Test
    void canonicalizesSpecsIndependentlyOfInputOrder() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("storage", "256GB");
        first.put("color", "黑色");
        Map<String, String> second = Map.of("color", "黑色", "storage", "256GB");

        Map<String, String> normalizedFirst = SpecNormalizer.normalize(first);
        Map<String, String> normalizedSecond = SpecNormalizer.normalize(second);

        assertThat(normalizedFirst.keySet()).containsExactly("color", "storage");
        assertThat(SpecNormalizer.key(normalizedFirst)).isEqualTo(SpecNormalizer.key(normalizedSecond));
        assertThat(SpecNormalizer.key(normalizedFirst)).hasSize(64);
    }

    @Test
    void rejectsInvalidSpecs() {
        assertThatThrownBy(() -> SpecNormalizer.normalize(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpecNormalizer.normalize(Map.of(" ", "value")))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, String> tooMany = IntStream.rangeClosed(1, 11).boxed()
                .collect(java.util.stream.Collectors.toMap(value -> "key" + value, value -> "value"));
        assertThatThrownBy(() -> SpecNormalizer.normalize(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 10");
    }

    @Test
    void hashesPasswordsWithoutSpringSecurity() {
        PasswordService service = new PasswordService();
        String digest = service.hash("Market123");

        assertThat(digest).startsWith("$2");
        assertThat(service.matches("Market123", digest)).isTrue();
        assertThat(service.matches("wrong-password", digest)).isFalse();
    }

    @Test
    void successResponsesContainDataEvenWhenNullAndErrorsNeverContainData() throws Exception {
        RequestContext.setRequestId("contract-test");
        ObjectMapper mapper = new ObjectMapper();

        String success = mapper.writeValueAsString(ApiResponse.success(null));
        String error = mapper.writeValueAsString(ApiErrorResponse.of("BAD_REQUEST", "bad", null));

        assertThat(success).contains("\"data\":null").contains("\"requestId\":\"contract-test\"");
        assertThat(error).doesNotContain("\"data\"").contains("\"code\":\"BAD_REQUEST\"");
    }

    @Test
    void patchDtosDistinguishMissingFieldsFromExplicitNulls() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        UpdateProfileRequest profile = mapper.readValue("{\"phone\":null}", UpdateProfileRequest.class);
        UpdateSkuRequest sku = mapper.readValue("{\"marketPrice\":null,\"version\":2}", UpdateSkuRequest.class);
        UpdateCartItemRequest cart = mapper.readValue("{\"selected\":null}", UpdateCartItemRequest.class);

        assertThat(profile.hasPhone()).isTrue();
        assertThat(profile.phone()).isNull();
        assertThat(profile.hasEmail()).isFalse();
        assertThat(sku.hasMarketPrice()).isTrue();
        assertThat(sku.marketPrice()).isNull();
        assertThat(sku.version()).isEqualTo(2);
        assertThat(cart.hasQuantity()).isFalse();
        assertThat(cart.hasSelected()).isTrue();
        assertThat(cart.selected()).isNull();
    }

    @Test
    void validatesImageUrlsAndSanitizesProductHtml() {
        ContentSafety developmentSafety = new ContentSafety(true);
        ContentSafety productionSafety = new ContentSafety(false);

        assertThat(developmentSafety.imageUrl("imageUrl", " https://static.example.com/a.png "))
                .isEqualTo("https://static.example.com/a.png");
        assertThat(developmentSafety.imageUrl("imageUrl", "http://localhost:8080/a.png"))
                .isEqualTo("http://localhost:8080/a.png");
        assertThatThrownBy(() -> developmentSafety.imageUrl("imageUrl", "http://example.com/a.png"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("VALIDATION_FAILED"));
        assertThatThrownBy(() -> productionSafety.imageUrl("imageUrl", "http://localhost/a.png"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> developmentSafety.imageUrls("galleryUrls",
                List.of("https://example.com/a.png", " https://example.com/a.png "), 10))
                .isInstanceOf(BusinessException.class);

        String cleaned = developmentSafety.detailHtml("""
                <p style="color:red" onclick="alert(1)">safe <strong>text</strong></p>
                <script>alert(1)</script><iframe src="https://example.com"></iframe>
                <img src="javascript:alert(1)" onerror="alert(2)">
                <img src="http://example.com/image.png">
                <img src="http://localhost:8080/image.png">
                """);

        assertThat(cleaned).contains("<p>safe <strong>text</strong></p>")
                .contains("http://localhost:8080/image.png")
                .doesNotContain("script", "iframe", "onclick", "onerror", "style=", "javascript:",
                        "http://example.com/image.png");
    }
}
