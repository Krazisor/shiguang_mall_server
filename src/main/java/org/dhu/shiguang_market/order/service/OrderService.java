package org.dhu.shiguang_market.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderDetailView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderSummaryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShipOrderRequest;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShopOrderSummaryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShopOrderDetailView;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.order.model.OrderStatusHistory;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.dhu.shiguang_market.integration.merchantwallet.MerchantSettlementPort;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderStatusHistoryMapper historyMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final SysUserMapper userMapper;
    private final CurrentUserService currentUser;
    private final ShopAccessService shopAccess;
    private final OrderViewService views;
    private final NumberGenerator numbers;
    private MerchantSettlementPort merchantSettlement;

    public OrderService(OrderInfoMapper orderMapper, OrderItemMapper itemMapper,
                        OrderStatusHistoryMapper historyMapper, AfterSaleRequestMapper afterSaleMapper,
                        InventoryStockMapper stockMapper,
                        InventoryTransactionMapper inventoryTransactionMapper,
                        SysUserMapper userMapper, CurrentUserService currentUser,
                        ShopAccessService shopAccess, OrderViewService views, NumberGenerator numbers) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.historyMapper = historyMapper;
        this.afterSaleMapper = afterSaleMapper;
        this.stockMapper = stockMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.userMapper = userMapper;
        this.currentUser = currentUser;
        this.shopAccess = shopAccess;
        this.views = views;
        this.numbers = numbers;
    }

    @Autowired(required = false)
    public void setMerchantSettlement(MerchantSettlementPort merchantSettlement) {
        this.merchantSettlement = merchantSettlement;
    }

    public PageView<OrderSummaryView> buyerOrders(
            OrderStatus orderStatus, OrderPaymentStatus paymentStatus, String keyword,
            LocalDateTime createdFrom, LocalDateTime createdTo, long page, long pageSize) {
        currentUser.requirePermission("order:read:self");
        Page<OrderInfo> result = pageOrders(currentUser.id(), null, orderStatus, paymentStatus,
                keyword, createdFrom, createdTo, page, pageSize);
        return PageView.of(result, result.getRecords().stream().map(views::summary).toList());
    }

    public OrderDetailView buyerDetail(long orderId) {
        OrderInfo order = owned(orderId);
        return views.detail(order);
    }

    @Transactional
    public OrderDetailView complete(long orderId) {
        OrderInfo order = owned(orderId, true);
        requireNoActiveAfterSale(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING_RECEIPT) {
            throw BusinessException.conflict("ORDER_NOT_COMPLETABLE", "订单当前不可确认收货");
        }
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        if (merchantSettlement != null) merchantSettlement.markOrderCompleted(order);
        historyMapper.insert(history(order.getId(), OrderStatus.PENDING_RECEIPT, OrderStatus.COMPLETED,
                OrderOperationType.COMPLETE, OperatorType.USER, currentUser.id(), null));
        return views.detail(orderMapper.selectById(orderId));
    }

    public PageView<ShopOrderSummaryView> shopOrders(
            long shopId, OrderStatus orderStatus, OrderPaymentStatus paymentStatus, String keyword,
            LocalDateTime createdFrom, LocalDateTime createdTo, long page, long pageSize) {
        shopAccess.require(shopId, "shop:order:read");
        Page<OrderInfo> result = pageOrders(null, shopId, orderStatus, paymentStatus,
                keyword, createdFrom, createdTo, page, pageSize);
        return PageView.of(result, result.getRecords().stream().map(order -> {
            OrderSummaryView summary = views.summary(order);
            return new ShopOrderSummaryView(summary.id(), summary.orderNo(), summary.tradeId(), summary.tradeNo(),
                    summary.shop(), summary.orderStatus(), summary.displayStatus(), summary.paymentStatus(), summary.payableAmount(),
                    summary.refundAmount(), summary.itemSummary(), summary.itemKinds(), summary.totalQuantity(),
                    summary.createdAt(), summary.availableActions(),
                    IdentityViewMapper.user(userMapper.selectById(order.getUserId())));
        }).toList());
    }

    public ShopOrderDetailView shopDetail(long shopId, long orderId) {
        shopAccess.require(shopId, "shop:order:read");
        OrderInfo order = scoped(shopId, orderId);
        return views.shopDetail(order);
    }

    @Transactional
    public ShopOrderDetailView ship(long shopId, long orderId, ShipOrderRequest request) {
        shopAccess.require(shopId, "shop:order:ship");
        if (orderMapper.exists(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getTrackingNo, request.trackingNo()).ne(OrderInfo::getId, orderId))) {
            throw BusinessException.conflict("TRACKING_NO_ALREADY_USED", "运单号已使用");
        }
        OrderInfo order = scoped(shopId, orderId, true);
        requireNoActiveAfterSale(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING_SHIPMENT
                || order.getPaymentStatus() == OrderPaymentStatus.UNPAID) {
            throw BusinessException.conflict("ORDER_NOT_SHIPPABLE", "订单当前不可发货");
        }
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId).eq(OrderItem::getShopId, shopId)
                .orderByAsc(OrderItem::getSkuId));
        for (OrderItem item : items) {
            InventoryStock before = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                    .eq(InventoryStock::getSkuId, item.getSkuId()));
            if (item.getReservationStatus() != ReservationStatus.LOCKED
                    || stockMapper.deduct(item.getSkuId(), item.getQuantity()) != 1) {
                throw BusinessException.conflict("LOCKED_INVENTORY_INCONSISTENT", "锁定库存不一致");
            }
            item.setReservationStatus(ReservationStatus.DEDUCTED);
            itemMapper.updateById(item);
            InventoryStock after = stockMapper.selectById(before.getId());
            InventoryTransaction transaction = new InventoryTransaction();
            transaction.setTransactionNo(numbers.next("IT"));
            transaction.setSkuId(item.getSkuId());
            transaction.setTransactionType(InventoryTransactionType.DEDUCT);
            transaction.setAvailableChange(0);
            transaction.setLockedChange(-item.getQuantity());
            transaction.setAvailableAfter(after.getAvailableQuantity());
            transaction.setLockedAfter(after.getLockedQuantity());
            transaction.setBusinessType("ORDER_SHIPMENT");
            transaction.setBusinessNo(order.getOrderNo() + "-SHIP-" + item.getSkuId());
            transaction.setOperatorId(currentUser.id());
            inventoryTransactionMapper.insert(transaction);
        }
        order.setOrderStatus(OrderStatus.PENDING_RECEIPT);
        order.setCarrierCode(request.carrierCode().trim());
        order.setCarrierName(request.carrierName().trim());
        order.setTrackingNo(request.trackingNo().trim());
        order.setShippedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        historyMapper.insert(history(orderId, OrderStatus.PENDING_SHIPMENT, OrderStatus.PENDING_RECEIPT,
                OrderOperationType.SHIP, OperatorType.SHOP, currentUser.id(), null));
        return views.shopDetail(orderMapper.selectById(orderId));
    }

    /**
     * 发货和确认收货共用的售后保护。
     * PENDING、WAITING_RETURN、REFUNDING 都表示售后尚未结束，此时禁止继续推进订单。
     */
    private void requireNoActiveAfterSale(long orderId) {
        if (afterSaleMapper.existsActiveByOrderId(orderId)
                || afterSaleMapper.existsPendingAppealByOrderId(orderId)) {
            throw BusinessException.conflict("ORDER_HAS_ACTIVE_AFTER_SALE", "订单存在活跃售后");
        }
    }

    private Page<OrderInfo> pageOrders(Long userId, Long shopId, OrderStatus status,
                                       OrderPaymentStatus paymentStatus, String keyword,
                                       LocalDateTime from, LocalDateTime to, long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return orderMapper.selectOrderPage(Page.of(page, pageSize), userId, shopId, status,
                paymentStatus, normalizedKeyword, from, to);
    }

    private OrderInfo owned(long orderId) {
        return owned(orderId, false);
    }

    private OrderInfo owned(long orderId, boolean lock) {
        LambdaQueryWrapper<OrderInfo> query = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId).eq(OrderInfo::getUserId, currentUser.id());
        if (lock) query.last("FOR UPDATE");
        OrderInfo order = orderMapper.selectOne(query);
        if (order == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "订单不存在");
        return order;
    }

    private OrderInfo scoped(long shopId, long orderId) {
        return scoped(shopId, orderId, false);
    }

    private OrderInfo scoped(long shopId, long orderId, boolean lock) {
        LambdaQueryWrapper<OrderInfo> query = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId).eq(OrderInfo::getShopId, shopId);
        if (lock) query.last("FOR UPDATE");
        OrderInfo order = orderMapper.selectOne(query);
        if (order == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "订单不存在");
        return order;
    }

    private OrderStatusHistory history(long orderId, OrderStatus from, OrderStatus to,
                                       OrderOperationType type, OperatorType operatorType,
                                       Long operatorId, String remark) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperationType(type);
        history.setOperatorType(operatorType);
        history.setOperatorId(operatorId);
        history.setRemark(remark);
        return history;
    }
}
