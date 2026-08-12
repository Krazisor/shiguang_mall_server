package org.dhu.shiguang_market.coupon.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.BenefitView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ScopeView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.TemplateView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UserCouponDetailView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ValidityView;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;

public class CouponViewMapper {
    private final ShopMapper shopMapper;

    public CouponViewMapper(ShopMapper shopMapper) {
        this.shopMapper = shopMapper;
    }

    public TemplateView template(CouponTemplate template) {
        ShopSummary shop = template.getOwnerShopId() == null ? null
                : IdentityViewMapper.shop(shopMapper.selectById(template.getOwnerShopId()));
        BenefitView benefit = new BenefitView(money(template.getThresholdAmount()),
                money(template.getDiscountAmount()), percent(template.getPercentageOff()),
                money(template.getMaximumDiscountAmount()), displayText(template));
        ScopeView scope = new ScopeView(template.getScopeType(), scopeSummary(template),
                null, null, null, null);
        ValidityView validity = new ValidityView(template.getValidityType(), time(template.getValidFrom()),
                time(template.getValidTo()), template.getEffectiveDelayMinutes(), template.getValidForHours(),
                validitySummary(template));
        return new TemplateView(id(template.getId()), template.getTemplateNo(), template.getCouponName(),
                template.getOwnerType(), shop, template.getCouponType(), benefit, scope, validity,
                template.getStackMode(), template.getDescription());
    }

    public UserCouponDetailView detail(UserCoupon coupon, CouponTemplate template, LocalDateTime now,
                                       String claimSource, LocalDateTime claimedAt,
                                       String qualificationReason) {
        UserCouponStatus display = coupon.getStatus() == UserCouponStatus.AVAILABLE
                && !now.isBefore(coupon.getValidTo()) ? UserCouponStatus.EXPIRED : coupon.getStatus();
        String reason = switch (display) {
            case EXPIRED -> "EXPIRED";
            case REVOKED -> "REVOKED";
            case LOCKED -> "LOCKED_BY_OTHER_TRADE";
            case USED -> "COUPON_ALREADY_USED";
            case AVAILABLE -> now.isBefore(coupon.getValidFrom()) ? "NOT_EFFECTIVE" : qualificationReason;
            default -> null;
        };
        List<String> actions = display == UserCouponStatus.AVAILABLE && reason == null
                ? List.of("VIEW_ELIGIBLE_PRODUCTS", "USE") : List.of();
        return new UserCouponDetailView(id(coupon.getId()), coupon.getCouponNo(), template(template),
                coupon.getStatus(), display, time(coupon.getValidFrom()), time(coupon.getValidTo()),
                id(coupon.getLockedTradeId()), claimSource, time(claimedAt), time(coupon.getUsedAt()),
                coupon.getRestoreCount() == null ? 0 : coupon.getRestoreCount(), time(coupon.getLastRestoredAt()),
                reason, actions);
    }

    private String displayText(CouponTemplate t) {
        return switch (t.getCouponType()) {
            case PERCENTAGE -> "减 " + percent(t.getPercentageOff()) + "%";
            case THRESHOLD_REDUCTION -> "满 " + money(t.getThresholdAmount()) + " 减 " + money(t.getDiscountAmount());
            case CASH_RED_PACKET -> "红包 " + money(t.getDiscountAmount());
        };
    }

    private String scopeSummary(CouponTemplate t) {
        return switch (t.getScopeType()) {
            case ALL -> "全平台可用";
            case SHOP -> "指定店铺可用";
            case CATEGORY -> "指定类目可用";
            case SPU -> "指定商品可用";
            case SKU -> "指定 SKU 可用";
        };
    }

    private String validitySummary(CouponTemplate t) {
        return t.getValidityType().name().equals("FIXED_RANGE")
                ? moneyDate(t.getValidFrom()) + " 至 " + moneyDate(t.getValidTo())
                : "领取后 " + t.getValidForHours() + " 小时内有效";
    }

    private String moneyDate(LocalDateTime value) {
        return value == null ? "" : time(value).toString();
    }

    private String percent(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
