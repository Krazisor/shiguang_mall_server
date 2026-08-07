package org.dhu.shiguang_market.aftersale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleItemSnapshot;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleOrderSnapshot;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ApproveAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RejectAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RetryRefundRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ShopAfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.mapper.WalletTransactionMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ShopAfterSaleService 商家端售后 Service 单元测试。
 * <p>
 * 重点验证：审核状态机校验、版本冲突检测、退款重试条件、店铺跨店越权保护。
 */
class ShopAfterSaleServiceTests {

    private final AfterSaleRequestMapper afterSaleMapper = mock(AfterSaleRequestMapper.class);
    private final OrderItemMapper itemMapper = mock(OrderItemMapper.class);
    private final OrderInfoMapper orderMapper = mock(OrderInfoMapper.class);
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final InventoryTransactionMapper invTxMapper = mock(InventoryTransactionMapper.class);
    private final WalletAccountMapper walletMapper = mock(WalletAccountMapper.class);
    private final WalletTransactionMapper walletTxMapper = mock(WalletTransactionMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final AfterSaleService afterSaleService = mock(AfterSaleService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final ShopAccessService shopAccess = mock(ShopAccessService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final NumberGenerator numbers = mock(NumberGenerator.class);
    private ShopAfterSaleService service;

    private static final long USER_ID = 100L;
    private static final long SHOP_ID = 600L;
    private static final long ORDER_ID = 200L;
    private static final long AFTER_SALE_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new ShopAfterSaleService(afterSaleMapper, itemMapper, orderMapper,
                stockMapper, invTxMapper, walletMapper, walletTxMapper, userMapper,
                afterSaleService, currentUser, shopAccess, idempotency, numbers);
        when(currentUser.id()).thenReturn(USER_ID);
        Shop mockShop = new Shop();
        mockShop.setId(SHOP_ID);
        when(shopAccess.require(eq(SHOP_ID), anyString())).thenReturn(mockShop);
        SysUser buyer = new SysUser();
        buyer.setId(USER_ID);
        buyer.setUsername("buyer");
        buyer.setNickname("测试买家");
        buyer.setStatus(UserStatus.ACTIVE);
        when(userMapper.selectById(USER_ID)).thenReturn(buyer);
    }

    // ══════════════════════════════════════════════════════════════
    // 批准测试
    // ══════════════════════════════════════════════════════════════

    /**
     * 非 PENDING 状态不可批准，应返回 AFTER_SALE_NOT_PENDING。
     */
    @Test
    void approveRejectsNonPendingAfterSale() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.REFUNDING, RefundStatus.NOT_STARTED);
        setupShopScopeMock(ar);

        when(idempotency.execute(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ShopAfterSaleDetailView> action = inv.getArgument(6);
                    return action.get();
                });

        assertThatThrownBy(() -> service.approve(SHOP_ID, AFTER_SALE_ID, validApproveRequest(0), "k1"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("AFTER_SALE_NOT_PENDING");
                });
    }

