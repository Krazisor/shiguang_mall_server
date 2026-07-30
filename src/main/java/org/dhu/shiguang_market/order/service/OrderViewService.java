package org.dhu.shiguang_market.order.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.dhu.shiguang_market.common.api.CommonViews.AddressSnapshot;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderDetailView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderItemSummaryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderItemView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderStatusHistoryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderSummaryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShippingView;
import org.dhu.shiguang_market.order.dto.OrderDtos.TradeDetailView;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.order.model.OrderStatusHistory;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.springframework.stereotype.Service;

@Service
public class OrderViewService {
    private final TradeOrderMapper tradeMapper;
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderStatusHistoryMapper historyMapper;
    private final ShopMapper shopMapper;

    public OrderViewService(TradeOrderMapper tradeMapper, OrderInfoMapper orderMapper,
                            OrderItemMapper itemMapper, OrderStatusHistoryMapper historyMapper,
                            ShopMapper shopMapper) {
        this.tradeMapper = tradeMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.historyMapper = historyMapper;
        this.shopMapper = shopMapper;
    }

    public TradeDetailView trade(TradeOrder trade) {
        List<OrderSummaryView> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getTradeId, trade.getId()).orderByDesc(OrderInfo::getId))
                .stream().map(order -> summary(order, trade)).toList();
        List<String> actions = switch (trade.getTradeStatus()) {
            case PENDING_PAYMENT -> List.of("CANCEL", "PAY");
            default -> List.of();
        };
        return new TradeDetailView(id(trade.getId()), trade.getTradeNo(), trade.getTradeStatus(),
                money(trade.getPayableAmount()), address(trade), time(trade.getPayExpireAt()),
                time(trade.getPaidAt()), time(trade.getCancelledAt()), orders, actions);
    }

    public OrderSummaryView summary(OrderInfo order) {
        return summary(order, tradeMapper.selectById(order.getTradeId()));
    }

    public OrderSummaryView summary(OrderInfo order, TradeOrder trade) {
        Shop shop = shopMapper.selectById(order.getShopId());
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getId));
        List<OrderItemSummaryView> itemSummary = items.stream().limit(3)
                .map(item -> new OrderItemSummaryView(item.getProductName(), item.getSkuName(),
                        item.getImageUrl(), item.getQuantity())).toList();
        int totalQuantity = items.stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderSummaryView(id(order.getId()), order.getOrderNo(), id(order.getTradeId()),
                trade.getTradeNo(), IdentityViewMapper.shop(shop), order.getOrderStatus(),
                order.getPaymentStatus(), money(order.getPayableAmount()), money(order.getRefundAmount()),
                itemSummary, items.size(), totalQuantity, time(order.getCreatedAt()), actions(order));
    }

    public OrderDetailView detail(OrderInfo order) {
        TradeOrder trade = tradeMapper.selectById(order.getTradeId());
        Shop shop = shopMapper.selectById(order.getShopId());
        List<OrderItemView> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getId))
                .stream().map(this::item).toList();
        List<OrderStatusHistoryView> history = historyMapper.selectList(
                        new LambdaQueryWrapper<OrderStatusHistory>()
                                .eq(OrderStatusHistory::getOrderId, order.getId())
                                .orderByAsc(OrderStatusHistory::getCreatedAt).orderByAsc(OrderStatusHistory::getId))
                .stream().map(value -> new OrderStatusHistoryView(value.getFromStatus(), value.getToStatus(),
                        value.getOperationType(), value.getOperatorType(), value.getRemark(), time(value.getCreatedAt())))
                .toList();
        ShippingView shipping = order.getShippedAt() == null ? null
                : new ShippingView(order.getCarrierCode(), order.getCarrierName(),
                order.getTrackingNo(), time(order.getShippedAt()));
        return new OrderDetailView(id(order.getId()), order.getOrderNo(), id(order.getTradeId()),
                trade.getTradeNo(), IdentityViewMapper.shop(shop), order.getOrderStatus(), order.getPaymentStatus(),
                money(order.getItemAmount()), money(order.getFreightAmount()), money(order.getPayableAmount()),
                money(order.getRefundAmount()), order.getBuyerRemark(), address(trade), shipping,
                items, history, actions(order));
    }

    private OrderItemView item(OrderItem item) {
        return new OrderItemView(id(item.getId()), id(item.getSpuId()), id(item.getSkuId()),
                item.getSpuNo(), item.getSkuNo(), item.getProductName(), item.getSkuName(), item.getSpecJson(),
                item.getImageUrl(), money(item.getUnitPrice()), item.getQuantity(), money(item.getOriginalAmount()),
                money(item.getFreightAmount()), money(item.getPayableAmount()), item.getRefundedQuantity(),
                money(item.getRefundedAmount()), item.getReservationStatus());
    }

    private List<String> actions(OrderInfo order) {
        return switch (order.getOrderStatus()) {
            case PENDING_RECEIPT -> List.of("COMPLETE");
            default -> List.of();
        };
    }

    private AddressSnapshot address(TradeOrder trade) {
        return new AddressSnapshot(trade.getRecipientName(), trade.getRecipientPhone(),
                trade.getProvinceName(), trade.getCityName(), trade.getDistrictName(), trade.getDetailAddress());
    }
}
