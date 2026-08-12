package org.dhu.shiguang_market.coupon.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.RedeemCouponCodeRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UserCouponDetailView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UserCouponSummaryView;
import org.dhu.shiguang_market.coupon.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
public class UserCouponController {
    private final CouponService service;

    public UserCouponController(CouponService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageView<UserCouponSummaryView>> list(@RequestParam(required = false) UserCouponStatus status, @RequestParam(required = false) CouponType couponType, @RequestParam(required = false) CouponOwnerType ownerType, @RequestParam(required = false) java.time.OffsetDateTime expiringBefore, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) String sort) {
        return ApiResponse.success(service.mine(status, couponType, ownerType, expiringBefore == null ? null : expiringBefore.toLocalDateTime(), keyword, page, pageSize, sort));
    }

    @GetMapping("/{userCouponId}")
    public ApiResponse<UserCouponDetailView> detail(@PathVariable long userCouponId) {
        return ApiResponse.success(service.mineDetail(userCouponId));
    }

    @GetMapping("/{userCouponId}/eligible-products")
    public ApiResponse<PageView<org.dhu.shiguang_market.product.dto.ProductDtos.ProductCardView>> eligible(@PathVariable long userCouponId, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) String sort) {
        return ApiResponse.success(service.eligibleProducts(userCouponId, keyword, page, pageSize, sort));
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<UserCouponDetailView>> redeem(@Valid @RequestBody RedeemCouponCodeRequest request, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.redeem(request, key)));
    }
}
