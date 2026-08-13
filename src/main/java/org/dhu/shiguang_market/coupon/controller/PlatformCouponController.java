package org.dhu.shiguang_market.coupon.controller;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;

import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.*;
import org.dhu.shiguang_market.coupon.service.CouponAdminService;
import org.dhu.shiguang_market.coupon.service.CouponService;
import org.dhu.shiguang_market.coupon.service.CouponOperationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform")
public class PlatformCouponController {
    private final CouponAdminService admin;
    private final CouponService coupons;
    private final CouponOperationService operations;

    public PlatformCouponController(CouponAdminService admin, CouponService coupons, CouponOperationService operations) {
        this.admin = admin;
        this.coupons = coupons;
        this.operations = operations;
    }

    @GetMapping("/coupon-activities")
    public ApiResponse<PageView<CouponActivityAdminView>> activities(@RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus status, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityType activityType, @RequestParam(required = false) String keyword, @RequestParam(required = false) LocalDateTime createdFrom, @RequestParam(required = false) LocalDateTime createdTo, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ApiResponse.success(admin.activities(null, status, activityType, keyword, createdFrom, createdTo, page, pageSize, sort));
    }

    @PostMapping("/coupon-activities")
    public ResponseEntity<ApiResponse<CouponActivityAdminView>> createActivity(@Valid @RequestBody CreateCouponActivityRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(admin.createActivity(null, r, key)));
    }

    @PostMapping("/coupon-activities/recurring")
    public ResponseEntity<ApiResponse<CouponActivityAdminView>> createRecurringActivity(
            @Valid @RequestBody CreateRecurringCouponActivityRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(admin.createRecurringActivity(null, request, key)));
    }

    @GetMapping("/coupon-activities/{activityId}")
    public ApiResponse<CouponActivityAdminView> activity(@PathVariable long activityId) {
        return ApiResponse.success(admin.activity(null, activityId));
    }

    @GetMapping("/coupon-activities/{activityId}/schedule")
    public ApiResponse<CouponActivityScheduleView> activitySchedule(@PathVariable long activityId) {
        return ApiResponse.success(admin.activitySchedule(null, activityId));
    }

    @PutMapping("/coupon-activities/{activityId}/schedule")
    public ApiResponse<CouponActivityScheduleView> updateActivitySchedule(
            @PathVariable long activityId, @Valid @RequestBody UpdateCouponActivityScheduleRequest request) {
        return ApiResponse.success(admin.updateActivitySchedule(null, activityId, request));
    }

    @PutMapping("/coupon-activities/{activityId}")
    public ApiResponse<CouponActivityAdminView> updateActivity(@PathVariable long activityId, @Valid @RequestBody UpdateCouponActivityRequest r) {
        return ApiResponse.success(admin.updateActivity(null, activityId, r));
    }

    @PostMapping("/coupon-activities/{activityId}/{action:publish|resume}")
    public ApiResponse<CouponActivityAdminView> action(@PathVariable long activityId, @PathVariable String action, @Valid @RequestBody VersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.activityAction(null, activityId, action, null, r.version(), key, false));
    }

    @PostMapping("/coupon-activities/{activityId}/{action:pause|end|cancel}")
    public ApiResponse<CouponActivityAdminView> actionReason(@PathVariable long activityId, @PathVariable String action, @Valid @RequestBody ReasonVersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.activityAction(null, activityId, action, r.reason(), r.version(), key, false));
    }

    @GetMapping("/coupon-templates")
    public ApiResponse<PageView<CouponTemplateAdminSummaryView>> templates(@RequestParam(required = false) Long activityId, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus status, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponType couponType, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType distributionType, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ApiResponse.success(admin.templates(null, activityId, status, couponType, distributionType, keyword, page, pageSize, sort));
    }

    @PostMapping("/coupon-templates")
    public ResponseEntity<ApiResponse<CouponTemplateAdminDetailView>> createTemplate(@Valid @RequestBody CreateCouponTemplateRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(admin.createTemplate(null, r, key)));
    }

    @GetMapping("/coupon-templates/{templateId}")
    public ApiResponse<CouponTemplateAdminDetailView> template(@PathVariable long templateId) {
        return ApiResponse.success(admin.template(null, templateId));
    }

    @PutMapping("/coupon-templates/{templateId}")
    public ApiResponse<CouponTemplateAdminDetailView> updateTemplate(@PathVariable long templateId, @Valid @RequestBody UpdateCouponTemplateRequest r) {
        return ApiResponse.success(admin.updateTemplate(null, templateId, r));
    }

    @PutMapping("/coupon-templates/{templateId}/scope")
    public ApiResponse<CouponTemplateAdminDetailView> scope(@PathVariable long templateId, @Valid @RequestBody ScopeRequest r) {
        return ApiResponse.success(admin.replaceScope(null, templateId, r));
    }

    @PatchMapping("/coupon-templates/{templateId}/presentation")
    public ApiResponse<CouponTemplateAdminDetailView> presentation(@PathVariable long templateId, @Valid @RequestBody UpdateCouponPresentationRequest r) {
        return ApiResponse.success(admin.presentation(null, templateId, r));
    }

    @PostMapping("/coupon-templates/{templateId}/{action:activate|resume}")
    public ApiResponse<CouponTemplateAdminDetailView> templateAction(@PathVariable long templateId, @PathVariable String action, @Valid @RequestBody VersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.templateAction(null, templateId, action, null, r.version(), key));
    }

    @PostMapping("/coupon-templates/{templateId}/{action:pause|end}")
    public ApiResponse<CouponTemplateAdminDetailView> templateActionReason(@PathVariable long templateId, @PathVariable String action, @Valid @RequestBody ReasonVersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.templateAction(null, templateId, action, r.reason(), r.version(), key));
    }

    @PostMapping("/coupon-templates/{templateId}/copy")
    public ResponseEntity<ApiResponse<CouponTemplateAdminDetailView>> copy(@PathVariable long templateId, @Valid @RequestBody CopyCouponTemplateRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(admin.copyTemplate(null, templateId, r, key)));
    }

    @GetMapping("/coupon-templates/{templateId}/scope-targets")
    public ApiResponse<PageView<CouponScopeTargetView>> scopeTargets(@PathVariable long templateId, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "100") long pageSize) {
        return ApiResponse.success(admin.scopeTargets(null, templateId, page, pageSize));
    }

    @PostMapping("/coupon-templates/{templateId}/funding-invitations")
    public ApiResponse<List<CouponFundingParticipationView>> invite(@PathVariable long templateId, @Valid @RequestBody SendFundingInvitationRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.invite(templateId, r, key));
    }

    @PostMapping("/coupon-templates/{templateId}/grants")
    public ApiResponse<BatchCouponGrantView> grants(@PathVariable long templateId, @Valid @RequestBody GrantCouponsRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(coupons.grant(templateId, null, r, key));
    }

    @PostMapping("/coupon-templates/{templateId}/redeem-code-batches")
    public ResponseEntity<ApiResponse<CouponCodeBatchCreatedView>> codes(@PathVariable long templateId, @Valid @RequestBody CreateRedeemCodeBatchRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(coupons.createCodeBatch(templateId, null, r, key)));
    }

    @GetMapping("/coupon-code-batches")
    public ApiResponse<PageView<CouponCodeBatchSummaryView>> codeBatches(@RequestParam(required = false) Long templateId, @RequestParam(required = false) String batchNo, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponRedeemCodeStatus status, @RequestParam(required = false) LocalDateTime createdFrom, @RequestParam(required = false) LocalDateTime createdTo, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(coupons.codeBatches(null, templateId, batchNo, status, createdFrom, createdTo, page, pageSize));
    }

    @PostMapping("/coupon-governance/activities/{activityId}/pause")
    public ApiResponse<CouponActivityAdminView> governancePause(@PathVariable long activityId, @Valid @RequestBody ReasonVersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.activityAction(null, activityId, "pause", r.reason(), r.version(), key, true));
    }

    @PostMapping("/coupon-governance/activities/{activityId}/resume")
    public ApiResponse<CouponActivityAdminView> governanceResume(@PathVariable long activityId, @Valid @RequestBody VersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.activityAction(null, activityId, "resume", null, r.version(), key, true));
    }

    @PostMapping("/coupon-governance/user-coupons/{userCouponId}/revoke")
    public ApiResponse<UserCouponDetailView> revoke(@PathVariable long userCouponId, @Valid @RequestBody ReasonVersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(coupons.revoke(userCouponId, r.reason(), r.version(), key));
    }

    @GetMapping("/coupon-operations/activities")
    public ApiResponse<PageView<CouponActivityAdminView>> opActivities(@RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType ownerType, @RequestParam(required = false) Long shopId, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus status, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(admin.operationActivities(ownerType, shopId, status, keyword, page, pageSize));
    }

    @GetMapping("/coupon-operations/templates")
    public ApiResponse<PageView<CouponTemplateAdminSummaryView>> opTemplates(@RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType ownerType, @RequestParam(required = false) Long shopId, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus status, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponType couponType, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(admin.operationTemplates(ownerType, shopId, status, couponType, keyword, page, pageSize));
    }

    @GetMapping("/coupon-operations/user-coupons")
    public ApiResponse<PageView<OperationUserCouponView>> opUserCoupons(@RequestParam(required = false) String couponNo, @RequestParam(required = false) String templateNo, @RequestParam(required = false) Long userId, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus status, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(operations.userCoupons(couponNo, templateNo, userId, status, page, pageSize));
    }

    @GetMapping("/coupon-operations/redemptions")
    public ApiResponse<PageView<OperationCouponRedemptionView>> opRedemptions(@RequestParam(required = false) String redemptionNo, @RequestParam(required = false) String tradeNo, @RequestParam(required = false) String orderNo, @RequestParam(required = false) Long shopId, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponRedemptionStatus status, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(operations.redemptions(redemptionNo, tradeNo, orderNo, shopId, status, page, pageSize));
    }

    @GetMapping("/coupon-operations/trace")
    public ApiResponse<CouponBusinessTraceView> trace(@RequestParam String businessType, @RequestParam String businessNo) {
        return ApiResponse.success(operations.trace(businessType, businessNo));
    }
}
