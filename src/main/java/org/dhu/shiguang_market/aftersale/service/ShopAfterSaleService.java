package org.dhu.shiguang_market.aftersale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleSummaryView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ApproveAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ConfirmReturnReceivedRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RejectAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RetryRefundRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ShopAfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ShopAfterSaleSummaryView;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletTransactionType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.mapper.WalletTransactionMapper;
import org.dhu.shiguang_market.payment.model.WalletAccount;
import org.dhu.shiguang_market.payment.model.WalletTransaction;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.dhu.shiguang_market.integration.merchantwallet.MerchantSettlementPort;
import org.dhu.shiguang_market.coupon.service.CouponRefundService;

/**
 * 商家端售后 Service。
 * <p>
 * 负责本店售后列表/详情、审核（批准/拒绝）、确认退货收货以及退款重试。
 * 所有写操作在用例边界使用 {@link Transactional}，并发关键路径使用 FOR UPDATE 行锁保护。
 * 批准和确认收货为幂等接口，通过 {@link IdempotencyService} 包裹。
 * <p>
 * <b>退款执行核心逻辑：</b>
 * <ol>
 *   <li>仅退款批准：立即执行退款 → 释放锁定库存 → 全额退款时取消子订单</li>
 *   <li>退货退款批准：进入 WAITING_RETURN 等待买家退货</li>
 *   <li>确认退货收货：退货回库 + 写 RETURN 库存流水 + 执行退款</li>
 *   <li>退款重试：复用原 refundNo，仅重试钱包退款和订单累计退款更新，不复增库存</li>
 * </ol>
 * <p>
 * <b>批准额度校验（批准口径）：</b>
 * 在锁售后行和订单明细后，从其他 WAITING_RETURN/REFUNDING 申请中扣除已批准且未结束的量后复核本次批准量。
 * PENDING 申请在创建阶段已相互占用，不参与批准量上限计算。
 * <p>
 * <b>事务边界：</b>
 * 确认收货事务中库存回库与退款执行为同一事务，退款失败不回滚库存回库（实物已入库）。
 * 退款重试只重试资金部分，不复增库存。
 */
@Service
public class ShopAfterSaleService {
    private static final String MONEY_PATTERN = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$";
    private final AfterSaleRequestMapper afterSaleMapper;
    private final OrderItemMapper itemMapper;
    private final OrderInfoMapper orderMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final WalletAccountMapper walletMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final SysUserMapper userMapper;
    private final AfterSaleService afterSaleService;
    private final CurrentUserService currentUser;
    private final ShopAccessService shopAccess;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;
    private MerchantSettlementPort merchantSettlement;
    private CouponRefundService couponRefunds;

    public ShopAfterSaleService(AfterSaleRequestMapper afterSaleMapper, OrderItemMapper itemMapper,
                                OrderInfoMapper orderMapper, InventoryStockMapper stockMapper,
                                InventoryTransactionMapper inventoryTransactionMapper,
                                WalletAccountMapper walletMapper, WalletTransactionMapper walletTransactionMapper,
                                SysUserMapper userMapper, AfterSaleService afterSaleService,
                                CurrentUserService currentUser, ShopAccessService shopAccess,
                                IdempotencyService idempotency, NumberGenerator numbers) {
        this.afterSaleMapper = afterSaleMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.stockMapper = stockMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.walletMapper = walletMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.userMapper = userMapper;
        this.afterSaleService = afterSaleService;
        this.currentUser = currentUser;
        this.shopAccess = shopAccess;
        this.idempotency = idempotency;
        this.numbers = numbers;
    }

    @Autowired(required = false)
    public void setMerchantSettlement(MerchantSettlementPort merchantSettlement) {
        this.merchantSettlement = merchantSettlement;
    }

    @Autowired(required = false)
    public void setCouponRefunds(CouponRefundService couponRefunds) {
        this.couponRefunds = couponRefunds;
    }

    // ══════════════════════════════════════════════════════════════
    // 列表 / 详情
    // ══════════════════════════════════════════════════════════════

    /**
     * 查询本店售后列表（分页）。
     * <p>
     * 通过 shopId 限定数据范围。支持按状态、退款状态、售后类型、关键词、时间范围筛选。
     * 关键词搜索同时匹配订单号和售后编号。
     * 鉴权：需要 {@code shop:after-sale:manage} 店铺权限。
     *
     * @param shopId       店铺 ID
     * @param status       售后状态筛选（可选）
     * @param refundStatus 退款状态筛选（可选）
     * @param requestType  售后类型筛选（可选）
     * @param keyword      关键词搜索（匹配订单号或售后编号，可选）
     * @param createdFrom  创建时间起始（可选，含）
     * @param createdTo    创建时间截止（可选，不含）
     * @param page         页码（从 1 开始）
     * @param pageSize     每页条数（1-100）
     * @return 分页商家端售后摘要列表（含买家信息）
     */
    public PageView<ShopAfterSaleSummaryView> list(long shopId, AfterSaleStatus status,
                                                    RefundStatus refundStatus, AfterSaleType requestType,
                                                    String keyword, LocalDateTime createdFrom, LocalDateTime createdTo,
                                                    long page, long pageSize) {
        shopAccess.require(shopId, "shop:after-sale:manage");
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }

