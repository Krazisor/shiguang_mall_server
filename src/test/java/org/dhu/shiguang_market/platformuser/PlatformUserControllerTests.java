package org.dhu.shiguang_market.platformuser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dhu.shiguang_market.common.exception.GlobalExceptionHandler;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.identity.controller.PlatformUserController;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.AssignPlatformRolesRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.ChangeUserStatusRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.ReasonRequest;
import org.dhu.shiguang_market.identity.service.PlatformUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 平台用户管理 HTTP 接口测试，可直接运行查看五个接口的路由和参数绑定结果。 */
class PlatformUserControllerTests {
    private final PlatformUserService service = mock(PlatformUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PlatformUserController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** 列表和详情接口应正确绑定筛选、分页及路径参数。 */
    @Test
    void queryRoutesDelegateToService() throws Exception {
        mvc.perform(get("/api/platform/rbac/users")
                        .param("keyword", "alice")
                        .param("status", "ACTIVE")
                        .param("roleCode", "PLATFORM_ADMIN")
                        .param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        mvc.perform(get("/api/platform/rbac/users/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(service).list("alice", UserStatus.ACTIVE, "PLATFORM_ADMIN", 2L, 10L);
        verify(service).detail(101L);
    }

    /** 状态、角色和强制下线接口应接收 JSON 请求并委托给服务层。 */
    @Test
    void managementRoutesDelegateToService() throws Exception {
        mvc.perform(post("/api/platform/rbac/users/101/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"DISABLED\",\"reason\":\"测试停用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        mvc.perform(put("/api/platform/rbac/users/101/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"1001\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        mvc.perform(post("/api/platform/rbac/users/101/kickout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"账号安全检查\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(service).changeStatus(eq(101L), any(ChangeUserStatusRequest.class));
        verify(service).assignRoles(eq(101L), any(AssignPlatformRolesRequest.class));
        verify(service).kickout(eq(101L), any(ReasonRequest.class));
    }

    /** 空下线原因不符合接口契约，应由统一异常处理返回参数校验错误。 */
    @Test
    void blankKickoutReasonReturnsValidationError() throws Exception {
        mvc.perform(post("/api/platform/rbac/users/101/kickout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(service, org.mockito.Mockito.never()).kickout(eq(101L), any());
    }
}
