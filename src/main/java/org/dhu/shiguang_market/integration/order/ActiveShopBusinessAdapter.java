package org.dhu.shiguang_market.integration.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 使用 B 线订单和售后表实现店铺活跃业务检查。 */
@Component
public class ActiveShopBusinessAdapter implements ActiveShopBusinessPort {
    private final OrderInfoMapper orderMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final CouponActivityMapper activityMapper;
    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper couponMapper;

    @Autowired
    public ActiveShopBusinessAdapter(OrderInfoMapper orderMapper, AfterSaleRequestMapper afterSaleMapper,
                                     CouponActivityMapper activityMapper, CouponTemplateMapper templateMapper,
                                     UserCouponMapper couponMapper) {
        this.orderMapper = orderMapper;
        this.afterSaleMapper = afterSaleMapper;
        this.activityMapper = activityMapper;
        this.templateMapper = templateMapper;
        this.couponMapper = couponMapper;
    }

    /** Kept for focused phase-six tests that predate the coupon domain. */
    public ActiveShopBusinessAdapter(OrderInfoMapper orderMapper, AfterSaleRequestMapper afterSaleMapper) {
        this(orderMapper, afterSaleMapper, null, null, null);
    }

    /** 先检查订单，命中后直接返回；只有无活跃订单时才继续查询售后。 */
    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveBusiness(long shopId) {
        boolean activeOrders = orderMapper.exists(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getShopId, shopId)
                .in(OrderInfo::getOrderStatus, OrderStatus.PENDING_PAYMENT,
                        OrderStatus.PENDING_SHIPMENT, OrderStatus.PENDING_RECEIPT));
        if (activeOrders) return true;

        boolean activeAfterSale = afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSaleRequest>()
                .in(AfterSaleRequest::getStatus, AfterSaleStatus.PENDING,
                        AfterSaleStatus.WAITING_RETURN, AfterSaleStatus.REFUNDING)
                .inSql(AfterSaleRequest::getOrderId,
                "SELECT id FROM order_info WHERE shop_id = " + shopId)) > 0;
        if (activeAfterSale || afterSaleMapper.existsPendingAppealByShopId(shopId)) return true;
        if (activityMapper == null || templateMapper == null || couponMapper == null) return false;

        boolean activeActivities = activityMapper.exists(new LambdaQueryWrapper<CouponActivity>()
                .eq(CouponActivity::getShopId, shopId)
                .in(CouponActivity::getStatus, CouponActivityStatus.RUNNING, CouponActivityStatus.PAUSED));
        if (activeActivities) return true;

        boolean unfinishedTemplates = templateMapper.exists(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getOwnerShopId, shopId)
                .ne(CouponTemplate::getStatus, CouponTemplateStatus.ENDED));
        if (unfinishedTemplates) return true;

        return couponMapper.exists(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getStatus, UserCouponStatus.LOCKED)
                .inSql(UserCoupon::getTemplateId,
                        "SELECT id FROM coupon_template WHERE owner_shop_id = " + shopId));
    }
}
