package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.GlobalExceptionHandler;
import org.dhu.shiguang_market.coupon.controller.CouponCenterController;
import org.dhu.shiguang_market.coupon.controller.CouponTaskController;
import org.dhu.shiguang_market.coupon.controller.PlatformCouponController;
import org.dhu.shiguang_market.coupon.controller.ShopCouponController;
import org.dhu.shiguang_market.coupon.controller.UserCouponController;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponTaskRunRequest;
import org.dhu.shiguang_market.coupon.service.CouponAdminService;
import org.dhu.shiguang_market.coupon.service.CouponOperationService;
import org.dhu.shiguang_market.coupon.service.CouponService;
import org.dhu.shiguang_market.coupon.service.CouponTaskService;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class CouponEndpointContractTests {
    private MockMvc mockMvc;
    private CouponService coupons;
    private CouponTaskService tasks;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        CouponAdminService admin = mock(CouponAdminService.class);
        coupons = mock(CouponService.class);
        CouponOperationService operations = mock(CouponOperationService.class);
        tasks = mock(CouponTaskService.class);
        CurrentUserService user = mock(CurrentUserService.class);
        when(tasks.start(any(Integer.class), any(Boolean.class))).thenReturn(null);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CouponCenterController(coupons),
                        new UserCouponController(coupons),
                        new ShopCouponController(admin, coupons),
                        new PlatformCouponController(admin, coupons, operations),
                        new CouponTaskController(tasks, user, true))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @org.junit.jupiter.api.Test
    void exposesDocumentedCouponRoutes() {
        Set<String> actual = List.of(CouponCenterController.class, UserCouponController.class,
                        ShopCouponController.class, PlatformCouponController.class, CouponTaskController.class)
                .stream().flatMap(type -> endpoints(type).stream()).collect(Collectors.toSet());
        assertThat(actual).contains(
                "GET /api/coupon-center/activities",
                "GET /api/coupon-center/activities/{activityId}",
                "POST /api/coupon-center/activities/{activityId}/templates/{templateId}/claim",
                "GET /api/coupons", "GET /api/coupons/{userCouponId}",
                "GET /api/coupons/{userCouponId}/eligible-products", "POST /api/coupons/redeem",
                "GET /api/shops/{shopId}/coupon-activities",
                "POST /api/shops/{shopId}/coupon-activities",
                "GET /api/shops/{shopId}/coupon-activities/{activityId}",
                "PUT /api/shops/{shopId}/coupon-activities/{activityId}",
                "POST /api/shops/{shopId}/coupon-activities/{activityId}/{action:publish|resume}",
                "POST /api/shops/{shopId}/coupon-activities/{activityId}/{action:pause|end|cancel}",
                "GET /api/shops/{shopId}/coupon-templates",
                "POST /api/shops/{shopId}/coupon-templates",
                "GET /api/shops/{shopId}/coupon-templates/{templateId}",
                "PUT /api/shops/{shopId}/coupon-templates/{templateId}",
                "PUT /api/shops/{shopId}/coupon-templates/{templateId}/scope",
                "PATCH /api/shops/{shopId}/coupon-templates/{templateId}/presentation",
                "POST /api/shops/{shopId}/coupon-templates/{templateId}/{action:activate|resume}",
                "POST /api/shops/{shopId}/coupon-templates/{templateId}/{action:pause|end}",
                "POST /api/shops/{shopId}/coupon-templates/{templateId}/copy",
                "GET /api/shops/{shopId}/coupon-templates/{templateId}/scope-targets",
                "POST /api/shops/{shopId}/coupon-templates/{templateId}/grants",
                "POST /api/shops/{shopId}/coupon-templates/{templateId}/redeem-code-batches",
                "GET /api/shops/{shopId}/coupon-code-batches",
                "GET /api/shops/{shopId}/coupon-funding-invitations",
                "POST /api/shops/{shopId}/coupon-funding-invitations/{participationId}/decide",
                "GET /api/platform/coupon-activities", "POST /api/platform/coupon-activities",
                "GET /api/platform/coupon-activities/{activityId}",
                "PUT /api/platform/coupon-activities/{activityId}",
                "POST /api/platform/coupon-activities/{activityId}/{action:publish|resume}",
                "POST /api/platform/coupon-activities/{activityId}/{action:pause|end|cancel}",
                "GET /api/platform/coupon-templates", "POST /api/platform/coupon-templates",
                "GET /api/platform/coupon-templates/{templateId}",
                "PUT /api/platform/coupon-templates/{templateId}",
                "PUT /api/platform/coupon-templates/{templateId}/scope",
                "PATCH /api/platform/coupon-templates/{templateId}/presentation",
                "POST /api/platform/coupon-templates/{templateId}/{action:activate|resume}",
                "POST /api/platform/coupon-templates/{templateId}/{action:pause|end}",
                "POST /api/platform/coupon-templates/{templateId}/copy",
                "GET /api/platform/coupon-templates/{templateId}/scope-targets",
                "POST /api/platform/coupon-templates/{templateId}/funding-invitations",
                "POST /api/platform/coupon-templates/{templateId}/grants",
                "POST /api/platform/coupon-templates/{templateId}/redeem-code-batches",
                "GET /api/platform/coupon-code-batches",
                "POST /api/platform/coupon-governance/activities/{activityId}/pause",
                "POST /api/platform/coupon-governance/activities/{activityId}/resume",
                "POST /api/platform/coupon-governance/user-coupons/{userCouponId}/revoke",
                "GET /api/platform/coupon-operations/activities",
                "GET /api/platform/coupon-operations/templates",
                "GET /api/platform/coupon-operations/user-coupons",
                "GET /api/platform/coupon-operations/redemptions",
                "GET /api/platform/coupon-operations/trace",
                "POST /api/internal/tasks/start-coupon-activities",
                "POST /api/internal/tasks/end-coupon-activities",
                "POST /api/internal/tasks/grant-system-coupons",
                "POST /api/internal/tasks/expire-user-coupons",
                "POST /api/internal/tasks/recover-coupon-reservations",
                "POST /api/internal/tasks/reconcile-coupons");
    }

    @org.junit.jupiter.api.Test
    void requiresIdempotencyHeaderBeforeClaimBusinessExecution() throws Exception {
        mockMvc.perform(post("/api/coupon-center/activities/1/templates/2/claim"))
                .andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.api.Test
    void rejectsStateActionWhenVersionIsOmitted() throws Exception {
        mockMvc.perform(post("/api/platform/coupon-activities/1/publish")
                        .header("Idempotency-Key", "coupon-publish-1")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static List<String> endpoints(Class<?> controller) {
        String prefix = controller.getAnnotation(RequestMapping.class).value()[0];
        List<String> result = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            add(result, "GET", prefix, method.getAnnotation(GetMapping.class) == null ? null : method.getAnnotation(GetMapping.class).value());
            add(result, "POST", prefix, method.getAnnotation(PostMapping.class) == null ? null : method.getAnnotation(PostMapping.class).value());
            add(result, "PUT", prefix, method.getAnnotation(PutMapping.class) == null ? null : method.getAnnotation(PutMapping.class).value());
            add(result, "PATCH", prefix, method.getAnnotation(PatchMapping.class) == null ? null : method.getAnnotation(PatchMapping.class).value());
            add(result, "DELETE", prefix, method.getAnnotation(DeleteMapping.class) == null ? null : method.getAnnotation(DeleteMapping.class).value());
        }
        return result;
    }

    private static void add(List<String> target, String method, String prefix, String[] paths) {
        if (paths == null) return;
        if (paths.length == 0) target.add(method + " " + prefix);
        else for (String path : paths) target.add(method + " " + prefix + path);
    }
}
