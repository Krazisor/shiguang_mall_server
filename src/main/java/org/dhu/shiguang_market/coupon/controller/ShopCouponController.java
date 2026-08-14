package org.dhu.shiguang_market.coupon.controller;

import jakarta.validation.Valid;

import java.time.LocalDateTime;

import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.*;
import org.dhu.shiguang_market.coupon.service.CouponAdminService;
import org.dhu.shiguang_market.coupon.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shops/{shopId}")
public class ShopCouponController {
    private final CouponAdminService admin;
    private final CouponService coupons;

    public ShopCouponController(CouponAdminService admin, CouponService coupons) {
        this.admin = admin;
        this.coupons = coupons;
    }

    @GetMapping("/coupon-activities")
    public ApiResponse<PageView<CouponActivityAdminView>> activities(@PathVariable long shopId, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus status, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityType activityType, @RequestParam(required = false) String keyword, @RequestParam(required = false) LocalDateTime createdFrom, @RequestParam(required = false) LocalDateTime createdTo, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ApiResponse.success(admin.activities(shopId, status, activityType, keyword, createdFrom, createdTo, page, pageSize, sort));
    }

    @PostMapping("/coupon-activities")
    public ResponseEntity<ApiResponse<CouponActivityAdminView>> createActivity(@PathVariable long shopId, @Valid @RequestBody CreateCouponActivityRequest request, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(admin.createActivity(shopId, request, key)));
    }

    @PostMapping("/coupon-activities/recurring")
    public ResponseEntity<ApiResponse<CouponActivityAdminView>> createRecurringActivity(
            @PathVariable long shopId, @Valid @RequestBody CreateRecurringCouponActivityRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(admin.createRecurringActivity(shopId, request, key)));
    }

    @GetMapping("/coupon-activities/{activityId}")
    public ApiResponse<CouponActivityAdminView> activity(@PathVariable long shopId, @PathVariable long activityId) {
        return ApiResponse.success(admin.activity(shopId, activityId));
    }

    @GetMapping("/coupon-activities/{activityId}/schedule")
    public ApiResponse<CouponActivityScheduleView> activitySchedule(@PathVariable long shopId,
                                                                    @PathVariable long activityId) {
        return ApiResponse.success(admin.activitySchedule(shopId, activityId));
    }

    @PutMapping("/coupon-activities/{activityId}/schedule")
    public ApiResponse<CouponActivityScheduleView> updateActivitySchedule(@PathVariable long shopId,
                                                                          @PathVariable long activityId,
                                                                          @Valid @RequestBody UpdateCouponActivityScheduleRequest request) {
        return ApiResponse.success(admin.updateActivitySchedule(shopId, activityId, request));
    }

    @PutMapping("/coupon-activities/{activityId}")
    public ApiResponse<CouponActivityAdminView> updateActivity(@PathVariable long shopId, @PathVariable long activityId, @Valid @RequestBody UpdateCouponActivityRequest request) {
        return ApiResponse.success(admin.updateActivity(shopId, activityId, request));
    }

    @PostMapping("/coupon-activities/{activityId}/{action:publish|resume}")
    public ApiResponse<CouponActivityAdminView> activitySimple(@PathVariable long shopId, @PathVariable long activityId, @PathVariable String action, @Valid @RequestBody VersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.activityAction(shopId, activityId, action, null, r.version(), key, false));
    }

    @PostMapping("/coupon-activities/{activityId}/{action:pause|end|cancel}")
    public ApiResponse<CouponActivityAdminView> activityReason(@PathVariable long shopId, @PathVariable long activityId, @PathVariable String action, @Valid @RequestBody ReasonVersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.activityAction(shopId, activityId, action, r.reason(), r.version(), key, false));
    }

    @GetMapping("/coupon-templates")
    public ApiResponse<PageView<CouponTemplateAdminSummaryView>> templates(@PathVariable long shopId, @RequestParam(required = false) Long activityId, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus status, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponType couponType, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType distributionType, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ApiResponse.success(admin.templates(shopId, activityId, status, couponType, distributionType, keyword, page, pageSize, sort));
    }

    @PostMapping("/coupon-templates")
    public ResponseEntity<ApiResponse<CouponTemplateAdminDetailView>> createTemplate(@PathVariable long shopId, @Valid @RequestBody CreateCouponTemplateRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(admin.createTemplate(shopId, r, key)));
    }

    @GetMapping("/coupon-templates/{templateId}")
    public ApiResponse<CouponTemplateAdminDetailView> template(@PathVariable long shopId, @PathVariable long templateId) {
        return ApiResponse.success(admin.template(shopId, templateId));
    }

    @PutMapping("/coupon-templates/{templateId}")
    public ApiResponse<CouponTemplateAdminDetailView> updateTemplate(@PathVariable long shopId, @PathVariable long templateId, @Valid @RequestBody UpdateCouponTemplateRequest r) {
        return ApiResponse.success(admin.updateTemplate(shopId, templateId, r));
    }

    @PutMapping("/coupon-templates/{templateId}/scope")
    public ApiResponse<CouponTemplateAdminDetailView> scope(@PathVariable long shopId, @PathVariable long templateId, @Valid @RequestBody ScopeRequest r) {
        return ApiResponse.success(admin.replaceScope(shopId, templateId, r));
    }

    @PatchMapping("/coupon-templates/{templateId}/presentation")
    public ApiResponse<CouponTemplateAdminDetailView> presentation(@PathVariable long shopId, @PathVariable long templateId, @Valid @RequestBody UpdateCouponPresentationRequest r) {
        return ApiResponse.success(admin.presentation(shopId, templateId, r));
    }

    @PostMapping("/coupon-templates/{templateId}/{action:activate|resume}")
    public ApiResponse<CouponTemplateAdminDetailView> templateAction(@PathVariable long shopId, @PathVariable long templateId, @PathVariable String action, @Valid @RequestBody VersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.templateAction(shopId, templateId, action, null, r.version(), key));
    }

    @PostMapping("/coupon-templates/{templateId}/{action:pause|end}")
    public ApiResponse<CouponTemplateAdminDetailView> templateActionReason(@PathVariable long shopId, @PathVariable long templateId, @PathVariable String action, @Valid @RequestBody ReasonVersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.templateAction(shopId, templateId, action, r.reason(), r.version(), key));
    }

    @PostMapping("/coupon-templates/{templateId}/archive")
    public ApiResponse<CouponTemplateAdminDetailView> archiveTemplate(@PathVariable long shopId, @PathVariable long templateId, @Valid @RequestBody ReasonVersionRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.templateAction(shopId, templateId, "archive", r.reason(), r.version(), key));
    }

    @PostMapping("/coupon-templates/{templateId}/copy")
    public ResponseEntity<ApiResponse<CouponTemplateAdminDetailView>> copy(@PathVariable long shopId, @PathVariable long templateId, @Valid @RequestBody CopyCouponTemplateRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(admin.copyTemplate(shopId, templateId, r, key)));
    }

    @GetMapping("/coupon-templates/{templateId}/scope-targets")
    public ApiResponse<PageView<CouponScopeTargetView>> scopeTargets(@PathVariable long shopId, @PathVariable long templateId, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "100") long pageSize) {
        return ApiResponse.success(admin.scopeTargets(shopId, templateId, page, pageSize));
    }

    @PostMapping("/coupon-templates/{templateId}/grants")
    public ApiResponse<BatchCouponGrantView> grants(@PathVariable long shopId, @PathVariable long templateId, @Valid @RequestBody GrantCouponsRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(coupons.grant(templateId, shopId, r, key));
    }

    @PostMapping("/coupon-templates/{templateId}/redeem-code-batches")
    public ResponseEntity<ApiResponse<CouponCodeBatchCreatedView>> codes(@PathVariable long shopId, @PathVariable long templateId, @Valid @RequestBody CreateRedeemCodeBatchRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(coupons.createCodeBatch(templateId, shopId, r, key)));
    }

    @GetMapping("/coupon-code-batches")
    public ApiResponse<PageView<CouponCodeBatchSummaryView>> codeBatches(@PathVariable long shopId, @RequestParam(required = false) Long templateId, @RequestParam(required = false) String batchNo, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponRedeemCodeStatus status, @RequestParam(required = false) LocalDateTime createdFrom, @RequestParam(required = false) LocalDateTime createdTo, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(coupons.codeBatches(shopId, templateId, batchNo, status, createdFrom, createdTo, page, pageSize));
    }

    @GetMapping("/coupon-funding-invitations")
    public ApiResponse<PageView<CouponFundingParticipationView>> invitations(@PathVariable long shopId, @RequestParam(required = false) org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingParticipationStatus status, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(admin.invitations(shopId, status, page, pageSize));
    }

    @PostMapping("/coupon-funding-invitations/{participationId}/decide")
    public ApiResponse<CouponFundingParticipationView> decide(@PathVariable long shopId, @PathVariable long participationId, @Valid @RequestBody DecideCouponFundingRequest r, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(admin.decide(shopId, participationId, r, key));
    }
}
