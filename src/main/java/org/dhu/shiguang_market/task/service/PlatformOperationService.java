package org.dhu.shiguang_market.task.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleSummaryView;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.aftersale.service.AfterSaleService;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.PaymentOrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderSummaryView;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.order.service.OrderViewService;
import org.dhu.shiguang_market.payment.mapper.PaymentOrderMapper;
import org.dhu.shiguang_market.payment.mapper.WalletTransactionMapper;
import org.dhu.shiguang_market.payment.model.PaymentOrder;
import org.dhu.shiguang_market.payment.model.WalletTransaction;
import org.dhu.shiguang_market.task.dto.OperationDtos.BusinessTraceLink;
import org.dhu.shiguang_market.task.dto.OperationDtos.BusinessTraceView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationAfterSaleView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationOrderDetailView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationOrderView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationPaymentView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationTradeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台运营只读查询服务。
 * <p>复用订单和售后已有视图映射，保持字段口径一致；该服务不提供任何状态修改能力。</p>
 */
@Service
@Transactional(readOnly = true)
public class PlatformOperationService {
    private final TradeOrderMapper tradeMapper;
    private final OrderInfoMapper orderMapper;
    private final PaymentOrderMapper paymentMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final SysUserMapper userMapper;
    private final OrderViewService orderViews;
    private final AfterSaleService afterSaleService;

    public PlatformOperationService(TradeOrderMapper tradeMapper, OrderInfoMapper orderMapper,
                                    PaymentOrderMapper paymentMapper, AfterSaleRequestMapper afterSaleMapper,
                                    InventoryTransactionMapper inventoryTransactionMapper,
                                    WalletTransactionMapper walletTransactionMapper, SysUserMapper userMapper,
                                    OrderViewService orderViews, AfterSaleService afterSaleService) {
        this.tradeMapper = tradeMapper;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.afterSaleMapper = afterSaleMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.userMapper = userMapper;
        this.orderViews = orderViews;
        this.afterSaleService = afterSaleService;
    }

    /** 分页查询主交易，支持按编号、买家、状态和创建时间筛选。 */
    public PageView<OperationTradeView> trades(String tradeNo, Long userId, TradeStatus status,
                                                LocalDateTime createdFrom, LocalDateTime createdTo,
                                                long page, long pageSize) {
        checkPage(page, pageSize);
        LambdaQueryWrapper<TradeOrder> query = new LambdaQueryWrapper<TradeOrder>()
                .eq(hasText(tradeNo), TradeOrder::getTradeNo, trim(tradeNo))
                .eq(userId != null, TradeOrder::getUserId, userId)
                .eq(status != null, TradeOrder::getTradeStatus, status)
                .ge(createdFrom != null, TradeOrder::getCreatedAt, createdFrom)
                .lt(createdTo != null, TradeOrder::getCreatedAt, createdTo)
                .orderByDesc(TradeOrder::getCreatedAt).orderByDesc(TradeOrder::getId);
        Page<TradeOrder> result = tradeMapper.selectPage(Page.of(page, pageSize), query);
        List<OperationTradeView> items = result.getRecords().stream().map(this::tradeView).toList();
        return PageView.of(result, items);
    }

    /** 分页查询子订单，展示字段复用普通订单摘要，并补充脱敏后的买家摘要。 */
    public PageView<OperationOrderView> orders(String orderNo, Long shopId, Long userId,
                                                OrderStatus orderStatus, OrderPaymentStatus paymentStatus,
                                                long page, long pageSize) {
        checkPage(page, pageSize);
        LambdaQueryWrapper<OrderInfo> query = new LambdaQueryWrapper<OrderInfo>()
                .eq(hasText(orderNo), OrderInfo::getOrderNo, trim(orderNo))
                .eq(shopId != null, OrderInfo::getShopId, shopId)
                .eq(userId != null, OrderInfo::getUserId, userId)
                .eq(orderStatus != null, OrderInfo::getOrderStatus, orderStatus)
                .eq(paymentStatus != null, OrderInfo::getPaymentStatus, paymentStatus)
                .orderByDesc(OrderInfo::getCreatedAt).orderByDesc(OrderInfo::getId);
        Page<OrderInfo> result = orderMapper.selectPage(Page.of(page, pageSize), query);
        List<OperationOrderView> items = result.getRecords().stream().map(this::orderView).toList();
        return PageView.of(result, items);
    }

