package org.dhu.shiguang_market.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CategoryScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.ShopScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.SkuScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.SpuScope;

public final class CouponScopeMappers {
    private CouponScopeMappers() {
    }

    public interface Shop extends BaseMapper<ShopScope> { }
    public interface Category extends BaseMapper<CategoryScope> { }
    public interface Spu extends BaseMapper<SpuScope> { }
    public interface Sku extends BaseMapper<SkuScope> { }
}
