package org.dhu.shiguang_market.shopmember;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dhu.shiguang_market.common.exception.GlobalExceptionHandler;
import org.dhu.shiguang_market.shop.controller.ShopMemberController;
import org.dhu.shiguang_market.shop.service.ShopMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 店铺成员 HTTP 接口契约测试。 */
class ShopMemberControllerTests {
    private final ShopMemberService service = mock(ShopMemberService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ShopMemberController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** 商家成员管理员可以通过成员资源查询可分配角色。 */
    @Test
    void assignableRoleRouteIsExposed() throws Exception {
        mvc.perform(get("/api/shops/41/members/roles")
                        .param("keyword", "订单")
                        .param("page", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(service).roles(41L, "订单", 2L, 10L);
    }
}
