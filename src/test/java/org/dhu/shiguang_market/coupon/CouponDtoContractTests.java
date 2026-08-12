package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import org.dhu.shiguang_market.coupon.dto.CouponDtos.CopyCouponTemplateRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UpdateCouponPresentationRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CouponDtoContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void distinguishesOmittedAndExplicitNullPatchFields() throws Exception {
        UpdateCouponPresentationRequest omitted = mapper.readValue("{\"version\":1}", UpdateCouponPresentationRequest.class);
        UpdateCouponPresentationRequest explicit = mapper.readValue("{\"description\":null,\"version\":1}", UpdateCouponPresentationRequest.class);
        assertThat(omitted.hasDescription()).isFalse();
        assertThat(explicit.hasDescription()).isTrue();
        assertThat(explicit.description()).isNull();
    }

    @Test
    void distinguishesOmittedAndExplicitNullCopyActivity() throws Exception {
        CopyCouponTemplateRequest omitted = mapper.readValue("{\"couponName\":\"copy\",\"copyScope\":false,\"version\":0}", CopyCouponTemplateRequest.class);
        CopyCouponTemplateRequest explicit = mapper.readValue("{\"couponName\":\"copy\",\"activityId\":null,\"copyScope\":false,\"version\":0}", CopyCouponTemplateRequest.class);
        assertThat(omitted.hasActivityId()).isFalse();
        assertThat(explicit.hasActivityId()).isTrue();
    }
}
