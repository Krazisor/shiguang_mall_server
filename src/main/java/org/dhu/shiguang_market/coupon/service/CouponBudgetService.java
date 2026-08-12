package org.dhu.shiguang_market.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.dhu.shiguang_market.coupon.mapper.CouponBudgetLedgerMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.BudgetLedger;
import org.dhu.shiguang_market.coupon.model.CouponModels.ClaimRecord;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.Redemption;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponBudgetService {
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CouponTemplateMapper templates;
    private final CouponBudgetLedgerMapper ledgers;
    private final NumberGenerator numbers;

    public CouponBudgetService(CouponTemplateMapper templates, CouponBudgetLedgerMapper ledgers,
                               NumberGenerator numbers) {
        this.templates = templates;
        this.ledgers = ledgers;
        this.numbers = numbers;
    }

    @Transactional
    public void recordClaim(CouponTemplate template, UserCoupon coupon, ClaimRecord claim) {
        CouponTemplate updated = templates.selectById(template.getId());
        BigDecimal liability = maxLiability(template);
        Funding funding = split(liability, template);
        insert(updated, coupon.getId(), null, "CLAIM_RESERVE", liability, ZERO, ZERO,
                funding.platform(), funding.shop(), "COUPON_CLAIM", claim.getClaimNo());
    }

    @Transactional
    public void consume(Redemption redemption) {
        CouponTemplate template = lockTemplate(redemption.getTemplateId());
        String businessType = "COUPON_REDEMPTION";
        String businessNo = redemption.getRedemptionNo();
        if (exists("USE_CONSUME", businessType, businessNo)) return;
        BigDecimal liability = maxLiability(template);
        if (templates.consumeBudget(template.getId(), liability, redemption.getDiscountAmount()) != 1) {
            throw new IllegalStateException("coupon budget changed during consume");
        }
        CouponTemplate updated = templates.selectById(template.getId());
        insert(updated, redemption.getUserCouponId(), redemption.getId(), "USE_CONSUME",
                liability.negate(), redemption.getDiscountAmount(), ZERO,
                redemption.getPlatformFundedAmount(), redemption.getShopFundedAmount(), businessType, businessNo);
    }

    @Transactional
    public void release(UserCoupon coupon, String entryType, String businessType, String businessNo) {
        CouponTemplate template = lockTemplate(coupon.getTemplateId());
        if (exists(entryType, businessType, businessNo)) return;
        BigDecimal liability = maxLiability(template);
        if (templates.releaseBudget(template.getId(), liability) != 1) {
            throw new IllegalStateException("coupon budget changed during release");
        }
        CouponTemplate updated = templates.selectById(template.getId());
        Funding funding = split(liability, template);
        insert(updated, coupon.getId(), null, entryType, liability.negate(), ZERO, ZERO,
                funding.platform(), funding.shop(), businessType, businessNo);
    }

    @Transactional
    public void reverse(Redemption redemption, BigDecimal amount, BigDecimal platformAmount,
                        BigDecimal shopAmount, String refundNo) {
        CouponTemplate template = lockTemplate(redemption.getTemplateId());
        String businessNo = refundNo + ":" + redemption.getRedemptionNo();
        if (exists("REFUND_REVERSE", "COUPON_REFUND", businessNo)) return;
        if (templates.reverseBudget(template.getId(), amount) != 1) {
            throw new IllegalStateException("coupon budget changed during refund reversal");
        }
        CouponTemplate updated = templates.selectById(template.getId());
        insert(updated, redemption.getUserCouponId(), redemption.getId(), "REFUND_REVERSE",
                ZERO, ZERO, amount, platformAmount, shopAmount, "COUPON_REFUND", businessNo);
    }

    @Transactional
    public void restore(UserCoupon coupon, Redemption redemption, String businessNo) {
        CouponTemplate template = lockTemplate(coupon.getTemplateId());
        if (exists("RESTORE_RESERVE", "COUPON_RESTORE", businessNo)) return;
        BigDecimal liability = maxLiability(template);
        if (templates.restoreBudget(template.getId(), liability) != 1) {
            throw new IllegalStateException("coupon budget changed during restore");
        }
        CouponTemplate updated = templates.selectById(template.getId());
        Funding funding = split(liability, template);
        insert(updated, coupon.getId(), redemption == null ? null : redemption.getId(), "RESTORE_RESERVE",
                liability, ZERO, ZERO, funding.platform(), funding.shop(),
                "COUPON_RESTORE", businessNo);
    }

    public BigDecimal maxLiability(CouponTemplate template) {
        return (template.getCouponType()
                == org.dhu.shiguang_market.common.model.MarketEnums.CouponType.PERCENTAGE
                ? template.getMaximumDiscountAmount() : template.getDiscountAmount()).setScale(2);
    }

    private CouponTemplate lockTemplate(long templateId) {
        CouponTemplate template = templates.selectOne(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getId, templateId).last("FOR UPDATE"));
        if (template == null) throw new IllegalStateException("coupon template not found");
        return template;
    }

    private boolean exists(String entryType, String businessType, String businessNo) {
        return ledgers.selectCount(new LambdaQueryWrapper<BudgetLedger>()
                .eq(BudgetLedger::getEntryType, entryType)
                .eq(BudgetLedger::getBusinessType, businessType)
                .eq(BudgetLedger::getBusinessNo, businessNo)) > 0;
    }

    private Funding split(BigDecimal amount, CouponTemplate template) {
        BigDecimal rate = template.getPlatformShareRate() == null ? ZERO : template.getPlatformShareRate();
        BigDecimal platform = amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new Funding(platform, amount.subtract(platform));
    }

    private void insert(CouponTemplate template, Long userCouponId, Long redemptionId, String entryType,
                        BigDecimal reservedChange, BigDecimal consumedChange, BigDecimal reversedChange,
                        BigDecimal platformAmount, BigDecimal shopAmount,
                        String businessType, String businessNo) {
        BudgetLedger ledger = new BudgetLedger();
        ledger.setLedgerNo(numbers.next("CBL"));
        ledger.setTemplateId(template.getId());
        ledger.setUserCouponId(userCouponId);
        ledger.setRedemptionId(redemptionId);
        ledger.setEntryType(entryType);
        ledger.setReservedChange(reservedChange.setScale(2));
        ledger.setConsumedChange(consumedChange.setScale(2));
        ledger.setReversedChange(reversedChange.setScale(2));
        ledger.setReservedAfter(template.getBudgetReservedAmount());
        ledger.setConsumedAfter(template.getBudgetConsumedAmount());
        ledger.setReversedAfter(template.getBudgetReversedAmount());
        ledger.setPlatformAmount(platformAmount.setScale(2));
        ledger.setShopAmount(shopAmount.setScale(2));
        ledger.setBusinessType(businessType);
        ledger.setBusinessNo(businessNo);
        ledgers.insert(ledger);
    }

    private record Funding(BigDecimal platform, BigDecimal shop) { }
}
