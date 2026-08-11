package org.dhu.shiguang_market.phasefive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.exception.GlobalExceptionHandler;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.task.controller.PlatformOperationController;
import org.dhu.shiguang_market.task.service.PlatformOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 阶段五平台运营只读接口测试。 */
class PlatformOperationControllerTests {
    private final PlatformOperationService service = mock(PlatformOperationService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PlatformOperationController(service, currentUser))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** 所有接口都必须先校验平台运营读取权限，再委托给只读查询服务。 */
    @Test
    void allOperationRoutesRequirePermissionAndDelegate() throws Exception {
        perform("/api/platform/operations/trades");
        perform("/api/platform/operations/orders");
        perform("/api/platform/operations/orders/711");
        perform("/api/platform/operations/payments");
        perform("/api/platform/operations/after-sales");
        perform("/api/platform/operations/business/TRADE/T202608070001");

        verify(currentUser, times(6)).requirePermission("platform:operation:read");
        verify(service).trades(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1L), eq(20L));
        verify(service).orders(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1L), eq(20L));
        verify(service).orderDetail(711L);
        verify(service).payments(isNull(), isNull(), isNull(), eq(1L), eq(20L));
        verify(service).afterSales(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1L), eq(20L));
        verify(service).trace("TRADE", "T202608070001");
    }

    /** 查询参数能够按项目约定的枚举、时间和分页类型完成绑定。 */
    @Test
    void tradeFiltersAreBoundAndPassedToService() throws Exception {
        mvc.perform(get("/api/platform/operations/trades")
                        .param("tradeNo", "T001")
                        .param("userId", "8")
                        .param("status", "PAID")
                        .param("createdFrom", "2026-08-01T00:00:00")
                        .param("createdTo", "2026-08-08T00:00:00")
                        .param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(service).trades(eq("T001"), eq(8L), any(),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(2L), eq(10L));
    }

    private void perform(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }
}
