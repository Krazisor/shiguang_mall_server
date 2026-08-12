package org.dhu.shiguang_market.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedemptionStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.common.util.RequestContext;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.TaskRunView;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponBudgetLedgerMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponClaimRecordMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionAllocationMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRefundAllocationMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.model.CouponModels.BudgetLedger;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.Redemption;
import org.dhu.shiguang_market.coupon.model.CouponModels.RedemptionAllocation;
import org.dhu.shiguang_market.coupon.model.CouponModels.RefundAllocation;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.merchantwallet.mapper.ShopSettlementMapper;
import org.dhu.shiguang_market.merchantwallet.model.ShopSettlement;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.task.service.TaskLockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CouponTaskService {
    private static final Logger log = LoggerFactory.getLogger(CouponTaskService.class);
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final CouponActivityMapper activityMapper;private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper couponMapper;private final CouponRedemptionMapper redemptionMapper;
    private final TradeOrderMapper tradeMapper;private final CouponBudgetService budget;
    private final SysUserMapper userMapper;private final CouponService couponService;
    private final CouponClaimRecordMapper claimMapper;
    private final CouponBudgetLedgerMapper ledgerMapper;
    private final CouponRedemptionAllocationMapper allocationMapper;
    private final CouponRefundAllocationMapper refundMapper;
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final ShopSettlementMapper settlementMapper;
    private final TaskLockService locks;
    public CouponTaskService(CouponActivityMapper activityMapper,CouponTemplateMapper templateMapper,
                             UserCouponMapper couponMapper,CouponRedemptionMapper redemptionMapper,
                             TradeOrderMapper tradeMapper,CouponBudgetService budget,
                             SysUserMapper userMapper,CouponService couponService){
        this(activityMapper, templateMapper, couponMapper, redemptionMapper, tradeMapper, budget, userMapper,
                couponService, null, null, null, null, null, null, null, null);
    }
    @Autowired
    public CouponTaskService(CouponActivityMapper activityMapper,CouponTemplateMapper templateMapper,
                             UserCouponMapper couponMapper,CouponRedemptionMapper redemptionMapper,
                             TradeOrderMapper tradeMapper,CouponBudgetService budget,
                             SysUserMapper userMapper,CouponService couponService,
                             CouponClaimRecordMapper claimMapper, CouponBudgetLedgerMapper ledgerMapper,
                             CouponRedemptionAllocationMapper allocationMapper, OrderInfoMapper orderMapper,
                             OrderItemMapper itemMapper, ShopSettlementMapper settlementMapper,
                             CouponRefundAllocationMapper refundMapper, TaskLockService locks){
        this.activityMapper=activityMapper;this.templateMapper=templateMapper;this.couponMapper=couponMapper;
        this.redemptionMapper=redemptionMapper;this.tradeMapper=tradeMapper;this.budget=budget;
        this.userMapper=userMapper;this.couponService=couponService;
        this.claimMapper=claimMapper;this.ledgerMapper=ledgerMapper;this.allocationMapper=allocationMapper;
        this.refundMapper=refundMapper;
        this.orderMapper=orderMapper;this.itemMapper=itemMapper;this.settlementMapper=settlementMapper;
        this.locks=locks;
    }
    @Transactional public TaskRunView start(int size,boolean dry){return locked("start-coupon-activities",()->{
        LocalDateTime now=LocalDateTime.now();
        List<CouponActivity>x=activityMapper.selectList(new LambdaQueryWrapper<CouponActivity>()
                .eq(CouponActivity::getStatus,CouponActivityStatus.SCHEDULED)
                .le(CouponActivity::getStartsAt,now).gt(CouponActivity::getEndsAt,now)
                .orderByAsc(CouponActivity::getId).last("LIMIT "+size));
        int processed=0;
        if(!dry)for(CouponActivity a:x)processed+=activityMapper.update(null,
                new LambdaUpdateWrapper<CouponActivity>().eq(CouponActivity::getId,a.getId())
                        .eq(CouponActivity::getStatus,CouponActivityStatus.SCHEDULED)
                        .le(CouponActivity::getStartsAt,LocalDateTime.now())
                        .gt(CouponActivity::getEndsAt,LocalDateTime.now())
                        .set(CouponActivity::getStatus,CouponActivityStatus.RUNNING)
                        .setSql("version=version+1"));
        return view("start-coupon-activities",dry,x.size(),dry?0:processed,0);});}
    @Transactional public TaskRunView end(int size,boolean dry){return locked("end-coupon-activities",()->{
        List<CouponActivity>x=activityMapper.selectList(new LambdaQueryWrapper<CouponActivity>()
                .in(CouponActivity::getStatus,List.of(CouponActivityStatus.RUNNING,CouponActivityStatus.PAUSED,CouponActivityStatus.SCHEDULED))
                .le(CouponActivity::getEndsAt,LocalDateTime.now()).orderByAsc(CouponActivity::getId)
                .last("LIMIT "+size));
        int processed=0;
        if(!dry)for(CouponActivity a:x)processed+=activityMapper.update(null,
                new LambdaUpdateWrapper<CouponActivity>().eq(CouponActivity::getId,a.getId())
                        .eq(CouponActivity::getStatus,a.getStatus())
                        .le(CouponActivity::getEndsAt,LocalDateTime.now())
                        .set(CouponActivity::getStatus,CouponActivityStatus.ENDED)
                        .set(CouponActivity::getPauseReason,null)
                        .set(CouponActivity::getPauseSource,null)
                        .setSql("version=version+1"));
        return view("end-coupon-activities",dry,x.size(),dry?0:processed,0);});}
    @Transactional public TaskRunView expire(int size,boolean dry){return locked("expire-user-coupons",()->{
        List<UserCoupon>x=couponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getStatus,UserCouponStatus.AVAILABLE).le(UserCoupon::getValidTo,LocalDateTime.now())
                .orderByAsc(UserCoupon::getId).last("LIMIT "+size));
        int processed=0;
        if(!dry){for(UserCoupon c:x){c.setStatus(UserCouponStatus.EXPIRED);c.setExpiredAt(LocalDateTime.now());
            if(couponMapper.updateById(c)==1){budget.release(c,"EXPIRE_RELEASE","COUPON_EXPIRY",c.getCouponNo());processed++;}}}
        return view("expire-user-coupons",dry,x.size(),dry?0:processed,0);});
    }
    @Transactional public TaskRunView recover(int size,boolean dry){return locked("recover-coupon-reservations",()->{List<UserCoupon>x=couponMapper.selectList(new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getStatus,UserCouponStatus.LOCKED).orderByAsc(UserCoupon::getId).last("LIMIT "+size));int processed=0;for(UserCoupon c:x){TradeOrder t=c.getLockedTradeId()==null?null:tradeMapper.selectById(c.getLockedTradeId());if(t==null||t.getTradeStatus()==org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus.CANCELLED){if(!dry){c.setLockedTradeId(null);boolean expired=!LocalDateTime.now().isBefore(c.getValidTo());c.setStatus(expired?UserCouponStatus.EXPIRED:UserCouponStatus.AVAILABLE);if(expired)c.setExpiredAt(LocalDateTime.now());if(couponMapper.updateById(c)==1&&expired)budget.release(c,"EXPIRE_RELEASE","COUPON_EXPIRY",c.getCouponNo());Redemption r=redemptionMapper.selectOne(new LambdaQueryWrapper<Redemption>().eq(Redemption::getUserCouponId,c.getId()).eq(Redemption::getStatus,CouponRedemptionStatus.RESERVED));if(r!=null){r.setStatus(CouponRedemptionStatus.RELEASED);r.setReleasedAt(LocalDateTime.now());r.setReleaseReason("SYSTEM_RECOVERY");redemptionMapper.updateById(r);}}processed++;}}return view("recover-coupon-reservations",dry,x.size(),dry?0:processed,0);});}
    @Transactional(readOnly=true)
    public TaskRunView reconcile(int size){return locked("reconcile-coupons",()->{
        List<CouponTemplate> templates=templateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>()
                .orderByAsc(CouponTemplate::getId).last("LIMIT "+size));
        int mismatches=0;
        for(CouponTemplate template:templates){
            long issued=couponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getTemplateId,template.getId()));
            mismatches+=compare("issued_count",template.getId(),template.getIssuedCount(),issued);
            if(ledgerMapper!=null){
                List<BudgetLedger> rows=ledgerMapper.selectList(new LambdaQueryWrapper<BudgetLedger>()
                        .eq(BudgetLedger::getTemplateId,template.getId()));
                mismatches+=compare("budget_reserved",template.getId(),template.getBudgetReservedAmount(),sum(rows,BudgetLedger::getReservedChange));
                mismatches+=compare("budget_consumed",template.getId(),template.getBudgetConsumedAmount(),sum(rows,BudgetLedger::getConsumedChange));
                mismatches+=compare("budget_reversed",template.getId(),template.getBudgetReversedAmount(),sum(rows,BudgetLedger::getReversedChange));
            }
            List<Redemption> redemptions=redemptionMapper.selectList(new LambdaQueryWrapper<Redemption>()
                    .eq(Redemption::getTemplateId,template.getId()).orderByAsc(Redemption::getId));
            for(Redemption redemption:redemptions){
                if(allocationMapper==null) break;
                List<RedemptionAllocation> allocations=allocationMapper.selectList(new LambdaQueryWrapper<RedemptionAllocation>()
                        .eq(RedemptionAllocation::getRedemptionId,redemption.getId()));
                mismatches+=compare("redemption_discount",redemption.getId(),redemption.getDiscountAmount(),sum(allocations,RedemptionAllocation::getDiscountAmount));
                mismatches+=compare("redemption_platform_funding",redemption.getId(),redemption.getPlatformFundedAmount(),sum(allocations,RedemptionAllocation::getPlatformFundedAmount));
                mismatches+=compare("redemption_shop_funding",redemption.getId(),redemption.getShopFundedAmount(),sum(allocations,RedemptionAllocation::getShopFundedAmount));
            }
        }
        if(allocationMapper!=null&&itemMapper!=null&&orderMapper!=null){
            mismatches+=reconcileOrderAmounts(size);
            mismatches+=reconcileTradeAmounts(size);
        }
        if(allocationMapper!=null&&settlementMapper!=null){
            mismatches+=reconcileSettlementAmounts(size);
        }
        return view("reconcile-coupons",true,templates.size(),0,mismatches);});
    }
    public TaskRunView grantSystem(int size,boolean dry){
        return locked("grant-system-coupons",()->grantSystemUnlocked(size,dry));
    }
    private TaskRunView grantSystemUnlocked(int size,boolean dry){
        if(claimMapper==null) return legacyGrantSystem(size,dry);
        List<CouponClaimRecordMapper.SystemGrantCandidate> candidates=claimMapper.selectMissingSystemGrantCandidates(size);
        int scanned=0,succeeded=0,failed=0;
        for(CouponClaimRecordMapper.SystemGrantCandidate candidate:candidates){scanned++;
            if(!dry){try{couponService.grantSystemCoupon(candidate.userId(),candidate.templateId());succeeded++;}
            catch(RuntimeException ex){failed++;log.warn("System coupon compensation failed userId={} templateId={}",candidate.userId(),candidate.templateId(),ex);}}}
        return new TaskRunView("grant-system-coupons",dry,scanned,dry?0:succeeded+failed,
                dry?0:succeeded,dry?0:failed,0,RequestContext.now(),RequestContext.now(),RequestContext.requestId());
    }
    public void grantSystemForUser(long userId){
        List<CouponTemplate> templates=templateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getOwnerType,CouponOwnerType.PLATFORM)
                .eq(CouponTemplate::getDistributionType,CouponDistributionType.SYSTEM_GRANT)
                .eq(CouponTemplate::getAudienceType,CouponAudienceType.NEW_USERS)
                .eq(CouponTemplate::getStatus,CouponTemplateStatus.ACTIVE).orderByAsc(CouponTemplate::getId));
        for(CouponTemplate template:templates)couponService.grantSystemCoupon(userId,template.getId());
    }
    private TaskRunView legacyGrantSystem(int size,boolean dry){
        List<SysUser> users=userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus,UserStatus.ACTIVE).orderByAsc(SysUser::getId).last("LIMIT "+size));
        List<CouponTemplate> templates=templateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getOwnerType,CouponOwnerType.PLATFORM)
                .eq(CouponTemplate::getDistributionType,CouponDistributionType.SYSTEM_GRANT)
                .eq(CouponTemplate::getAudienceType,CouponAudienceType.NEW_USERS)
                .eq(CouponTemplate::getStatus,CouponTemplateStatus.ACTIVE).orderByAsc(CouponTemplate::getId));
        int scanned=0,succeeded=0,failed=0;
        outer:for(SysUser user:users)for(CouponTemplate template:templates){if(scanned>=size)break outer;scanned++;if(!dry)try{couponService.grantSystemCoupon(user.getId(),template.getId());succeeded++;}catch(RuntimeException ignored){failed++;}}
        return new TaskRunView("grant-system-coupons",dry,scanned,dry?0:succeeded+failed,dry?0:succeeded,dry?0:failed,0,RequestContext.now(),RequestContext.now(),RequestContext.requestId());
    }
    private int reconcileOrderAmounts(int size){
        int mismatches=0;
        List<OrderItem> items=itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().orderByAsc(OrderItem::getId).last("LIMIT "+size));
        for(OrderItem item:items){
            BigDecimal expected=sum(allocationMapper.selectList(new LambdaQueryWrapper<RedemptionAllocation>().eq(RedemptionAllocation::getOrderItemId,item.getId())),RedemptionAllocation::getDiscountAmount);
            mismatches+=compare("order_item_discount",item.getId(),item.getCouponDiscountAmount(),expected);
        }
        List<OrderInfo> orders=orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>().orderByAsc(OrderInfo::getId).last("LIMIT "+size));
        for(OrderInfo order:orders){
            BigDecimal expected=sum(itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId,order.getId())),OrderItem::getCouponDiscountAmount);
            mismatches+=compare("order_discount",order.getId(),order.getCouponDiscountAmount(),expected);
        }
        return mismatches;
    }
    private int reconcileTradeAmounts(int size){
        int mismatches=0;
        for(TradeOrder trade:tradeMapper.selectList(new LambdaQueryWrapper<TradeOrder>().orderByAsc(TradeOrder::getId).last("LIMIT "+size))){
            BigDecimal expected=sum(orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getTradeId,trade.getId())),OrderInfo::getCouponDiscountAmount);
            mismatches+=compare("trade_discount",trade.getId(),trade.getCouponDiscountAmount(),expected);
        }
        return mismatches;
    }
    private int reconcileSettlementAmounts(int size){
        int mismatches=0;
        for(ShopSettlement settlement:settlementMapper.selectList(new LambdaQueryWrapper<ShopSettlement>().orderByAsc(ShopSettlement::getId).last("LIMIT "+size))){
            List<RedemptionAllocation> allocations=allocationMapper.selectList(new LambdaQueryWrapper<RedemptionAllocation>()
                    .eq(RedemptionAllocation::getOrderId,settlement.getOrderId()).eq(RedemptionAllocation::getShopId,settlement.getShopId()));
            mismatches+=compare("settlement_platform_subsidy",settlement.getId(),settlement.getPlatformCouponSubsidyAmount(),sum(allocations,RedemptionAllocation::getPlatformFundedAmount));
            mismatches+=compare("settlement_shop_discount",settlement.getId(),settlement.getShopCouponDiscountAmount(),sum(allocations,RedemptionAllocation::getShopFundedAmount));
            if(refundMapper!=null){
                List<Long> allocationIds=allocations.stream().map(RedemptionAllocation::getId).toList();
                BigDecimal reversed=allocationIds.isEmpty()?ZERO:sum(refundMapper.selectList(
                        new LambdaQueryWrapper<RefundAllocation>().in(RefundAllocation::getRedemptionAllocationId,allocationIds)),
                        RefundAllocation::getPlatformFundingReversalAmount);
                mismatches+=compare("settlement_platform_subsidy_refund",settlement.getId(),
                        settlement.getPlatformSubsidyRefundAmount(),reversed);
            }
        }
        return mismatches;
    }
    private <T> BigDecimal sum(List<T> rows,java.util.function.Function<T,BigDecimal> getter){return rows.stream().map(getter).filter(java.util.Objects::nonNull).reduce(ZERO,BigDecimal::add).setScale(2);}
    private int compare(String target,Object id,Object actual,Object expected){
        if(actual instanceof BigDecimal a&&expected instanceof BigDecimal e){if(a.compareTo(e)==0)return 0;}else if(java.util.Objects.equals(actual,expected))return 0;
        log.warn("Coupon reconciliation mismatch target={} id={} expected={} actual={}; no data was modified",target,id,expected,actual);return 1;
    }
    private <T> T locked(String taskName,Supplier<T> action){
        if(locks==null)return action.get();
        String token=UUID.randomUUID().toString();
        if(!locks.tryLock(taskName,token))throw org.dhu.shiguang_market.common.exception.BusinessException.conflict("TASK_ALREADY_RUNNING","任务正在执行中");
        boolean defer=TransactionSynchronizationManager.isSynchronizationActive();
        if(defer)TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){locks.unlock(taskName,token);}});
        try{return action.get();}finally{if(!defer)locks.unlock(taskName,token);}
    }
    private TaskRunView view(String n,boolean d,int s,int p,int m){OffsetDateTime now=RequestContext.now();return new TaskRunView(n,d,s,p,p,0,m,now,RequestContext.now(),RequestContext.requestId());}
}