        LambdaQueryWrapper<AfterSaleRequest> query = new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(status != null, AfterSaleRequest::getStatus, status)
                .eq(refundStatus != null, AfterSaleRequest::getRefundStatus, refundStatus)
                .eq(requestType != null, AfterSaleRequest::getRequestType, requestType)
                .ge(createdFrom != null, AfterSaleRequest::getCreatedAt, createdFrom)
                .lt(createdTo != null, AfterSaleRequest::getCreatedAt, createdTo)
                .orderByDesc(AfterSaleRequest::getCreatedAt).orderByDesc(AfterSaleRequest::getId);

        // 无论是否带关键词，主查询都必须先限定本店订单，防止售后号搜索跨店泄露。
        List<OrderInfo> shopOrders = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getShopId, shopId));
        if (shopOrders.isEmpty()) return PageView.of(new Page<>(page, pageSize), List.of());
        query.in(AfterSaleRequest::getOrderId, shopOrders.stream().map(OrderInfo::getId).toList());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            List<Long> matchingOrderIds = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                            .eq(OrderInfo::getShopId, shopId)
                            .like(OrderInfo::getOrderNo, kw))
                    .stream().map(OrderInfo::getId).toList();
            query.and(nested -> {
                nested.like(AfterSaleRequest::getAfterSaleNo, kw);
                if (!matchingOrderIds.isEmpty()) {
                    nested.or().in(AfterSaleRequest::getOrderId, matchingOrderIds);
                }
            });
        }

        Page<AfterSaleRequest> result = afterSaleMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(ar -> shopSummary(ar, shopId)).toList());
    }

    /**
     * 查询本店售后详情。
     * <p>
     * 需要校验售后所属订单确实属于当前店铺（双重校验：售后→订单→店铺）。
     * 鉴权：需要 {@code shop:after-sale:manage} 店铺权限。
     *
     * @param shopId      店铺 ID
     * @param afterSaleId 售后申请 ID
     * @return 商家端售后详情（在买家端详情基础上附加买家信息）
     */
    public ShopAfterSaleDetailView detail(long shopId, long afterSaleId) {
        shopAccess.require(shopId, "shop:after-sale:manage");
        AfterSaleRequest ar = scoped(shopId, afterSaleId);
        // 已完成店铺归属校验，此处使用内部映射，不能再次套用买家所有权校验。
        AfterSaleDetailView base = afterSaleService.detail(ar);
        return shopDetail(ar, base);
    }

    // ══════════════════════════════════════════════════════════════
    // 批准
    // ══════════════════════════════════════════════════════════════

    /**
     * 批准售后申请（幂等）。
     * <p>
     * <b>批准流程：</b>
     * <ol>
     *   <li>锁售后行(FOR UPDATE) + 锁订单明细(FOR UPDATE)</li>
     *   <li>校验 status=PENDING、version 匹配</li>
     *   <li>按批准口径重算额度：扣除其他 WAITING_RETURN/REFUNDING 申请已占用的批准量后复核</li>
     *   <li>写入审核信息（reviewerId、reviewComment、reviewedAt）</li>
     *   <li>仅退款（REFUND_ONLY）：立即执行退款 → 释放锁定库存 → 全额退款时取消子订单</li>
     *   <li>退货退款（RETURN_REFUND）：进入 WAITING_RETURN 状态等待买家退货，不执行退款</li>
     * </ol>
     * <p>
     * 退款执行失败时，售后状态保持 REFUNDING/FAILED，不滚回审核结果。
     * 鉴权：需要 {@code shop:after-sale:manage} 店铺权限。
     *
     * @param shopId      店铺 ID
     * @param afterSaleId 售后申请 ID
     * @param request     批准参数（批准数量、金额、审核意见、version）
     * @param key         Idempotency-Key 请求头值
     * @return 批准后的售后详情
     */
    @Transactional
    public ShopAfterSaleDetailView approve(long shopId, long afterSaleId,
                                            ApproveAfterSaleRequest request, String key) {
        shopAccess.require(shopId, "shop:after-sale:manage");
        long userId = currentUser.id();
        String path = "/api/shops/" + shopId + "/after-sales/" + afterSaleId + "/approve";
        return idempotency.execute(userId, "POST", path, key, request,
                ShopAfterSaleDetailView.class, () -> approveRequest(shopId, afterSaleId, request, key, userId));
    }

    /**
     * 批准的核心业务逻辑（幂等 action 回调）。
     */
    private ShopAfterSaleDetailView approveRequest(long shopId, long afterSaleId,
                                                    ApproveAfterSaleRequest request, String key, long operatorId) {
        // 1. 锁售后行，校验 PENDING 状态和 version
        AfterSaleRequest ar = scoped(shopId, afterSaleId, true);
        requireNoPendingAppeal(ar.getId());
        if (ar.getStatus() != AfterSaleStatus.PENDING) {
            throw BusinessException.conflict("AFTER_SALE_NOT_PENDING", "只有待处理的申请可以审核");
        }
        if (!request.version().equals(ar.getVersion())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "售后版本已变化");
        }

        // 2. 批准量不允许超过申请量
        if (request.approvedQuantity() > ar.getQuantity()) {
            throw BusinessException.conflict("AFTER_SALE_APPROVAL_EXCEEDED", "批准数量超过申请数量");
        }
        BigDecimal approvedAmt = parsePositiveMoney(request.approvedAmount(), "批准金额");
        if (approvedAmt.compareTo(ar.getRequestedAmount()) > 0) {
            throw BusinessException.conflict("AFTER_SALE_APPROVAL_EXCEEDED", "批准金额超过申请金额");
        }

        // 3. 批准口径：锁订单明细后，扣除其他 WAITING_RETURN/REFUNDING 申请已批未结的量后复核
        //    PENDING 申请不参与批准量上限计算（它们在创建时已相互占用）
        OrderItem item = itemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getId, ar.getOrderItemId()).last("FOR UPDATE"));
        List<AfterSaleRequest> activeOthers = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(AfterSaleRequest::getOrderItemId, ar.getOrderItemId())
                .in(AfterSaleRequest::getStatus, List.of(AfterSaleStatus.WAITING_RETURN, AfterSaleStatus.REFUNDING))
                .ne(AfterSaleRequest::getId, afterSaleId));

        int otherUsedQty = 0;
        BigDecimal otherUsedAmt = BigDecimal.ZERO;
        for (AfterSaleRequest o : activeOthers) {
            otherUsedQty += o.getApprovedQuantity() == null ? 0 : o.getApprovedQuantity();
            otherUsedAmt = otherUsedAmt.add(o.getApprovedAmount() == null ? BigDecimal.ZERO : o.getApprovedAmount());
        }

        // 最大可批准 = 购买量 - 已退量 - 其他已批未结占用量
        int refundedQty = item.getRefundedQuantity() == null ? 0 : item.getRefundedQuantity();
        BigDecimal refundedAmt = item.getRefundedAmount() == null ? BigDecimal.ZERO : item.getRefundedAmount();
        int maxQty = item.getQuantity() - refundedQty - otherUsedQty;
        BigDecimal maxAmt = item.getPayableAmount().subtract(refundedAmt).subtract(otherUsedAmt);

        if (request.approvedQuantity() > maxQty) {
            throw BusinessException.conflict("AFTER_SALE_APPROVAL_EXCEEDED", "批准数量超限");
        }
        if (approvedAmt.compareTo(maxAmt) > 0) {
            throw BusinessException.conflict("AFTER_SALE_APPROVAL_EXCEEDED", "批准金额超限");
        }

        // 4. 写入审核信息
        ar.setApprovedQuantity(request.approvedQuantity());
        ar.setApprovedAmount(approvedAmt);
        ar.setReviewerId(operatorId);
        ar.setReviewComment(request.reviewComment());
        ar.setReviewedAt(LocalDateTime.now());

        if (ar.getRequestType() == AfterSaleType.REFUND_ONLY) {
            // 仅退款：立即进入 REFUNDING 并执行退款+释放库存
            ar.setRefundNo(numbers.next("RF"));
            ar.setStatus(AfterSaleStatus.REFUNDING);
            ar.setRefundStatus(RefundStatus.PROCESSING);
            ar.setRefundFailureReason(null);
            afterSaleMapper.updateById(ar);
            boolean refunded = false;
            try {
                executeRefund(ar, item, operatorId);
                refunded = true;
            } catch (BusinessException e) {
                // 退款失败不滚回审核，标记 FAILED 等待重试
                ar.setRefundStatus(RefundStatus.FAILED);
                ar.setRefundFailureReason(e.getMessage());
                afterSaleMapper.updateById(ar);
            }
            if (refunded) {
                // 钱包和订单退款成功后再释放库存；技术异常直接回滚整个事务。
                releaseRefundOnlyStock(ar, item, operatorId);
            }
        } else {
            // 退货退款：进入等待退货状态，不执行退款
            ar.setStatus(AfterSaleStatus.WAITING_RETURN);
            ar.setRefundStatus(RefundStatus.NOT_STARTED);
            afterSaleMapper.updateById(ar);
        }

        return detail(shopId, afterSaleMapper.selectById(afterSaleId).getId());
    }

    // ══════════════════════════════════════════════════════════════
    // 拒绝
    // ══════════════════════════════════════════════════════════════

    /**
     * 拒绝售后申请。
     * <p>
     * 仅允许拒绝状态为 PENDING 的申请。拒绝后 approvedQuantity/approvedAmount 保持 null（符合数据库 CHECK 约束）。
     *
     * @param shopId      店铺 ID
     * @param afterSaleId 售后申请 ID
     * @param request     拒绝参数（拒绝原因、version）
     * @return 拒绝后的售后详情
     */
    @Transactional
    public ShopAfterSaleDetailView reject(long shopId, long afterSaleId,
                                           RejectAfterSaleRequest request) {
        shopAccess.require(shopId, "shop:after-sale:manage");
        long userId = currentUser.id();
        AfterSaleRequest ar = scoped(shopId, afterSaleId, true);
        requireNoPendingAppeal(ar.getId());
        if (ar.getStatus() != AfterSaleStatus.PENDING) {
            throw BusinessException.conflict("AFTER_SALE_NOT_PENDING", "只有待处理的申请可以审核");
        }
        if (!request.version().equals(ar.getVersion())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "售后版本已变化");
        }
        ar.setStatus(AfterSaleStatus.REJECTED);
        ar.setReviewerId(userId);
        ar.setReviewComment(request.reviewComment());
        ar.setReviewedAt(LocalDateTime.now());
        afterSaleMapper.updateById(ar);
        return detail(shopId, ar.getId());
    }

    // ══════════════════════════════════════════════════════════════
    // 确认退货收货
    // ══════════════════════════════════════════════════════════════

    /**
     * 确认退货收货并执行退款（幂等）。
     * <p>
     * <b>执行流程：</b>
     * <ol>
     *   <li>锁售后行，校验 WAITING_RETURN 状态 + 已提交物流 + 未确认收货 + version</li>
     *   <li>退货回库：stockMapper.returnStock() 增加可用库存 + 写 RETURN 库存流水</li>
     *   <li>写入 returnReceivedAt，状态改为 REFUNDING/PROCESSING</li>
     *   <li>执行退款（调用 {@link #executeRefund}）</li>
     * </ol>
     * <p>
     * <b>事务语义：</b>库存回库与退款在同一事务。退款失败时状态标记 FAILED，
     * 但已回库的库存不回滚（实物已入库），退款重试只处理资金部分。
     *
     * @param shopId      店铺 ID
     * @param afterSaleId 售后申请 ID
     * @param request     确认收货参数（备注、version）
     * @param key         Idempotency-Key 请求头值
     * @return 确认收货后的售后详情
     */
    @Transactional
    public ShopAfterSaleDetailView confirmReturnReceived(long shopId, long afterSaleId,
                                                          ConfirmReturnReceivedRequest request, String key) {
        shopAccess.require(shopId, "shop:after-sale:manage");
        long userId = currentUser.id();
        String path = "/api/shops/" + shopId + "/after-sales/" + afterSaleId + "/confirm-return-received";
        return idempotency.execute(userId, "POST", path, key, request,
                ShopAfterSaleDetailView.class, () -> confirmReturn(shopId, afterSaleId, request, key, userId));
    }

    /**
     * 确认收货的核心业务逻辑（幂等 action 回调）。
     */
    private ShopAfterSaleDetailView confirmReturn(long shopId, long afterSaleId,
                                                   ConfirmReturnReceivedRequest request, String key, long operatorId) {
        // 1. 锁售后行，多重状态校验
        AfterSaleRequest ar = scoped(shopId, afterSaleId, true);
        if (ar.getRequestType() != AfterSaleType.RETURN_REFUND) {
            throw BusinessException.conflict("RETURN_NOT_SHIPPED", "非退货退款申请");
        }
        if (ar.getStatus() != AfterSaleStatus.WAITING_RETURN) {
            throw BusinessException.conflict("RETURN_NOT_SHIPPED", "当前状态不允许确认收货");
        }
        if (ar.getReturnTrackingNo() == null) {
            throw BusinessException.conflict("RETURN_NOT_SHIPPED", "买家尚未提交退货物流");
        }
        if (ar.getReturnReceivedAt() != null) {
            throw BusinessException.conflict("RETURN_ALREADY_RECEIVED", "已确认收货");
        }
        if (!request.version().equals(ar.getVersion())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "售后版本已变化");
        }

        // 2. 退货回库：增加可用库存 + 写 RETURN 库存流水（businessType=AFTER_SALE, businessNo=afterSaleNo）
        OrderItem item = itemMapper.selectById(ar.getOrderItemId());
        if (stockMapper.returnStock(item.getSkuId(), ar.getApprovedQuantity()) != 1) {
            throw BusinessException.conflict("INVENTORY_INCONSISTENT", "退货库存不存在");
        }
        InventoryStock afterStock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, item.getSkuId()));
        InventoryTransaction invTx = new InventoryTransaction();
        invTx.setTransactionNo(numbers.next("IT"));
        invTx.setSkuId(item.getSkuId());
        invTx.setTransactionType(InventoryTransactionType.RETURN);
        invTx.setAvailableChange(ar.getApprovedQuantity());    // 可用库存增加
        invTx.setLockedChange(0);
        invTx.setAvailableAfter(afterStock.getAvailableQuantity());
        invTx.setLockedAfter(afterStock.getLockedQuantity());
        invTx.setBusinessType("AFTER_SALE");
        invTx.setBusinessNo(ar.getAfterSaleNo());
        invTx.setOperatorId(operatorId);
        invTx.setRemark(request.remark());
        inventoryTransactionMapper.insert(invTx);

        // 3. 写入确认收货时间，进入退款阶段
        ar.setReturnReceivedAt(LocalDateTime.now());
        ar.setRefundNo(numbers.next("RF"));
        ar.setStatus(AfterSaleStatus.REFUNDING);
        ar.setRefundStatus(RefundStatus.PROCESSING);
        ar.setRefundFailureReason(null);
        afterSaleMapper.updateById(ar);

        // 4. 执行退款（失败不滚回库存回库）
        try {
            executeRefund(ar, item, operatorId);
        } catch (BusinessException e) {
            ar.setRefundStatus(RefundStatus.FAILED);
            ar.setRefundFailureReason(e.getMessage());
            afterSaleMapper.updateById(ar);
        }

        return detail(shopId, ar.getId());
    }

    // ══════════════════════════════════════════════════════════════
    // 退款重试
    // ══════════════════════════════════════════════════════════════

    /**
     * 退款重试（幂等）。
     * <p>
     * 仅允许对 refundStatus=FAILED 且 status=REFUNDING 的售后申请重试。
     * 沿用原 refundNo，钱包流水业务唯一键（AFTER_SALE_REFUND + refundNo）保证不重复入账。
     * <p>
     * <b>重要：</b>退货退款的库存已在确认收货时回库，退款重试只重试资金和订单累计退款更新，
     * 不复增库存。仅退款的库存释放也使用幂等逻辑，同业务键不重复释放。
     *
     * @param shopId      店铺 ID
     * @param afterSaleId 售后申请 ID
     * @param request     重试参数（备注、version）
     * @param key         Idempotency-Key 请求头值
     * @return 重试后的售后详情
     */
    @Transactional
    public ShopAfterSaleDetailView retryRefund(long shopId, long afterSaleId,
                                                RetryRefundRequest request, String key) {
        shopAccess.require(shopId, "shop:after-sale:manage");
        long userId = currentUser.id();
        String path = "/api/shops/" + shopId + "/after-sales/" + afterSaleId + "/refund/retry";
        return idempotency.execute(userId, "POST", path, key, request,
                ShopAfterSaleDetailView.class, () -> retry(shopId, afterSaleId, request, userId));
    }

    /**
     * 定时任务使用的退款重试入口。
     *
     * <p>该入口不依赖当前登录用户，但仍通过售后行锁、状态和 version 校验保证安全；
     * 操作者沿用原审核人，避免系统任务伪造平台用户。</p>
     */
    @Transactional
    public boolean retryFailedRefund(long afterSaleId) {
        AfterSaleRequest ar = afterSaleMapper.selectById(afterSaleId);
        if (ar == null || ar.getStatus() != AfterSaleStatus.REFUNDING
                || ar.getRefundStatus() != RefundStatus.FAILED) {
            return false;
        }
        OrderInfo order = orderMapper.selectById(ar.getOrderId());
        if (order == null) return false;
        RetryRefundRequest request = new RetryRefundRequest("系统定时重试退款", ar.getVersion());
        retry(order.getShopId(), afterSaleId, request, ar.getReviewerId());
        return true;
    }

    /**
     * 重试的核心业务逻辑（幂等 action 回调）。
     */
    private ShopAfterSaleDetailView retry(long shopId, long afterSaleId,
                                           RetryRefundRequest request, long operatorId) {
        AfterSaleRequest ar = scoped(shopId, afterSaleId, true);
        if (ar.getRefundStatus() != RefundStatus.FAILED) {
            throw BusinessException.conflict("REFUND_NOT_RETRYABLE", "只有退款失败的申请可以重试");
        }
        if (ar.getStatus() != AfterSaleStatus.REFUNDING) {
            throw BusinessException.conflict("REFUND_NOT_RETRYABLE", "当前状态不允许退款重试");
        }
        if (!request.version().equals(ar.getVersion())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "售后版本已变化");
        }

        OrderItem item = itemMapper.selectById(ar.getOrderItemId());
        ar.setRefundStatus(RefundStatus.PROCESSING);
        // FAILED -> PROCESSING 时数据库要求失败原因同步清空。
        ar.setRefundFailureReason(null);
        afterSaleMapper.updateById(ar);

        boolean refunded = false;
        try {
            // 1. 执行退款（复用原 refundNo，钱包业务唯一键保证不重复）
            executeRefund(ar, item, operatorId);
            refunded = true;
        } catch (BusinessException e) {
            // 依然失败，标记 FAILED 等待下次重试
            ar.setRefundStatus(RefundStatus.FAILED);
            ar.setRefundFailureReason(e.getMessage());
            afterSaleMapper.updateById(ar);
        }
        // 2. 仅退款且未发货：成功后幂等释放库存；退货退款绝不重复回库。
        if (refunded && ar.getRequestType() == AfterSaleType.REFUND_ONLY) {
            releaseRefundOnlyStock(ar, item, operatorId);
        }

        AfterSaleRequest latest = afterSaleMapper.selectById(ar.getId());
        return shopDetail(latest, afterSaleService.detail(latest));
    }

    // ══════════════════════════════════════════════════════════════
    // 退款执行（核心）
    // ══════════════════════════════════════════════════════════════

    /**
     * 执行钱包退款 + 更新累计退款 + 写入钱包流水。
     * <p>
     * <b>执行步骤：</b>
     * <ol>
     *   <li>首次退款生成 refundNo（格式 RF...），重试复用已有 refunNo</li>
     *   <li>幂等检查：同 refundNo 的 AFTER_SALE_REFUND 流水已存在 → 直接标记成功返回（不重复入账）</li>
     *   <li>FOR UPDATE 锁钱包 → credit 入账</li>
     *   <li>写入钱包流水（transactionType=REFUND, direction=CREDIT, businessType=AFTER_SALE_REFUND）</li>
     *   <li>订单明细累计退款更新（refundedQuantity + approvedQuantity, refundedAmount + approvedAmount）</li>
     *   <li>子订单累计退款更新 + 支付状态更新（PARTIALLY_REFUNDED / REFUNDED）</li>
     *   <li>标记售后 COMPLETED/SUCCESS</li>
     * </ol>
     *
     * @param ar         售后实体（已加锁）
     * @param item       订单明细
     * @param operatorId 操作者 ID
     */
    private void executeRefund(AfterSaleRequest ar, OrderItem item, long operatorId) {
        // 首次退款生成退款编号，重试复用已有编号
        if (ar.getRefundNo() == null) {
            ar.setRefundNo(numbers.next("RF"));
        }

        // 幂等保护：同退款编号不重复执行
        boolean alreadyRefunded = walletTransactionMapper.exists(new LambdaQueryWrapper<WalletTransaction>()
                .eq(WalletTransaction::getBusinessNo, ar.getRefundNo())
                .eq(WalletTransaction::getBusinessType, "AFTER_SALE_REFUND"));
        if (alreadyRefunded) {
            ar.setRefundStatus(RefundStatus.SUCCESS);
            ar.setStatus(AfterSaleStatus.COMPLETED);
            ar.setRefundFailureReason(null);
            if (ar.getRefundedAt() == null) ar.setRefundedAt(LocalDateTime.now());
            ar.setCompletedAt(LocalDateTime.now());
            afterSaleMapper.updateById(ar);
            return;
        }

        // 资金变更前锁定子订单并预校验累计退款，避免并发退款超过订单实付金额。
        OrderInfo order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getId, ar.getOrderId()).last("FOR UPDATE"));
        if (order == null) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "子订单不存在");
        }
        BigDecimal prospectiveRefund = (order.getRefundAmount() == null
                ? BigDecimal.ZERO : order.getRefundAmount()).add(ar.getApprovedAmount());
        if (prospectiveRefund.compareTo(order.getPayableAmount()) > 0) {
            throw BusinessException.conflict("AFTER_SALE_APPROVAL_EXCEEDED", "子订单累计退款金额超限");
        }

        // 锁钱包 + 入账
        WalletAccount wallet = walletMapper.selectOne(new LambdaQueryWrapper<WalletAccount>()
                .eq(WalletAccount::getUserId, ar.getUserId()).last("FOR UPDATE"));
        if (wallet == null) {
            throw BusinessException.unprocessable("WALLET_UNAVAILABLE", "钱包不存在");
        }
        // 先校验买家钱包状态，再进入商家资金锁，避免商家结算已冲回而买家退款无法入账。
        if (wallet.getStatus() != org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus.ACTIVE) {
            throw BusinessException.unprocessable("WALLET_UNAVAILABLE", "钱包不可用");
        }
        BigDecimal platformSubsidyReversal = couponRefunds == null ? BigDecimal.ZERO
                : couponRefunds.preview(item, ar.getApprovedQuantity()).platformSubsidyReversal();
        if (merchantSettlement != null
                && !merchantSettlement.recordMerchantRefund(order, ar.getApprovedAmount(),
                platformSubsidyReversal, ar.getRefundNo(), operatorId)) {
            throw BusinessException.unprocessable("MERCHANT_WALLET_REFUND_INSUFFICIENT", "商家可冲回余额不足，需平台人工追缴");
        }
        BigDecimal before = wallet.getBalance();
        // credit() 内部有 status='ACTIVE' 条件，非活跃钱包返回 0
        if (walletMapper.credit(ar.getUserId(), ar.getApprovedAmount()) != 1) {
            throw BusinessException.unprocessable("WALLET_UNAVAILABLE", "钱包不可用");
        }
        WalletAccount after = walletMapper.selectById(wallet.getId());

        // 写钱包流水（不可变）
        WalletTransaction wtx = new WalletTransaction();
        wtx.setTransactionNo(numbers.next("WT"));
        wtx.setWalletId(wallet.getId());
        wtx.setTransactionType(WalletTransactionType.REFUND);
        wtx.setDirection(TransactionDirection.CREDIT);
        wtx.setAmount(ar.getApprovedAmount());
        wtx.setBalanceBefore(before);
        wtx.setBalanceAfter(after.getBalance());
        wtx.setBusinessType("AFTER_SALE_REFUND");
        wtx.setBusinessNo(ar.getRefundNo());
        wtx.setOperatorId(operatorId);
        if (walletTransactionMapper.insert(wtx) != 1) {
            throw new IllegalStateException("退款钱包流水写入失败");
        }

        // 更新订单明细累计退款
        item.setRefundedQuantity((item.getRefundedQuantity() == null ? 0 : item.getRefundedQuantity()) + ar.getApprovedQuantity());
        item.setRefundedAmount((item.getRefundedAmount() == null ? BigDecimal.ZERO : item.getRefundedAmount()).add(ar.getApprovedAmount()));
        if (itemMapper.updateById(item) != 1) {
            throw new IllegalStateException("订单明细累计退款更新失败");
        }

        // 同一次 UPDATE 内同步退款金额、支付状态和必要的订单状态，满足数据库 CHECK 约束。
        applyOrderRefund(order, ar.getApprovedAmount());
        if (orderMapper.updateById(order) != 1) {
            throw new IllegalStateException("子订单累计退款更新失败");
        }

        if (couponRefunds != null) couponRefunds.recordSuccessfulRefund(ar, item);

        // 标记售后完成
        ar.setRefundStatus(RefundStatus.SUCCESS);
        ar.setRefundFailureReason(null);
        ar.setRefundedAt(LocalDateTime.now());
        ar.setStatus(AfterSaleStatus.COMPLETED);
        ar.setCompletedAt(LocalDateTime.now());
        afterSaleMapper.updateById(ar);
    }

    private void applyOrderRefund(OrderInfo order, BigDecimal amount) {
        BigDecimal refunded = (order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount())
                .add(amount);
        if (refunded.compareTo(order.getPayableAmount()) > 0) {
            throw BusinessException.conflict("AFTER_SALE_APPROVAL_EXCEEDED", "子订单累计退款金额超限");
        }
        order.setRefundAmount(refunded);
        if (refunded.compareTo(order.getPayableAmount()) == 0) {
            order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
            if (order.getOrderStatus() == OrderStatus.PENDING_SHIPMENT) {
                order.setOrderStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
            }
        } else {
            order.setPaymentStatus(OrderPaymentStatus.PARTIALLY_REFUNDED);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 库存操作
    // ══════════════════════════════════════════════════════════════

    /**
     * 仅退款时释放锁定库存。
     * <p>
     * 仅对 reservationStatus=LOCKED 的订单明细执行释放。
     * 已经发货（DEDUCTED）或已释放（RELEASED）的不重复操作。
     * 写 RELEASE 库存流水。
     */
    private void releaseRefundOnlyStock(AfterSaleRequest ar, OrderItem item, long operatorId) {
        if (item.getReservationStatus() == ReservationStatus.LOCKED) {
            boolean released = inventoryTransactionMapper.exists(
                    new LambdaQueryWrapper<InventoryTransaction>()
                            .eq(InventoryTransaction::getSkuId, item.getSkuId())
                            .eq(InventoryTransaction::getTransactionType, InventoryTransactionType.RELEASE)
                            .eq(InventoryTransaction::getBusinessType, "AFTER_SALE")
                            .eq(InventoryTransaction::getBusinessNo, ar.getAfterSaleNo()));
            if (released) return;
            // release: available+1, locked-1
            if (stockMapper.release(item.getSkuId(), ar.getApprovedQuantity()) != 1) {
                throw BusinessException.conflict("INVENTORY_INCONSISTENT", "锁定库存不足，无法释放");
            }
            InventoryStock afterStock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                    .eq(InventoryStock::getSkuId, item.getSkuId()));
            InventoryTransaction invTx = new InventoryTransaction();
            invTx.setTransactionNo(numbers.next("IT"));
            invTx.setSkuId(item.getSkuId());
            invTx.setTransactionType(InventoryTransactionType.RELEASE);
            invTx.setAvailableChange(ar.getApprovedQuantity());
            invTx.setLockedChange(-ar.getApprovedQuantity());
            invTx.setAvailableAfter(afterStock.getAvailableQuantity());
            invTx.setLockedAfter(afterStock.getLockedQuantity());
            invTx.setBusinessType("AFTER_SALE");
            invTx.setBusinessNo(ar.getAfterSaleNo());
            invTx.setOperatorId(operatorId);
            if (inventoryTransactionMapper.insert(invTx) != 1) {
                throw new IllegalStateException("售后库存流水写入失败");
            }
            if (item.getRefundedQuantity() >= item.getQuantity()) {
                item.setReservationStatus(ReservationStatus.RELEASED);
                if (itemMapper.updateById(item) != 1) {
                    throw new IllegalStateException("订单明细库存状态更新失败");
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 店铺范围校验与视图映射
    // ══════════════════════════════════════════════════════════════

    /**
     * 校验售后申请是否属于指定店铺。
     * <p>
     * 双重校验：售后 → 订单 → 店铺。防止跨店越权访问。
     * lock=true 时对售后行加 FOR UPDATE。
     */
    private AfterSaleRequest scoped(long shopId, long afterSaleId, boolean lock) {
        AfterSaleRequest ar = afterSaleMapper.selectById(afterSaleId);
        if (ar == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "售后申请不存在");
        OrderInfo order = orderMapper.selectById(ar.getOrderId());
        if (order == null || order.getShopId() != shopId) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "售后申请不存在");
        }
        if (lock) {
            ar = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSaleRequest>()
                    .eq(AfterSaleRequest::getId, afterSaleId).last("FOR UPDATE"));
        }
        return ar;
    }

    private AfterSaleRequest scoped(long shopId, long afterSaleId) {
        return scoped(shopId, afterSaleId, false);
    }

    /**
     * 构建商家端列表摘要（在买家端基础上附加买家身份信息）。
     */
    private ShopAfterSaleSummaryView shopSummary(AfterSaleRequest ar, long shopId) {
        AfterSaleSummaryView base = afterSaleService.summary(ar);
        return new ShopAfterSaleSummaryView(
                base.id(), base.afterSaleNo(), base.requestType(), base.status(), base.refundStatus(),
                base.order(), base.shop(), base.item(), base.quantity(), base.requestedAmount(),
                base.approvedAmount(), base.createdAt(), base.updatedAt(), buyer(ar.getUserId()));
    }

    /**
     * 构建商家端详情（在买家端基础上附加买家身份信息）。
     */
    private ShopAfterSaleDetailView shopDetail(AfterSaleRequest ar, AfterSaleDetailView base) {
        return new ShopAfterSaleDetailView(
                base.id(), base.afterSaleNo(), base.requestType(), base.status(), base.refundStatus(),
                base.order(), base.shop(), base.item(), base.quantity(), base.reasonCode(),
                base.reasonDescription(), base.evidenceUrls(), base.requestedAmount(),
                base.approvedQuantity(), base.approvedAmount(), base.review(),
                base.returnShipment(), base.appeal(), base.refundNo(), base.refundFailureReason(),
                base.refundedAt(), base.completedAt(), base.cancelledAt(), base.version(),
                base.createdAt(), base.updatedAt(), base.availableActions(), buyer(ar.getUserId()),
                afterSaleService.eligibilityAtReview(ar));
    }

    private UserSummary buyer(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new IllegalStateException("售后申请关联买家不存在");
        return IdentityViewMapper.user(user);
    }

    private BigDecimal parsePositiveMoney(String raw, String field) {
        if (raw == null || !raw.matches(MONEY_PATTERN)) {
            throw BusinessException.badRequest("VALIDATION_FAILED", field + "必须为两位小数字符串");
        }
        BigDecimal value = new BigDecimal(raw);
        if (value.signum() <= 0) {
            throw BusinessException.badRequest("VALIDATION_FAILED", field + "必须大于 0.00");
        }
        return value;
    }

    private void requireNoPendingAppeal(long afterSaleId) {
        if (afterSaleMapper.existsPendingAppeal(afterSaleId)) {
            throw BusinessException.conflict("APPEAL_NOT_DECIDABLE", "售后申诉待平台裁决，商家不能继续审核");
        }
    }

    @Transactional
    public void executePlatformRefund(long shopId, long afterSaleId, long operatorId) {
        AfterSaleRequest ar = scoped(shopId, afterSaleId, true);
        if (ar.getRequestType() != AfterSaleType.REFUND_ONLY || ar.getStatus() != AfterSaleStatus.REFUNDING) {
            throw BusinessException.conflict("AFTER_SALE_NOT_PENDING", "平台批准的售后不可执行仅退款");
        }
        OrderItem item = itemMapper.selectById(ar.getOrderItemId());
        try {
            executeRefund(ar, item, operatorId);
            releaseRefundOnlyStock(ar, item, operatorId);
        } catch (BusinessException ex) {
            ar.setRefundStatus(RefundStatus.FAILED);
            ar.setRefundFailureReason(ex.getMessage());
            afterSaleMapper.updateById(ar);
        }
    }
}
