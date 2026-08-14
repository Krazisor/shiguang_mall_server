package org.dhu.shiguang_market.integration.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActiveShopBusinessArchiveTests {
    @BeforeEach
    void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "active-shop-business-archive-test");
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
        TableInfoHelper.initTableInfo(assistant, AfterSaleRequest.class);
        TableInfoHelper.initTableInfo(assistant, CouponActivity.class);
        TableInfoHelper.initTableInfo(assistant, CouponTemplate.class);
        TableInfoHelper.initTableInfo(assistant, UserCoupon.class);
    }

    @Test
    void archivedTemplatesDoNotCountAsActiveShopBusiness() {
        OrderInfoMapper orders = mock(OrderInfoMapper.class);
        AfterSaleRequestMapper afterSales = mock(AfterSaleRequestMapper.class);
        CouponActivityMapper activities = mock(CouponActivityMapper.class);
        CouponTemplateMapper templates = mock(CouponTemplateMapper.class);
        UserCouponMapper coupons = mock(UserCouponMapper.class);
        when(orders.exists(any())).thenReturn(false);
        when(afterSales.selectCount(any())).thenReturn(0L);
        when(afterSales.existsPendingAppealByShopId(8L)).thenReturn(false);
        when(activities.exists(any())).thenReturn(false);
        when(templates.exists(any())).thenReturn(false);
        when(coupons.exists(any())).thenReturn(false);

        boolean result = new ActiveShopBusinessAdapter(
                orders, afterSales, activities, templates, coupons).hasActiveBusiness(8L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<CouponTemplate>> query =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(templates).exists(query.capture());
        assertThat(result).isFalse();
        assertThat(query.getValue().getSqlSegment()).contains("status IN");
        assertThat(query.getValue().getParamNameValuePairs().values())
                .contains(CouponTemplateStatus.DRAFT, CouponTemplateStatus.ACTIVE,
                        CouponTemplateStatus.PAUSED)
                .doesNotContain(CouponTemplateStatus.ENDED, CouponTemplateStatus.ARCHIVED);
    }
}
