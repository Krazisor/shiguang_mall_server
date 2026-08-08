package org.dhu.shiguang_market.merchantwallet.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.merchantwallet.service.MerchantWalletService;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunView;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWithdrawalStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/tasks")
public class MerchantWalletTaskController {
    private final MerchantWalletService service;
    private final CurrentUserService currentUser;
    private final boolean enabled;
    public MerchantWalletTaskController(MerchantWalletService service, CurrentUserService currentUser,
                                         @Value("${market.internal-task-api-enabled:false}") boolean enabled) {
        this.service = service; this.currentUser = currentUser; this.enabled = enabled;
    }

    @PostMapping("/release-settlements")
    public ApiResponse<TaskRunView> release(@Valid @RequestBody TaskRunRequest request) {
        authorize(); long started = System.currentTimeMillis(); int count = service.releaseSettlements(request.batchSize(), request.dryRun());
        return ApiResponse.success(new TaskRunView("release-settlements", request.dryRun(), count, request.dryRun() ? 0 : count, count, 0, 0,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(), org.dhu.shiguang_market.common.util.RequestContext.requestId()));
    }

    @PostMapping("/process-virtual-withdrawals")
    public ApiResponse<TaskRunView> process(@Valid @RequestBody TaskRunRequest request,
                                             @RequestParam(defaultValue = "SUCCESS") MerchantWithdrawalStatus outcome) {
        authorize(); int count = service.processWithdrawals(request.batchSize(), request.dryRun(), outcome);
        return ApiResponse.success(new TaskRunView("process-virtual-withdrawals", request.dryRun(), count, request.dryRun() ? 0 : count, count, 0, 0,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(), org.dhu.shiguang_market.common.util.RequestContext.requestId()));
    }

    private void authorize() { if (!enabled) throw org.dhu.shiguang_market.common.exception.BusinessException.notFound("RESOURCE_NOT_FOUND", "请求的资源不存在"); currentUser.requirePermission("platform:task:execute"); }
}
