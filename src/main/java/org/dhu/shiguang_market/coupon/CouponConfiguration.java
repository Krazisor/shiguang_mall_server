package org.dhu.shiguang_market.coupon;

import org.dhu.shiguang_market.coupon.service.CouponViewMapper;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CouponConfiguration {
    @Bean
    CouponViewMapper couponViewMapper(ShopMapper shopMapper) {
        return new CouponViewMapper(shopMapper);
    }
}
