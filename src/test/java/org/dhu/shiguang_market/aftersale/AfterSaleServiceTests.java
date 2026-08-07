package org.dhu.shiguang_market.aftersale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ReturnShipmentRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.UpdateReturnShipmentRequest;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.aftersale.service.AfterSaleService;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * AfterSaleService 买家端售后 Service 单元测试。
 * <p>
 * 使用 Mockito 模拟全部依赖，验证撤销/退货物流的状态机校验和版本冲突。
 * <p>
 * 注意：涉及 MyBatis-Plus LambdaQueryWrapper 内部缓存的方法（如资格查询、创建申请）
 * 在纯 Mock 环境下依赖表元数据预热，这些场景留给集成测试覆盖。
 */
class AfterSaleServiceTests {

    private final AfterSaleRequestMapper afterSaleMapper = mock(AfterSaleRequestMapper.class);
    private final OrderItemMapper itemMapper = mock(OrderItemMapper.class);
    private final OrderInfoMapper orderMapper = mock(OrderInfoMapper.class);
    private final ShopMapper shopMapper = mock(ShopMapper.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final NumberGenerator numbers = mock(NumberGenerator.class);
    private final ContentSafety contentSafety = mock(ContentSafety.class);
    private AfterSaleService service;

    private static final long USER_ID = 100L;
    private static final long AFTER_SALE_ID = 1L;
    private static final long ORDER_ID = 200L;
    private static final long ORDER_ITEM_ID = 300L;
    private static final long SKU_ID = 400L;
    private static final long SPU_ID = 500L;
    private static final long SHOP_ID = 600L;

    @BeforeAll
    static void initializeLambdaMetadata() {
        // 纯 Mockito 测试不会启动 MyBatis，上下文缺失时需手工初始化 Lambda 列缓存。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, AfterSaleRequest.class);
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new AfterSaleService(afterSaleMapper, itemMapper, orderMapper,
                shopMapper, currentUser, idempotency, numbers, contentSafety);
        when(currentUser.id()).thenReturn(USER_ID);
    }

    // ══════════════════════════════════════════════════════════════
    // 撤销状态机测试
    // ══════════════════════════════════════════════════════════════

    /**
     * REFUNDING 状态的售后不可撤销，应返回 AFTER_SALE_NOT_CANCELLABLE。
     */
    @Test
    void cancelRefundingAfterSaleThrowsException() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.REFUNDING);
        when(afterSaleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ar);

