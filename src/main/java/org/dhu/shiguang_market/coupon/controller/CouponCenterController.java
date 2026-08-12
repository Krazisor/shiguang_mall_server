package org.dhu.shiguang_market.coupon.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityType;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ClaimableActivityDetailView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ClaimableActivitySummaryView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UserCouponDetailView;
import org.dhu.shiguang_market.coupon.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupon-center/activities")
public class CouponCenterController {
    private final CouponService service;
    public CouponCenterController(CouponService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageView<ClaimableActivitySummaryView>> list(
            @RequestParam(required=false) CouponActivityType activityType,
            @RequestParam(required=false) Long shopId,
            @RequestParam(required=false) CouponActivityStatus status,
            @RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long pageSize,
            @RequestParam(defaultValue="startsAt,asc") String sort) {
        return ApiResponse.success(service.center(activityType,shopId,status,page,pageSize,sort));
    }
    @GetMapping("/{activityId}")
    public ApiResponse<ClaimableActivityDetailView> detail(@PathVariable long activityId) {
        return ApiResponse.success(service.centerDetail(activityId));
    }
    @PostMapping("/{activityId}/templates/{templateId}/claim")
    public ResponseEntity<ApiResponse<UserCouponDetailView>> claim(@PathVariable long activityId,
            @PathVariable long templateId,@RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.claim(activityId,templateId,key)));
    }
}
