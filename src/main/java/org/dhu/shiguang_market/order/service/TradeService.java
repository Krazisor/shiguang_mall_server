package org.dhu.shiguang_market.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.cart.dto.CartDtos.CreateTradeRequest;
import org.dhu.shiguang_market.cart.model.CartItem;
import org.dhu.shiguang_market.cart.service.CartService;
import org.dhu.shiguang_market.cart.service.CartService.CheckoutLine;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.address.model.UserAddress;
import org.dhu.shiguang_market.address.service.AddressService;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.order.dto.OrderDtos.CancelTradeRequest;
import org.dhu.shiguang_market.order.dto.OrderDtos.TradeDetailView;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.order.model.OrderStatusHistory;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.coupon.service.CouponQuoteService;
import org.dhu.shiguang_market.coupon.service.CouponQuoteService.QuoteResult;
import org.dhu.shiguang_market.coupon.service.CouponReservationService;
import org.dhu.shiguang_market.coupon.service.CouponEligibilityService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeService {
    private final TradeOrderMapper tradeMapper;
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderStatusHistoryMapper historyMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final CartService cartService;
    private final AddressService addressService;
    private final CurrentUserService currentUser;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;
    private final OrderViewService views;
    private final long timeoutMinutes;
    private CouponQuoteService couponQuotes;
    private CouponReservationService couponReservations;
    private CouponEligibilityService couponEligibility;

    public TradeService(TradeOrderMapper tradeMapper, OrderInfoMapper orderMapper,
                        OrderItemMapper itemMapper, OrderStatusHistoryMapper historyMapper,
                        AfterSaleRequestMapper afterSaleMapper,
                        InventoryStockMapper stockMapper, InventoryTransactionMapper inventoryTransactionMapper,
                        CartService cartService, AddressService addressService,
                        CurrentUserService currentUser, IdempotencyService idempotency,
                        NumberGenerator numbers, OrderViewService views,
                        @Value("${market.trade.payment-timeout-minutes:30}") long timeoutMinutes) {
        this.tradeMapper = tradeMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.historyMapper = historyMapper;
        this.afterSaleMapper = afterSaleMapper;
        this.stockMapper = stockMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.cartService = cartService;
        this.addressService = addressService;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.numbers = numbers;
        this.views = views;
        this.timeoutMinutes = timeoutMinutes;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setCouponQuotes(CouponQuoteService couponQuotes) {
        this.couponQuotes = couponQuotes;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setCouponReservations(CouponReservationService couponReservations) {
        this.couponReservations = couponReservations;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCouponEligibility(CouponEligibilityService couponEligibility) {
        this.couponEligibility = couponEligibility;
    }

    @Transactional
    public TradeDetailView create(CreateTradeRequest request, String key) {
        currentUser.requirePermission("trade:create");
        long userId = currentUser.id();
        return idempotency.execute(userId, "POST", "/api/trades", key, request,
                TradeDetailView.class, () -> createTrade(userId, request, key));
    }

    private TradeDetailView createTrade(long userId, CreateTradeRequest request, String key) {
        String tradeNo = idempotency.businessNo("TR", userId, key);
        TradeOrder existing = tradeMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getTradeNo, tradeNo));
        if (existing != null) return views.trade(existing);

        UserAddress address = addressService.ownedEntity(Long.parseLong(request.addressId()), userId);
        List<CartItem> cartItems = cartService.resolveItems(userId, request.cartItemIds());
        List<CheckoutLine> lines = cartItems.stream().map(cartService::checkoutLine)
                .sorted(Comparator.comparing(line -> line.sku().getId())).toList();
        if (lines.stream().anyMatch(line -> !line.valid())) {
            throw BusinessException.unprocessable("CHECKOUT_ITEMS_INVALID", "结算项存在无效商品或库存不足");
        }
        QuoteResult quote = request.couponQuoteToken() == null || couponQuotes == null ? null
                : couponQuotes.verify(userId, request.couponQuoteToken(), lines);
        BigDecimal total = lines.stream().map(CheckoutLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = quote == null || quote.result() == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(quote.result().totalDiscountCents(), 2);
        boolean usesFirstOrderCoupon = couponEligibility != null && quote != null && quote.result() != null
                && couponEligibility.usesFirstOrderCoupon(quote.result().coupons().stream()
                .map(applied -> applied.coupon().templateId()).toList());
        if (usesFirstOrderCoupon) {
            if (couponEligibility.hasPaidTrade(userId)) {
                throw BusinessException.unprocessable("COUPON_FIRST_ORDER_QUALIFICATION_LOST",
                        "用户已完成其他首单");
            }
            TradeOrder pending = tradeMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getUserId, userId)
                    .eq(TradeOrder::getTradeStatus, TradeStatus.PENDING_PAYMENT)
                    .eq(TradeOrder::getUsesFirstOrderCoupon, true)
                    .orderByAsc(TradeOrder::getId).last("LIMIT 1"));
            if (pending != null) firstOrderTradeExists();
        }
        TradeOrder trade = new TradeOrder();
        trade.setTradeNo(tradeNo);
        trade.setUserId(userId);
        trade.setTradeStatus(TradeStatus.PENDING_PAYMENT);
        trade.setGrossAmount(total);
        trade.setCouponDiscountAmount(discount);
        trade.setUsesFirstOrderCoupon(usesFirstOrderCoupon);
        trade.setPayableAmount(total.subtract(discount));
        copyAddress(address, trade);
        trade.setPayExpireAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        trade.setVersion(0);
        try {
            tradeMapper.insert(trade);
        } catch (DuplicateKeyException ex) {
            if (usesFirstOrderCoupon) firstOrderTradeExists();
            throw ex;
        }

        Map<Long, List<CheckoutLine>> byShop = new LinkedHashMap<>();
        Map<Long, OrderItem> orderItems = new LinkedHashMap<>();
        lines.forEach(line -> byShop.computeIfAbsent(line.shop().getId(), ignored -> new ArrayList<>()).add(line));
        for (Map.Entry<Long, List<CheckoutLine>> entry : byShop.entrySet()) {
            createOrder(trade, entry.getKey(), entry.getValue(), request.shopRemarks(), quote, orderItems);
        }
        if (couponReservations != null && quote != null) {
            couponReservations.reserve(userId, trade.getId(), quote.result(), orderItems);
        }
        cartItems.forEach(item -> cartService.delete(item.getId()));
        return views.trade(tradeMapper.selectById(trade.getId()));
    }

    private void firstOrderTradeExists() {
        throw BusinessException.conflict("COUPON_FIRST_ORDER_TRADE_EXISTS",
                "已有一张使用首单券的待支付父交易");
    }

    public TradeDetailView detail(long tradeId) {
        TradeOrder trade = owned(tradeId);
        return views.trade(trade);
    }

    @Transactional
    public TradeDetailView cancel(long tradeId, CancelTradeRequest request) {
        TradeOrder trade = owned(tradeId, true);
        if (trade.getTradeStatus() != TradeStatus.PENDING_PAYMENT) {
            throw BusinessException.conflict("TRADE_NOT_CANCELLABLE", "交易当前不可取消");
        }
        if (couponReservations != null) couponReservations.release(tradeId, "USER_CANCEL");
        List<OrderInfo> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getTradeId, tradeId).orderByAsc(OrderInfo::getId));
        for (OrderInfo order : orders) {
            // 交易取消后订单不再继续履约，因此同步撤销尚未审核的售后申请。
            // Mapper 内部限定 status=PENDING，不影响已审核、退货中或退款中的售后。
            afterSaleMapper.cancelPendingByOrderId(order.getId());
            List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getSkuId));
            for (OrderItem item : items) {
                InventoryStock before = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                        .eq(InventoryStock::getSkuId, item.getSkuId()));
                if (stockMapper.release(item.getSkuId(), item.getQuantity()) != 1) {
                    throw BusinessException.conflict("LOCKED_INVENTORY_INCONSISTENT", "锁定库存不一致");
                }
                item.setReservationStatus(ReservationStatus.RELEASED);
                itemMapper.updateById(item);
                InventoryStock after = stockMapper.selectById(before.getId());
                inventoryTransactionMapper.insert(inventoryTx(item.getSkuId(), InventoryTransactionType.RELEASE,
                        item.getQuantity(), -item.getQuantity(), after, "TRADE_ORDER",
                        trade.getTradeNo() + "-RELEASE-" + item.getSkuId(),
                        userId(), request.reason()));
            }
            OrderStatus from = order.getOrderStatus();
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setCancelReason(request.reason().trim());
            order.setCancelledAt(LocalDateTime.now());
            orderMapper.updateById(order);
            historyMapper.insert(history(order.getId(), from, OrderStatus.CANCELLED,
                    OrderOperationType.CANCEL, userId(), request.reason()));
        }
        trade.setTradeStatus(TradeStatus.CANCELLED);
        trade.setCancelledAt(LocalDateTime.now());
        tradeMapper.updateById(trade);
        return views.trade(tradeMapper.selectById(tradeId));
    }

    private void createOrder(TradeOrder trade, long shopId, List<CheckoutLine> lines,
                             Map<String, String> remarks, QuoteResult quote,
                             Map<Long, OrderItem> orderItems) {
        BigDecimal amount = lines.stream().map(CheckoutLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = quote == null || quote.result() == null ? BigDecimal.ZERO : lines.stream()
                .map(line -> BigDecimal.valueOf(quote.result().lineDiscounts()
                        .getOrDefault(line.cart().getId(), 0L), 2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        OrderInfo order = new OrderInfo();
        order.setOrderNo(numbers.next("OR"));
        order.setTradeId(trade.getId());
        order.setUserId(trade.getUserId());
        order.setShopId(shopId);
        order.setShopName(lines.getFirst().shop().getShopName());
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setItemAmount(amount);
        order.setFreightAmount(BigDecimal.ZERO.setScale(2));
        order.setCouponDiscountAmount(discount);
        order.setPayableAmount(amount.subtract(discount));
        order.setRefundAmount(BigDecimal.ZERO.setScale(2));
        order.setBuyerRemark(remarks == null ? null : remarks.get(Long.toString(shopId)));
        order.setVersion(0);
        orderMapper.insert(order);
        for (CheckoutLine line : lines) {
            if (stockMapper.reserve(line.sku().getId(), line.cart().getQuantity()) != 1) {
                throw BusinessException.unprocessable("INVENTORY_INSUFFICIENT", "库存不足");
            }
            InventoryStock after = stockMapper.selectById(line.stock().getId());
            inventoryTransactionMapper.insert(inventoryTx(line.sku().getId(), InventoryTransactionType.LOCK,
                    -line.cart().getQuantity(), line.cart().getQuantity(), after, "TRADE_ORDER",
                    trade.getTradeNo() + "-LOCK-" + line.sku().getId(),
                    trade.getUserId(), null));
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setShopId(shopId);
            item.setSpuId(line.spu().getId());
            item.setSkuId(line.sku().getId());
            item.setSpuNo(line.spu().getSpuNo());
            item.setSkuNo(line.sku().getSkuNo());
            item.setProductName(line.spu().getProductName());
            item.setSkuName(line.sku().getSkuName());
            item.setSpecJson(line.sku().getSpecJson());
            item.setImageUrl(line.sku().getImageUrl());
            item.setUnitPrice(line.sku().getSalePrice());
            item.setQuantity(line.cart().getQuantity());
            item.setOriginalAmount(line.amount());
            item.setFreightAmount(BigDecimal.ZERO.setScale(2));
            BigDecimal itemDiscount = quote == null || quote.result() == null ? BigDecimal.ZERO
                    : BigDecimal.valueOf(quote.result().lineDiscounts()
                    .getOrDefault(line.cart().getId(), 0L), 2);
            item.setCouponDiscountAmount(itemDiscount);
            item.setPayableAmount(line.amount().subtract(itemDiscount));
            item.setRefundedQuantity(0);
            item.setRefundedAmount(BigDecimal.ZERO.setScale(2));
            item.setReservationStatus(ReservationStatus.LOCKED);
            itemMapper.insert(item);
            orderItems.put(line.cart().getId(), item);
        }
        historyMapper.insert(history(order.getId(), null, OrderStatus.PENDING_PAYMENT,
                OrderOperationType.CREATE, trade.getUserId(), null));
    }

    private InventoryTransaction inventoryTx(long skuId, InventoryTransactionType type,
                                              int availableChange, int lockedChange, InventoryStock after,
                                              String businessType, String businessNo, Long operator, String remark) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionNo(numbers.next("IT"));
        transaction.setSkuId(skuId);
        transaction.setTransactionType(type);
        transaction.setAvailableChange(availableChange);
        transaction.setLockedChange(lockedChange);
        transaction.setAvailableAfter(after.getAvailableQuantity());
        transaction.setLockedAfter(after.getLockedQuantity());
        transaction.setBusinessType(businessType);
        transaction.setBusinessNo(businessNo);
        transaction.setOperatorId(operator);
        transaction.setRemark(remark);
        return transaction;
    }

    private OrderStatusHistory history(long orderId, OrderStatus from, OrderStatus to,
                                       OrderOperationType type, Long operatorId, String remark) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperationType(type);
        history.setOperatorType(OperatorType.USER);
        history.setOperatorId(operatorId);
        history.setRemark(remark);
        return history;
    }

    private TradeOrder owned(long tradeId) {
        return owned(tradeId, false);
    }

    private TradeOrder owned(long tradeId, boolean lock) {
        LambdaQueryWrapper<TradeOrder> query = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getId, tradeId).eq(TradeOrder::getUserId, userId());
        if (lock) query.last("FOR UPDATE");
        TradeOrder trade = tradeMapper.selectOne(query);
        if (trade == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "交易不存在");
        return trade;
    }

    private long userId() {
        return currentUser.id();
    }

    private void copyAddress(UserAddress source, TradeOrder target) {
        target.setRecipientName(source.getRecipientName());
        target.setRecipientPhone(source.getRecipientPhone());
        target.setProvinceName(source.getProvinceName());
        target.setCityName(source.getCityName());
        target.setDistrictName(source.getDistrictName());
        target.setDetailAddress(source.getDetailAddress());
    }
}