    /**
     * version 冲突时批准失败，应返回 VERSION_CONFLICT。
     */
    @Test
    void approveRejectsVersionMismatch() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.PENDING, RefundStatus.NOT_STARTED);
        ar.setVersion(1);
        setupShopScopeMock(ar);

        when(idempotency.execute(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ShopAfterSaleDetailView> action = inv.getArgument(6);
                    return action.get();
                });

        assertThatThrownBy(() -> service.approve(SHOP_ID, AFTER_SALE_ID, validApproveRequest(0), "k2"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("VERSION_CONFLICT");
                });
    }

    /**
     * 批准数量超过申请数量时拒绝，返回 AFTER_SALE_APPROVAL_EXCEEDED。
     */
    @Test
    void approveRejectsExcessiveQuantity() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.PENDING, RefundStatus.NOT_STARTED);
        ar.setQuantity(1);
        ar.setRequestedAmount(new BigDecimal("3999.00"));
        setupShopScopeMock(ar);

        when(idempotency.execute(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ShopAfterSaleDetailView> action = inv.getArgument(6);
                    return action.get();
                });

        // 批准 2 件，但申请仅 1 件
        assertThatThrownBy(() -> service.approve(SHOP_ID, AFTER_SALE_ID,
                new ApproveAfterSaleRequest(2, "7998.00", "同意", 0), "k3"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("AFTER_SALE_APPROVAL_EXCEEDED");
                });
    }

    // ══════════════════════════════════════════════════════════════
    // 拒绝测试
    // ══════════════════════════════════════════════════════════════

    /**
     * PENDING 状态可正常拒绝，验证拒绝后写入 updateById 的实体状态变为 REJECTED。
     */
    @Test
    void rejectPendingAfterSaleSucceeds() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.PENDING, RefundStatus.NOT_STARTED);
        setupShopScopeMock(ar);

        var request = new RejectAfterSaleRequest("凭证不充分", 0);
        when(afterSaleService.detail(any(AfterSaleRequest.class))).thenReturn(buildDetailView(ar));

        service.reject(SHOP_ID, AFTER_SALE_ID, request);
        // 验证 aftersale 的 status 被更新为 REJECTED
        assertThat(ar.getStatus()).isEqualTo(AfterSaleStatus.REJECTED);
    }

    // ══════════════════════════════════════════════════════════════
    // 退款重试测试
    // ══════════════════════════════════════════════════════════════

    /**
     * SUCCESS 状态的退款不可重试，应返回 REFUND_NOT_RETRYABLE。
     */
    @Test
    void retryRefundRejectsNonFailedStatus() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.COMPLETED, RefundStatus.SUCCESS);
        setupShopScopeMock(ar);

        when(idempotency.execute(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ShopAfterSaleDetailView> action = inv.getArgument(6);
                    return action.get();
                });

        assertThatThrownBy(() -> service.retryRefund(SHOP_ID, AFTER_SALE_ID,
                new RetryRefundRequest("重试", 0), "rk"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("REFUND_NOT_RETRYABLE");
                });
    }

    /**
     * 退款重试时 version 不匹配应拒绝。
     */
    @Test
    void retryRefundRejectsVersionMismatch() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.REFUNDING, RefundStatus.FAILED);
        ar.setVersion(3);
        setupShopScopeMock(ar);

        when(idempotency.execute(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ShopAfterSaleDetailView> action = inv.getArgument(6);
                    return action.get();
                });

        assertThatThrownBy(() -> service.retryRefund(SHOP_ID, AFTER_SALE_ID,
                new RetryRefundRequest("重试", 1), "rk"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("VERSION_CONFLICT");
                });
    }

    // ══════════════════════════════════════════════════════════════
    // 店铺范围测试
    // ══════════════════════════════════════════════════════════════

    /**
     * 售后关联的订单不属于当前店铺时返回 RESOURCE_NOT_FOUND。
     */
    @Test
    void detailReturnsNotFoundForNonShopAfterSale() {
        AfterSaleRequest ar = buildAfterSale(AfterSaleStatus.PENDING, RefundStatus.NOT_STARTED);
        OrderInfo otherShopOrder = buildShopOrder();
        otherShopOrder.setShopId(999L);
        when(afterSaleMapper.selectById(AFTER_SALE_ID)).thenReturn(ar);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(otherShopOrder);

        assertThatThrownBy(() -> service.detail(SHOP_ID, AFTER_SALE_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
                });
    }

    // ══════════════════════════════════════════════════════════════
    // 构建器
    // ══════════════════════════════════════════════════════════════

    /**
     * 设置 scoped() 方法所需的 mock：selectById 返回实体，selectOne(FOR UPDATE) 同样返回实体。
     */
    private void setupShopScopeMock(AfterSaleRequest ar) {
        when(afterSaleMapper.selectById(AFTER_SALE_ID)).thenReturn(ar);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(buildShopOrder());
        // 带 FOR UPDATE 的 selectOne 也需要返回实体
        when(afterSaleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ar);
    }

    private AfterSaleRequest buildAfterSale(AfterSaleStatus status, RefundStatus refundStatus) {
        AfterSaleRequest ar = new AfterSaleRequest();
        ar.setId(AFTER_SALE_ID);
        ar.setAfterSaleNo("AS001");
        ar.setOrderId(ORDER_ID);
        ar.setOrderItemId(300L);
        ar.setUserId(USER_ID);
        ar.setRequestType(AfterSaleType.REFUND_ONLY);
        ar.setQuantity(1);
        ar.setRequestedAmount(new BigDecimal("3999.00"));
        ar.setApprovedQuantity(1);
        ar.setApprovedAmount(new BigDecimal("3999.00"));
        ar.setStatus(status);
        ar.setRefundStatus(refundStatus);
        ar.setRefundNo("RF001");
        ar.setReviewerId(200L);
        ar.setVersion(0);
        return ar;
    }

    private OrderInfo buildShopOrder() {
        OrderInfo order = new OrderInfo();
        order.setId(ORDER_ID);
        order.setOrderNo("OR001");
        order.setShopId(SHOP_ID);
        order.setUserId(USER_ID);
        order.setOrderStatus(OrderStatus.PENDING_SHIPMENT);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setPayableAmount(new BigDecimal("7998.00"));
        return order;
    }

    private ApproveAfterSaleRequest validApproveRequest(int version) {
        return new ApproveAfterSaleRequest(1, "3999.00", "同意退款", version);
    }

    private AfterSaleDetailView buildDetailView(AfterSaleRequest ar) {
        return new AfterSaleDetailView(
                String.valueOf(ar.getId()), ar.getAfterSaleNo(), ar.getRequestType(),
                ar.getStatus(), ar.getRefundStatus(),
                new AfterSaleOrderSnapshot("200", "OR001", OrderStatus.PENDING_SHIPMENT),
                new ShopSummary("600", "SHOP001", "测试店铺", null, ShopStatus.ACTIVE),
                new AfterSaleItemSnapshot("300", "测试商品", "黑色 256GB",
                        Map.of(), null, "3999.00", 2),
                ar.getQuantity(), "QUALITY_PROBLEM", null,
                List.of(), "3999.00",
                ar.getApprovedQuantity(), ar.getApprovedAmount() != null ? "3999.00" : null,
                null, null, ar.getRefundNo(), null,
                null, null, null,
                0, java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(),
                List.of());
    }
}
