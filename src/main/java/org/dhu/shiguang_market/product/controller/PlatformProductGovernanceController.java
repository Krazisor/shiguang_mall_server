package org.dhu.shiguang_market.product.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductGovernanceRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductReviewDetailView;
import org.dhu.shiguang_market.product.service.ProductReviewService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** phase-2 平台商品禁售治理接口。 */
@RestController
@RequestMapping("/api/platform/products/bans")
public class PlatformProductGovernanceController {
    private final ProductReviewService service;

    public PlatformProductGovernanceController(ProductReviewService service) {
        this.service = service;
    }

    /** 禁售已审核通过的上架或下架商品。 */
    @PostMapping("/{spuId}")
    public ApiResponse<ProductReviewDetailView> ban(
            @PathVariable long spuId, @Valid @RequestBody ProductGovernanceRequest request) {
        return ApiResponse.success(service.ban(spuId, request));
    }

    /** 解除禁售；商品解禁后保持下架，由店铺决定是否重新上架。 */
    @PostMapping("/{spuId}/revoke")
    public ApiResponse<ProductReviewDetailView> revoke(
            @PathVariable long spuId, @Valid @RequestBody ProductGovernanceRequest request) {
        return ApiResponse.success(service.revokeBan(spuId, request));
    }

    /** 平台强制下架正在销售的商品。 */
    @PostMapping("/{spuId}/take-off-shelf")
    public ApiResponse<ProductReviewDetailView> takeOffShelf(
            @PathVariable long spuId, @Valid @RequestBody ProductGovernanceRequest request) {
        return ApiResponse.success(service.takeOffShelf(spuId, request));
    }
}
