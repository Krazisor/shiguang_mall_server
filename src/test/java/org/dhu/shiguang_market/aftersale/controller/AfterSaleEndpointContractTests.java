package org.dhu.shiguang_market.aftersale.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleEligibilityView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleSummaryView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ApproveAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ConfirmReturnReceivedRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.CreateAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RejectAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RetryRefundRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ReturnShipmentRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ShopAfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ShopAfterSaleSummaryView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.UpdateReturnShipmentRequest;
import org.dhu.shiguang_market.aftersale.service.AfterSaleService;
import org.dhu.shiguang_market.aftersale.service.ShopAfterSaleService;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.GlobalExceptionHandler;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 阶段三售后接口契约测试。
 *
 * <p>测试只启动 Spring MVC 组件并模拟 Service，因此运行快、无须准备 MySQL 和 Redis。
 * Service 的状态机与真实基础设施测试仍由阶段二测试负责。</p>
 */
class AfterSaleEndpointContractTests {
    private AfterSaleService afterSaleService;
    private ShopAfterSaleService shopAfterSaleService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        afterSaleService = mock(AfterSaleService.class);
        shopAfterSaleService = mock(ShopAfterSaleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AfterSaleController(afterSaleService),
                        new ShopAfterSaleController(shopAfterSaleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** 验证阶段三计划中的 13 条 HTTP 路由均已暴露，防止路径或请求方法写错。 */
    @Test
    void exposesAllThirteenAfterSaleEndpoints() {
        Set<String> actual = List.of(AfterSaleController.class, ShopAfterSaleController.class)
                .stream().flatMap(controller -> endpoints(controller).stream()).collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrder(
                "GET /api/orders/{orderId}/items/{orderItemId}/after-sale-eligibility",
                "POST /api/after-sales",
                "GET /api/after-sales",
                "GET /api/after-sales/{afterSaleId}",
                "POST /api/after-sales/{afterSaleId}/cancel",
                "POST /api/after-sales/{afterSaleId}/return-shipment",
                "PUT /api/after-sales/{afterSaleId}/return-shipment",
                "GET /api/shops/{shopId}/after-sales",
                "GET /api/shops/{shopId}/after-sales/{afterSaleId}",
                "POST /api/shops/{shopId}/after-sales/{afterSaleId}/approve",
                "POST /api/shops/{shopId}/after-sales/{afterSaleId}/reject",
                "POST /api/shops/{shopId}/after-sales/{afterSaleId}/confirm-return-received",
                "POST /api/shops/{shopId}/after-sales/{afterSaleId}/refund/retry");
    }

    /** 验证买家端接口的参数绑定、201 状态及幂等键转发。 */
    @Test
    void bindsBuyerRequestsAndDelegatesToService() throws Exception {
        when(afterSaleService.list(any(), any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(new PageView<>(List.of(), 1, 20, 0, 0));

        mockMvc.perform(get("/api/orders/11/items/21/after-sale-eligibility"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"));
        mockMvc.perform(post("/api/after-sales")
                        .header("Idempotency-Key", "create-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"11","orderItemId":"21","requestType":"REFUND_ONLY",
                                 "quantity":1,"reasonCode":"NOT_WANTED","requestedAmount":"10.00"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("OK"));
        mockMvc.perform(get("/api/after-sales")
                        .param("status", "PENDING").param("requestType", "REFUND_ONLY")
                        .param("orderNo", "OR001").param("createdFrom", "2026-08-01T00:00:00")
                        .param("createdTo", "2026-08-02T00:00:00").param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1));
        mockMvc.perform(get("/api/after-sales/31")).andExpect(status().isOk());
        mockMvc.perform(post("/api/after-sales/31/cancel")).andExpect(status().isOk());
        mockMvc.perform(post("/api/after-sales/31/return-shipment")
                        .header("Idempotency-Key", "shipment-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"carrierCode":"SF","carrierName":"顺丰速运","trackingNo":"SF001"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/after-sales/31/return-shipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"carrierCode":"YT","carrierName":"圆通速递","trackingNo":"YT001","version":1}
                                """))
                .andExpect(status().isOk());

        verify(afterSaleService).eligibility(11L, 21L);
        verify(afterSaleService).create(any(CreateAfterSaleRequest.class), org.mockito.ArgumentMatchers.eq("create-key-001"));
        verify(afterSaleService).list(AfterSaleStatus.PENDING, AfterSaleType.REFUND_ONLY, "OR001",
                LocalDateTime.parse("2026-08-01T00:00:00"), LocalDateTime.parse("2026-08-02T00:00:00"), 2, 10);
        verify(afterSaleService).detail(31L);
        verify(afterSaleService).cancel(31L);
        verify(afterSaleService).submitReturnShipment(org.mockito.ArgumentMatchers.eq(31L),
                any(ReturnShipmentRequest.class), org.mockito.ArgumentMatchers.eq("shipment-key-001"));
        verify(afterSaleService).updateReturnShipment(org.mockito.ArgumentMatchers.eq(31L),
                any(UpdateReturnShipmentRequest.class));
    }

    /** 验证商家端六条接口均将店铺范围、请求体和幂等键正确交给 Service。 */
    @Test
    void bindsShopRequestsAndDelegatesToService() throws Exception {
        when(shopAfterSaleService.list(anyLong(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyLong())).thenReturn(new PageView<>(List.of(), 1, 20, 0, 0));

        mockMvc.perform(get("/api/shops/41/after-sales")
                        .param("status", "REFUNDING").param("refundStatus", "FAILED")
                        .param("requestType", "RETURN_REFUND").param("keyword", "AS001"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/shops/41/after-sales/31")).andExpect(status().isOk());
        mockMvc.perform(post("/api/shops/41/after-sales/31/approve")
                        .header("Idempotency-Key", "approve-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvedQuantity":1,"approvedAmount":"10.00","reviewComment":"同意","version":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shops/41/after-sales/31/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewComment\":\"凭证不足\",\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shops/41/after-sales/31/confirm-return-received")
                        .header("Idempotency-Key", "receive-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"商品已收到\",\"version\":2}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shops/41/after-sales/31/refund/retry")
                        .header("Idempotency-Key", "retry-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"人工重试\",\"version\":3}"))
                .andExpect(status().isOk());

        verify(shopAfterSaleService).list(41L, AfterSaleStatus.REFUNDING, RefundStatus.FAILED,
                AfterSaleType.RETURN_REFUND, "AS001", null, null, 1, 20);
        verify(shopAfterSaleService).detail(41L, 31L);
        verify(shopAfterSaleService).approve(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq(31L), any(ApproveAfterSaleRequest.class),
                org.mockito.ArgumentMatchers.eq("approve-key-001"));
        verify(shopAfterSaleService).reject(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq(31L), any(RejectAfterSaleRequest.class));
        verify(shopAfterSaleService).confirmReturnReceived(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq(31L), any(ConfirmReturnReceivedRequest.class),
                org.mockito.ArgumentMatchers.eq("receive-key-001"));
        verify(shopAfterSaleService).retryRefund(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq(31L), any(RetryRefundRequest.class),
                org.mockito.ArgumentMatchers.eq("retry-key-001"));
    }

    /** 必须幂等的写接口缺少请求头时，应在进入 Service 前返回 400。 */
    @Test
    void rejectsMissingIdempotencyKeyAndInvalidBody() throws Exception {
        mockMvc.perform(post("/api/after-sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"11","orderItemId":"21","requestType":"REFUND_ONLY",
                                 "quantity":1,"reasonCode":"NOT_WANTED","requestedAmount":"10.00"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/after-sales")
                        .header("Idempotency-Key", "create-key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"\",\"quantity\":0,\"requestedAmount\":\"10\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** 认证和权限异常必须保持项目统一的 401/403 JSON 错误契约。 */
    @Test
    void mapsAuthenticationAndPermissionFailures() throws Exception {
        when(afterSaleService.detail(99L)).thenThrow(
                new NotLoginException("未登录", NotLoginException.NOT_TOKEN, "login"));
        when(shopAfterSaleService.detail(41L, 99L)).thenThrow(
                new NotPermissionException("shop:after-sale:manage", "login"));

        mockMvc.perform(get("/api/after-sales/99"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_NOT_LOGGED_IN"));
        mockMvc.perform(get("/api/shops/41/after-sales/99"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_PERMISSION_DENIED"));
    }

    /** 请求与响应 record 的字段名必须和 phase-2-api.md 保持一致。 */
    @Test
    void keepsRequestAndResponseDtoFieldContracts() {
        assertThat(components(CreateAfterSaleRequest.class)).containsExactly(
                "orderId", "orderItemId", "requestType", "quantity", "reasonCode",
                "reasonDescription", "evidenceUrls", "requestedAmount");
        assertThat(components(ReturnShipmentRequest.class)).containsExactly("carrierCode", "carrierName", "trackingNo");
        assertThat(components(UpdateReturnShipmentRequest.class)).containsExactly(
                "carrierCode", "carrierName", "trackingNo", "version");
        assertThat(components(ApproveAfterSaleRequest.class)).containsExactly(
                "approvedQuantity", "approvedAmount", "reviewComment", "version");
        assertThat(components(RejectAfterSaleRequest.class)).containsExactly("reviewComment", "version");
        assertThat(components(ConfirmReturnReceivedRequest.class)).containsExactly("remark", "version");
        assertThat(components(RetryRefundRequest.class)).containsExactly("remark", "version");
        assertThat(components(AfterSaleEligibilityView.class)).contains("maximumRequestQuantity", "maximumRequestAmount");
        assertThat(components(AfterSaleSummaryView.class)).contains("afterSaleNo", "order", "shop", "item");
        assertThat(components(AfterSaleDetailView.class)).contains("review", "returnShipment", "availableActions");
        assertThat(components(ShopAfterSaleSummaryView.class)).contains("buyer");
        assertThat(components(ShopAfterSaleDetailView.class)).contains("buyer", "eligibilityAtReview");
    }

    private static List<String> components(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(component -> component.getName()).toList();
    }

    private static List<String> endpoints(Class<?> controller) {
        String prefix = controller.getAnnotation(RequestMapping.class).value()[0];
        List<String> endpoints = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            add(endpoints, "GET", prefix, values(method.getAnnotation(GetMapping.class)));
            add(endpoints, "POST", prefix, values(method.getAnnotation(PostMapping.class)));
            add(endpoints, "PUT", prefix, values(method.getAnnotation(PutMapping.class)));
        }
        return endpoints;
    }

    private static String[] values(Object annotation) {
        if (annotation == null) return null;
        if (annotation instanceof GetMapping value) return value.value();
        if (annotation instanceof PostMapping value) return value.value();
        return ((PutMapping) annotation).value();
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
