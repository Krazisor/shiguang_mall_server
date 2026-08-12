package org.dhu.shiguang_market.coupon.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.TaskRunView;
import org.dhu.shiguang_market.coupon.service.CouponTaskService;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponTaskRunRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/tasks")
public class CouponTaskController {
    private final CouponTaskService tasks;
    private final CurrentUserService user;
    private final boolean enabled;

    public CouponTaskController(CouponTaskService tasks, CurrentUserService user, @Value("${market.internal-task-api-enabled:false}") boolean enabled) {
        this.tasks = tasks;
        this.user = user;
        this.enabled = enabled;
    }

    @PostMapping("/start-coupon-activities")
    public ApiResponse<TaskRunView> start(@Valid @RequestBody CouponTaskRunRequest r) {
        auth();
        return ApiResponse.success(tasks.start(r.batchSize(), r.dryRun()));
    }

    @PostMapping("/end-coupon-activities")
    public ApiResponse<TaskRunView> end(@Valid @RequestBody CouponTaskRunRequest r) {
        auth();
        return ApiResponse.success(tasks.end(r.batchSize(), r.dryRun()));
    }

    @PostMapping("/grant-system-coupons")
    public ApiResponse<TaskRunView> grant(@Valid @RequestBody CouponTaskRunRequest r) {
        auth();
        return ApiResponse.success(tasks.grantSystem(r.batchSize(), r.dryRun()));
    }

    @PostMapping("/expire-user-coupons")
    public ApiResponse<TaskRunView> expire(@Valid @RequestBody CouponTaskRunRequest r) {
        auth();
        return ApiResponse.success(tasks.expire(r.batchSize(), r.dryRun()));
    }

    @PostMapping("/recover-coupon-reservations")
    public ApiResponse<TaskRunView> recover(@Valid @RequestBody CouponTaskRunRequest r) {
        auth();
        return ApiResponse.success(tasks.recover(r.batchSize(), r.dryRun()));
    }

    @PostMapping("/reconcile-coupons")
    public ApiResponse<TaskRunView> reconcile(@Valid @RequestBody CouponTaskRunRequest r) {
        auth();
        return ApiResponse.success(tasks.reconcile(r.batchSize()));
    }

    private void auth() {
        if (!enabled) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "请求的资源不存在");
        user.requirePermission("platform:task:execute");
    }
}