        assertThatThrownBy(() -> service.cancel(AFTER_SALE_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("AFTER_SALE_NOT_CANCELLABLE");
                });
    }

    /**
     * COMPLETED 状态的售后不可撤销。
     */
    @Test
    void cancelCompletedAfterSaleThrowsException() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.COMPLETED);
        when(afterSaleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ar);

        assertThatThrownBy(() -> service.cancel(AFTER_SALE_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("AFTER_SALE_NOT_CANCELLABLE");
                });
    }

    /**
     * CANCELLED 状态的售后不可再次撤销。
     */
    @Test
    void cancelAlreadyCancelledAfterSaleThrowsException() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.CANCELLED);
        when(afterSaleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ar);

        assertThatThrownBy(() -> service.cancel(AFTER_SALE_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("AFTER_SALE_NOT_CANCELLABLE");
                });
    }

    // ══════════════════════════════════════════════════════════════
    // 退货物流状态机测试
    // ══════════════════════════════════════════════════════════════

    /**
     * 已提交物流后不可重复提交，返回 RETURN_SHIPMENT_ALREADY_SUBMITTED。
     */
    @Test
    void submitReturnShipmentFailsWhenAlreadySubmitted() {
        AfterSaleRequest ar = buildWaitingReturnAfterSale("SF1234567890");
        when(afterSaleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ar);
        when(idempotency.execute(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<AfterSaleDetailView> action = inv.getArgument(6);
                    return action.get();
                });

        ReturnShipmentRequest request = new ReturnShipmentRequest("顺丰", "顺丰速运", "NEW999");

        assertThatThrownBy(() -> service.submitReturnShipment(AFTER_SALE_ID, request, "dup-ship"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("RETURN_SHIPMENT_ALREADY_SUBMITTED");
                });
    }

    /**
     * 仅退款类型不可提交退货物流，返回 RETURN_SHIPMENT_NOT_ALLOWED。
     */
    @Test
    void submitReturnShipmentFailsForRefundOnlyType() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.WAITING_RETURN);
        ar.setRequestType(AfterSaleType.REFUND_ONLY);
        when(afterSaleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ar);
        when(idempotency.execute(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<AfterSaleDetailView> action = inv.getArgument(6);
                    return action.get();
                });

        ReturnShipmentRequest request = new ReturnShipmentRequest("顺丰", "顺丰速运", "SF999");

        assertThatThrownBy(() -> service.submitReturnShipment(AFTER_SALE_ID, request, "ro-ship"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("RETURN_SHIPMENT_NOT_ALLOWED");
                });
    }

    /**
     * version 不匹配时更正物流失败，返回 VERSION_CONFLICT。
     */
    @Test
    void updateReturnShipmentRejectsVersionMismatch() {
        AfterSaleRequest ar = buildWaitingReturnAfterSale("SF1234567890");
        ar.setVersion(2);
        when(afterSaleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ar);

        UpdateReturnShipmentRequest req = new UpdateReturnShipmentRequest(
                "SF", "顺丰速运", "SF1234567890", 1);

        assertThatThrownBy(() -> service.updateReturnShipment(AFTER_SALE_ID, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("VERSION_CONFLICT");
                });
    }

    /**
     * 商家确认收货后不可更正物流，返回 RETURN_SHIPMENT_NOT_ALLOWED。
     */
    @Test
    void updateReturnShipmentFailsAfterReceived() {
        AfterSaleRequest ar = buildWaitingReturnAfterSale("SF1234567890");
        ar.setReturnReceivedAt(java.time.LocalDateTime.now());
        when(afterSaleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ar);

        UpdateReturnShipmentRequest req = new UpdateReturnShipmentRequest(
                "SF", "顺丰速运", "SF1234567890", 2);

        assertThatThrownBy(() -> service.updateReturnShipment(AFTER_SALE_ID, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("RETURN_SHIPMENT_NOT_ALLOWED");
                });
    }

    // ══════════════════════════════════════════════════════════════
    // 售后资格边界测试
    // ══════════════════════════════════════════════════════════════

    /**
     * 售后资格属于买家私有资源，不能查询其他用户的订单。
     */
    @Test
    void eligibilityRejectsOrderOwnedByAnotherUser() {
        OrderItem item = buildOrderItem();
        OrderInfo order = buildOrder(OrderStatus.PENDING_SHIPMENT);
        order.setUserId(USER_ID + 1);
        when(itemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(item);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(afterSaleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.eligibility(ORDER_ID, ORDER_ITEM_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_FOUND"));
    }

    /**
     * 已完成订单超过七天售后期限后，不再支持创建售后。
     */
    @Test
    void eligibilityExpiresSevenDaysAfterCompletion() {
        OrderItem item = buildOrderItem();
        OrderInfo order = buildOrder(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now().minusDays(8));
        when(itemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(item);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(afterSaleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var result = service.eligibility(ORDER_ID, ORDER_ITEM_ID);

        assertThat(result.eligible()).isFalse();
        assertThat(result.supportedTypes()).isEmpty();
        assertThat(result.ineligibleReason()).isEqualTo("已超过售后申请期限");
    }

    // ══════════════════════════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════════════════════════

    private AfterSaleRequest buildAfterSale(AfterSaleStatus status) {
        AfterSaleRequest ar = new AfterSaleRequest();
        ar.setId(AFTER_SALE_ID);
        ar.setAfterSaleNo("AS001");
        ar.setOrderId(ORDER_ID);
        ar.setOrderItemId(ORDER_ITEM_ID);
        ar.setUserId(USER_ID);
        ar.setRequestType(AfterSaleType.RETURN_REFUND);
        ar.setQuantity(1);
        ar.setRequestedAmount(new BigDecimal("3999.00"));
        ar.setStatus(status);
        ar.setRefundStatus(RefundStatus.NOT_STARTED);
        ar.setVersion(0);
        return ar;
    }

    private OrderItem buildOrderItem() {
        OrderItem item = new OrderItem();
        item.setId(ORDER_ITEM_ID);
        item.setOrderId(ORDER_ID);
        item.setQuantity(2);
        item.setPayableAmount(new BigDecimal("200.00"));
        item.setRefundedQuantity(0);
        item.setRefundedAmount(BigDecimal.ZERO);
        return item;
    }

    private OrderInfo buildOrder(OrderStatus status) {
        OrderInfo order = new OrderInfo();
        order.setId(ORDER_ID);
        order.setUserId(USER_ID);
        order.setOrderStatus(status);
        return order;
    }

    private AfterSaleRequest buildWaitingReturnAfterSale(String trackingNo) {
        AfterSaleRequest ar = new AfterSaleRequest();
        ar.setId(AFTER_SALE_ID);
        ar.setAfterSaleNo("AS002");
        ar.setOrderId(ORDER_ID);
        ar.setOrderItemId(ORDER_ITEM_ID);
        ar.setUserId(USER_ID);
        ar.setRequestType(AfterSaleType.RETURN_REFUND);
        ar.setQuantity(1);
        ar.setRequestedAmount(new BigDecimal("3999.00"));
        ar.setApprovedQuantity(1);
        ar.setApprovedAmount(new BigDecimal("3999.00"));
        ar.setStatus(AfterSaleStatus.WAITING_RETURN);
        ar.setRefundStatus(RefundStatus.NOT_STARTED);
        ar.setReturnTrackingNo(trackingNo);
        ar.setVersion(2);
        return ar;
    }
}
