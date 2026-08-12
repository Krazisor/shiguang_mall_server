package org.dhu.shiguang_market.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Collection;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.springframework.stereotype.Service;

@Service
public class CouponEligibilityService {
    private final SysUserMapper users;
    private final TradeOrderMapper trades;
    private final CouponTemplateMapper templates;

    public CouponEligibilityService(SysUserMapper users, TradeOrderMapper trades,
                                    CouponTemplateMapper templates) {
        this.users = users;
        this.trades = trades;
        this.templates = templates;
    }

    public void requireIssueEligibility(long userId, CouponTemplate template,
                                        CouponDistributionType source) {
        String reason = issueIneligibilityReason(userId, template, source);
        if (reason == null) return;
        if ("ACCOUNT_UNAVAILABLE".equals(reason)) {
            throw BusinessException.unprocessable("COUPON_AUDIENCE_NOT_ELIGIBLE", "用户账号不可参与发券");
        }
        if ("QUOTE_CHANGED".equals(reason)) {
            throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", "模板发放方式与当前渠道不一致");
        }
        throw BusinessException.unprocessable("COUPON_AUDIENCE_NOT_ELIGIBLE", "用户不符合优惠券人群资格");
    }

    public String issueIneligibilityReason(long userId, CouponTemplate template,
                                           CouponDistributionType source) {
        SysUser user = users.selectById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            return "ACCOUNT_UNAVAILABLE";
        }
        if (template.getDistributionType() != source) {
            return "QUOTE_CHANGED";
        }
        boolean eligible = switch (template.getAudienceType()) {
            case ALL_USERS -> true;
            case NEW_USERS -> template.getNewUserWithinDays() != null && user.getCreatedAt() != null
                    && !user.getCreatedAt().isBefore(LocalDateTime.now()
                    .minusDays(template.getNewUserWithinDays()));
            case FIRST_ORDER_USERS -> !hasPaidTrade(userId);
            case SPECIFIED_USERS -> source == CouponDistributionType.DIRECT_GRANT;
        };
        return eligible ? null : "AUDIENCE_NOT_ELIGIBLE";
    }

    public boolean eligibleForUse(long userId, CouponTemplate template) {
        return useIneligibilityReason(userId, template) == null;
    }

    public void requireUseEligibility(long userId, CouponTemplate template) {
        String reason = useIneligibilityReason(userId, template);
        if (reason == null) return;
        if ("FIRST_ORDER_QUALIFICATION_LOST".equals(reason)) {
            throw BusinessException.unprocessable("COUPON_FIRST_ORDER_QUALIFICATION_LOST", "用户已完成其他首单");
        }
        throw BusinessException.unprocessable("COUPON_AUDIENCE_NOT_ELIGIBLE", "用户账号当前不可使用优惠券");
    }

    /**
     * Coupon ownership is not enough for checkout: a disabled account and a first-order user that
     * has since paid both make the already-issued coupon unavailable.
     */
    public String useIneligibilityReason(long userId, CouponTemplate template) {
        SysUser user = users.selectById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            return "ACCOUNT_UNAVAILABLE";
        }
        if (template.getAudienceType() == CouponAudienceType.FIRST_ORDER_USERS && hasPaidTrade(userId)) {
            return "FIRST_ORDER_QUALIFICATION_LOST";
        }
        return null;
    }

    public boolean usesFirstOrderCoupon(Collection<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) return false;
        return templates.selectCount(new LambdaQueryWrapper<CouponTemplate>()
                .in(CouponTemplate::getId, templateIds)
                .eq(CouponTemplate::getAudienceType, CouponAudienceType.FIRST_ORDER_USERS)) > 0;
    }

    public boolean hasPaidTrade(long userId) {
        return trades.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId)
                .eq(TradeOrder::getTradeStatus, TradeStatus.PAID)) > 0;
    }

    public boolean hasOtherPaidTradeForUpdate(long userId, long excludedTradeId) {
        return trades.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId)
                .eq(TradeOrder::getTradeStatus, TradeStatus.PAID)
                .ne(TradeOrder::getId, excludedTradeId)
                .orderByAsc(TradeOrder::getId).last("LIMIT 1 FOR UPDATE")) != null;
    }

    public boolean eligibleForRestore(long userId, CouponTemplate template, long refundedTradeId) {
        return template.getAudienceType() != CouponAudienceType.FIRST_ORDER_USERS
                || trades.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId)
                .eq(TradeOrder::getTradeStatus, TradeStatus.PAID)
                .ne(TradeOrder::getId, refundedTradeId)) == 0;
    }
}