    /** 查询平台订单详情，只组合运营页面需要的安全字段。 */
    public OperationOrderDetailView orderDetail(long orderId) {
        if (orderId < 1) {
            throw BusinessException.badRequest("BAD_REQUEST", "订单 ID 必须为正整数");
        }
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "订单不存在");
        }
        TradeOrder trade = tradeMapper.selectById(order.getTradeId());
        var buyer = userMapper.selectById(order.getUserId());
        if (trade == null || buyer == null) {
            throw new IllegalStateException("Order references missing trade or buyer data");
        }
        return new OperationOrderDetailView(
                id(order.getId()), order.getOrderNo(), id(order.getTradeId()), trade.getTradeNo(),
                orderViews.shop(order), IdentityViewMapper.user(buyer),
                order.getOrderStatus(), orderViews.displayStatus(order), order.getPaymentStatus(),
                money(order.getItemAmount()), money(order.getFreightAmount()), money(order.getPayableAmount()),
                money(order.getRefundAmount()), time(order.getCreatedAt()), time(trade.getPayExpireAt()),
                time(trade.getPaidAt()), time(order.getCompletedAt()), time(order.getCancelledAt()),
                orderViews.shipping(order), orderViews.items(order.getId()), orderViews.history(order.getId()));
    }

    /** 分页查询支付单；tradeNo 先精确定位主交易，避免编写复杂联表 SQL。 */
    public PageView<OperationPaymentView> payments(String paymentNo, String tradeNo,
                                                    PaymentOrderStatus status, long page, long pageSize) {
        checkPage(page, pageSize);
        Long tradeId = null;
        if (hasText(tradeNo)) {
            TradeOrder trade = findTrade(trim(tradeNo));
            if (trade == null) return empty(page, pageSize);
            tradeId = trade.getId();
        }
        LambdaQueryWrapper<PaymentOrder> query = new LambdaQueryWrapper<PaymentOrder>()
                .eq(hasText(paymentNo), PaymentOrder::getPaymentNo, trim(paymentNo))
                .eq(tradeId != null, PaymentOrder::getTradeId, tradeId)
                .eq(status != null, PaymentOrder::getStatus, status)
                .orderByDesc(PaymentOrder::getCreatedAt).orderByDesc(PaymentOrder::getId);
        Page<PaymentOrder> result = paymentMapper.selectPage(Page.of(page, pageSize), query);
        List<OperationPaymentView> items = result.getRecords().stream().map(this::paymentView).toList();
        return PageView.of(result, items);
    }

    /** 分页查询售后申请，店铺条件通过子订单 ID 范围过滤。 */
    public PageView<OperationAfterSaleView> afterSales(String afterSaleNo, Long shopId, Long userId,
                                                       AfterSaleStatus status, RefundStatus refundStatus,
                                                       long page, long pageSize) {
        checkPage(page, pageSize);
        List<Long> shopOrderIds = null;
        if (shopId != null) {
            shopOrderIds = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                            .eq(OrderInfo::getShopId, shopId).select(OrderInfo::getId))
                    .stream().map(OrderInfo::getId).toList();
            if (shopOrderIds.isEmpty()) return empty(page, pageSize);
        }
        LambdaQueryWrapper<AfterSaleRequest> query = new LambdaQueryWrapper<AfterSaleRequest>()
                .eq(hasText(afterSaleNo), AfterSaleRequest::getAfterSaleNo, trim(afterSaleNo))
                .eq(userId != null, AfterSaleRequest::getUserId, userId)
                .eq(status != null, AfterSaleRequest::getStatus, status)
                .eq(refundStatus != null, AfterSaleRequest::getRefundStatus, refundStatus)
                .in(shopOrderIds != null, AfterSaleRequest::getOrderId, shopOrderIds)
                .orderByDesc(AfterSaleRequest::getCreatedAt).orderByDesc(AfterSaleRequest::getId);
        Page<AfterSaleRequest> result = afterSaleMapper.selectPage(Page.of(page, pageSize), query);
        List<OperationAfterSaleView> items = result.getRecords().stream().map(this::afterSaleView).toList();
        return PageView.of(result, items);
    }

    /**
     * 按任一业务编号串联交易、订单、支付、售后及资金/库存流水。
     * 核心逻辑只做精确编号查询和关联 ID 扩展，便于排障且保持实现直观。
     */
    public BusinessTraceView trace(String businessType, String businessNo) {
        String type = requireText(businessType, "businessType").toUpperCase(Locale.ROOT);
        String number = requireText(businessNo, "businessNo");

        TradeOrder trade = null;
        OrderInfo directOrder = null;
        PaymentOrder directPayment = null;
        AfterSaleRequest directAfterSale = null;
        // 既支持资源自身编号，也支持从库存/钱包流水携带的业务编号反向追踪。
        switch (type) {
            case "TRADE" -> trade = findTrade(number);
            case "ORDER" -> directOrder = findOrder(number);
            case "PAYMENT", "TRADE_PAYMENT" -> directPayment = findPayment(number);
            case "AFTER_SALE", "AFTER_SALE_REFUND" -> directAfterSale = findAfterSale(number);
            case "ORDER_SHIPMENT" -> directOrder = findOrder(beforeSuffix(number, "-SHIP-"));
            case "TRADE_TIMEOUT" -> trade = findTrade(beforeSuffix(number, "-RELEASE-"));
            default -> {
                // 手工调整等独立流水没有上游交易，后续仍会按原业务编号返回流水摘要。
            }
        }

        if (directPayment != null) trade = tradeMapper.selectById(directPayment.getTradeId());
        if (directAfterSale != null) directOrder = orderMapper.selectById(directAfterSale.getOrderId());
        if (directOrder != null) trade = tradeMapper.selectById(directOrder.getTradeId());

        List<OrderInfo> orders = trade == null ? new ArrayList<>()
                : new ArrayList<>(orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getTradeId, trade.getId()).orderByAsc(OrderInfo::getId)));
        addIfMissing(orders, directOrder, OrderInfo::getId);

        List<PaymentOrder> payments = trade == null ? new ArrayList<>()
                : new ArrayList<>(paymentMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getTradeId, trade.getId()).orderByAsc(PaymentOrder::getId)));
        addIfMissing(payments, directPayment, PaymentOrder::getId);

        List<Long> orderIds = orders.stream().map(OrderInfo::getId).toList();
        List<AfterSaleRequest> afterSales = orderIds.isEmpty() ? new ArrayList<>()
                : new ArrayList<>(afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSaleRequest>()
                .in(AfterSaleRequest::getOrderId, orderIds).orderByAsc(AfterSaleRequest::getId)));
        addIfMissing(afterSales, directAfterSale, AfterSaleRequest::getId);

        List<String> relatedNumbers = new ArrayList<>();
        relatedNumbers.add(number);
        if (trade != null) relatedNumbers.add(trade.getTradeNo());
        relatedNumbers.addAll(orders.stream().map(OrderInfo::getOrderNo).toList());
        relatedNumbers.addAll(payments.stream().map(PaymentOrder::getPaymentNo).toList());
        relatedNumbers.addAll(afterSales.stream().map(AfterSaleRequest::getAfterSaleNo).toList());
        relatedNumbers.addAll(afterSales.stream().map(AfterSaleRequest::getRefundNo)
                .filter(value -> value != null && !value.isBlank()).toList());
        relatedNumbers = relatedNumbers.stream().distinct().toList();

        List<InventoryTransaction> inventory = inventoryTransactionMapper.selectList(
                inventoryQuery(relatedNumbers));
        List<WalletTransaction> wallets = walletTransactionMapper.selectList(walletQuery(relatedNumbers));

        return new BusinessTraceView(type, number, trade == null ? null : link(trade),
                orders.stream().map(this::link).toList(), payments.stream().map(this::link).toList(),
                afterSales.stream().map(this::link).toList(), inventory.stream().map(this::link).toList(),
                wallets.stream().map(this::link).toList());
    }

    private OperationTradeView tradeView(TradeOrder trade) {
        long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getTradeId, trade.getId()));
        return new OperationTradeView(id(trade.getId()), trade.getTradeNo(),
                IdentityViewMapper.user(userMapper.selectById(trade.getUserId())), trade.getTradeStatus(),
                money(trade.getPayableAmount()), Math.toIntExact(orderCount), time(trade.getCreatedAt()));
    }

    private OperationOrderView orderView(OrderInfo order) {
        OrderSummaryView view = orderViews.summary(order);
        return new OperationOrderView(view.id(), view.orderNo(), view.tradeId(), view.tradeNo(), view.shop(),
                view.orderStatus(), view.paymentStatus(), view.payableAmount(), view.refundAmount(),
                view.itemSummary(), view.itemKinds(), view.totalQuantity(), view.createdAt(), view.availableActions(),
                IdentityViewMapper.user(userMapper.selectById(order.getUserId())));
    }

    private OperationPaymentView paymentView(PaymentOrder payment) {
        TradeOrder trade = tradeMapper.selectById(payment.getTradeId());
        return new OperationPaymentView(id(payment.getId()), payment.getPaymentNo(), id(payment.getTradeId()),
                money(payment.getAmount()), payment.getStatus(), payment.getFailureReason(), time(payment.getPaidAt()),
                time(payment.getExpiresAt()), time(payment.getCreatedAt()), time(payment.getUpdatedAt()),
                trade.getTradeNo(), IdentityViewMapper.user(userMapper.selectById(trade.getUserId())));
    }

    private OperationAfterSaleView afterSaleView(AfterSaleRequest afterSale) {
        AfterSaleSummaryView view = afterSaleService.summaryForOperation(afterSale);
        return new OperationAfterSaleView(view.id(), view.afterSaleNo(), view.requestType(), view.status(),
                view.refundStatus(), view.order(), view.shop(), view.item(), view.quantity(), view.requestedAmount(),
                view.approvedAmount(), view.createdAt(), view.updatedAt(),
                IdentityViewMapper.user(userMapper.selectById(afterSale.getUserId())));
    }

    private TradeOrder findTrade(String tradeNo) {
        return tradeMapper.selectOne(new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getTradeNo, tradeNo));
    }

    private OrderInfo findOrder(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo));
    }

    private PaymentOrder findPayment(String paymentNo) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentNo, paymentNo));
    }

    private AfterSaleRequest findAfterSale(String businessNo) {
        return afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSaleRequest>()
                .and(query -> query.eq(AfterSaleRequest::getAfterSaleNo, businessNo)
                        .or().eq(AfterSaleRequest::getRefundNo, businessNo)));
    }

    /** 精确编号和“编号-动作-明细”前缀都纳入追踪，兼容现有发货/释放库存流水。 */
    private LambdaQueryWrapper<InventoryTransaction> inventoryQuery(List<String> businessNumbers) {
        LambdaQueryWrapper<InventoryTransaction> query = new LambdaQueryWrapper<>();
        query.and(group -> {
            for (int i = 0; i < businessNumbers.size(); i++) {
                String number = businessNumbers.get(i);
                if (i == 0) {
                    group.eq(InventoryTransaction::getBusinessNo, number)
                            .or().likeRight(InventoryTransaction::getBusinessNo, number + "-");
                } else {
                    group.or(part -> part.eq(InventoryTransaction::getBusinessNo, number)
                            .or().likeRight(InventoryTransaction::getBusinessNo, number + "-"));
                }
            }
        });
        return query.orderByAsc(InventoryTransaction::getId);
    }

    private LambdaQueryWrapper<WalletTransaction> walletQuery(List<String> businessNumbers) {
        LambdaQueryWrapper<WalletTransaction> query = new LambdaQueryWrapper<>();
        query.and(group -> {
            for (int i = 0; i < businessNumbers.size(); i++) {
                String number = businessNumbers.get(i);
                if (i == 0) {
                    group.eq(WalletTransaction::getBusinessNo, number)
                            .or().likeRight(WalletTransaction::getBusinessNo, number + "-");
                } else {
                    group.or(part -> part.eq(WalletTransaction::getBusinessNo, number)
                            .or().likeRight(WalletTransaction::getBusinessNo, number + "-"));
                }
            }
        });
        return query.orderByAsc(WalletTransaction::getId);
    }

    private String beforeSuffix(String value, String separator) {
        int index = value.indexOf(separator);
        return index < 0 ? value : value.substring(0, index);
    }

    private BusinessTraceLink link(TradeOrder value) {
        return new BusinessTraceLink("TRADE", id(value.getId()), value.getTradeNo(),
                value.getTradeStatus().name(), time(value.getCreatedAt()));
    }

    private BusinessTraceLink link(OrderInfo value) {
        return new BusinessTraceLink("ORDER", id(value.getId()), value.getOrderNo(),
                value.getOrderStatus().name(), time(value.getCreatedAt()));
    }

    private BusinessTraceLink link(PaymentOrder value) {
        return new BusinessTraceLink("PAYMENT", id(value.getId()), value.getPaymentNo(),
                value.getStatus().name(), time(value.getCreatedAt()));
    }

    private BusinessTraceLink link(AfterSaleRequest value) {
        return new BusinessTraceLink("AFTER_SALE", id(value.getId()), value.getAfterSaleNo(),
                value.getStatus().name(), time(value.getCreatedAt()));
    }

    private BusinessTraceLink link(InventoryTransaction value) {
        return new BusinessTraceLink("INVENTORY_TRANSACTION", id(value.getId()), value.getTransactionNo(),
                value.getTransactionType().name(), time(value.getCreatedAt()));
    }

    private BusinessTraceLink link(WalletTransaction value) {
        return new BusinessTraceLink("WALLET_TRANSACTION", id(value.getId()), value.getTransactionNo(),
                value.getTransactionType().name(), time(value.getCreatedAt()));
    }

    private void checkPage(long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
    }

    private String requireText(String value, String field) {
        String text = trim(value);
        if (text == null) throw BusinessException.badRequest("BAD_REQUEST", field + "不能为空");
        return text;
    }

    private boolean hasText(String value) {
        return trim(value) != null;
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private <T> PageView<T> empty(long page, long pageSize) {
        return new PageView<>(List.of(), page, pageSize, 0, 0);
    }

    private <T> void addIfMissing(List<T> values, T candidate,
                                  java.util.function.Function<T, Long> idGetter) {
        if (candidate != null && values.stream().noneMatch(value -> idGetter.apply(value).equals(idGetter.apply(candidate)))) {
            values.add(candidate);
        }
    }
}
