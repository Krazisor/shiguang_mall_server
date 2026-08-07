package org.dhu.shiguang_market.aftersale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ApproveAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ConfirmReturnReceivedRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.CreateAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RetryRefundRequest;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.mapper.WalletTransactionMapper;
import org.dhu.shiguang_market.payment.model.WalletAccount;
import org.dhu.shiguang_market.payment.model.WalletTransaction;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 阶段二真实基础设施测试。
 *
 * <p>通过 REAL_INFRA_TEST=true 显式启用，MySQL 数据在测试事务中自动回滚，
 * Redis 幂等键在每个用例结束后清理，避免污染开发环境。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "REAL_INFRA_TEST", matches = "true")
class AfterSaleInfrastructureIntegrationTests {
    private static final BigDecimal ITEM_AMOUNT = new BigDecimal("100.00");

    @Autowired private ShopAfterSaleService shopAfterSaleService;
    @Autowired private AfterSaleService afterSaleService;
    @Autowired private IdempotencyService idempotencyService;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AfterSaleRequestMapper afterSaleMapper;
    @Autowired private TradeOrderMapper tradeMapper;
    @Autowired private OrderInfoMapper orderMapper;
    @Autowired private OrderItemMapper itemMapper;
    @Autowired private InventoryStockMapper stockMapper;
    @Autowired private InventoryTransactionMapper inventoryTransactionMapper;
    @Autowired private WalletAccountMapper walletMapper;
    @Autowired private WalletTransactionMapper walletTransactionMapper;
    @Autowired private ProductSkuMapper skuMapper;
    @Autowired private ShopMapper shopMapper;
    @Autowired private SysUserMapper userMapper;

    @MockitoBean private CurrentUserService currentUser;
    @MockitoBean private ShopAccessService shopAccess;

    private final List<String> redisBases = new ArrayList<>();

    @AfterEach
    void cleanRedisKeys() {
        for (String base : redisBases) {
            redis.delete(List.of(base + ":request", base + ":lock", base + ":response"));
        }
        redisBases.clear();
    }

    @Test
    void realRedisReplaysTheSameIdempotentRequestOnlyOnce() {
        long userId = activeUsers().getFirst().getId();
        String path = "/integration/after-sale/idempotency";
        String key = "redis-" + shortId();
        rememberRedisBase(userId, "POST", path, key);
        AtomicInteger executions = new AtomicInteger();

        String first = idempotencyService.execute(userId, "POST", path, key, Map.of("value", 1),
                String.class, () -> {
                    executions.incrementAndGet();
                    return "OK";
                });
        String replay = idempotencyService.execute(userId, "POST", path, key, Map.of("value", 1),
                String.class, () -> {
                    executions.incrementAndGet();
                    return "SHOULD_NOT_RUN";
                });

        assertThat(first).isEqualTo("OK");
        assertThat(replay).isEqualTo("OK");
        assertThat(executions).hasValue(1);
    }

    @Test
    @Transactional
    void buyerCreationUsesMysqlOccupancyAndRejectsASecondOverAllocatedRequest() {
        Fixture fixture = createOrder(OrderStatus.PENDING_SHIPMENT, ReservationStatus.LOCKED);
        when(currentUser.id()).thenReturn(fixture.buyerId());
        CreateAfterSaleRequest request = new CreateAfterSaleRequest(
                Long.toString(fixture.orderId()), Long.toString(fixture.itemId()),
                AfterSaleType.REFUND_ONLY, 1, "QUALITY_PROBLEM", "真实创建测试", List.of(), "100.00");
        String firstKey = "create-" + shortId();
        String secondKey = "create-" + shortId();
        rememberRedisBase(fixture.buyerId(), "POST", "/api/after-sales", firstKey);
        rememberRedisBase(fixture.buyerId(), "POST", "/api/after-sales", secondKey);

        var created = afterSaleService.create(request, firstKey);

        assertThat(created.status()).isEqualTo(AfterSaleStatus.PENDING);
        assertThat(created.requestedAmount()).isEqualTo("100.00");
        assertThatThrownBy(() -> afterSaleService.create(request, secondKey))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("AFTER_SALE_NOT_ELIGIBLE"));
    }

    @Test
    @Transactional
    void refundOnlyApprovalPassesMysqlChecksAndUpdatesWalletInventoryAndOrder() {
        Fixture fixture = createOrder(OrderStatus.PENDING_SHIPMENT, ReservationStatus.LOCKED);
        AfterSaleRequest afterSale = insertPendingAfterSale(fixture, AfterSaleType.REFUND_ONLY);
        WalletAccount walletBefore = walletMapper.selectById(fixture.walletId());
        InventoryStock stockBefore = stockMapper.selectById(fixture.stockId());
        String key = "approve-" + shortId();
        String path = "/api/shops/" + fixture.shopId() + "/after-sales/" + afterSale.getId() + "/approve";
        rememberRedisBase(fixture.operatorId(), "POST", path, key);

        var result = shopAfterSaleService.approve(fixture.shopId(), afterSale.getId(),
                new ApproveAfterSaleRequest(1, "100.00", "真实 MySQL 仅退款测试", afterSale.getVersion()), key);

        AfterSaleRequest saved = afterSaleMapper.selectById(afterSale.getId());
        WalletAccount walletAfter = walletMapper.selectById(fixture.walletId());
        InventoryStock stockAfter = stockMapper.selectById(fixture.stockId());
        OrderInfo orderAfter = orderMapper.selectById(fixture.orderId());
        OrderItem itemAfter = itemMapper.selectById(fixture.itemId());

        assertThat(result.status()).isEqualTo(AfterSaleStatus.COMPLETED);
        assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(result.buyer().id()).isEqualTo(Long.toString(fixture.buyerId()));
        assertThat(result.eligibilityAtReview()).isNotNull();
        assertThat(saved.getRefundNo()).isNotBlank();
        assertThat(walletAfter.getBalance()).isEqualByComparingTo(walletBefore.getBalance().add(ITEM_AMOUNT));
        assertThat(stockAfter.getAvailableQuantity()).isEqualTo(stockBefore.getAvailableQuantity() + 1);
        assertThat(stockAfter.getLockedQuantity()).isEqualTo(stockBefore.getLockedQuantity() - 1);
        assertThat(orderAfter.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderAfter.getPaymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);
        assertThat(orderAfter.getRefundAmount()).isEqualByComparingTo(ITEM_AMOUNT);
        assertThat(itemAfter.getReservationStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(countWalletRefund(saved.getRefundNo())).isOne();
        assertThat(countInventoryFlow(saved.getAfterSaleNo(), InventoryTransactionType.RELEASE)).isOne();
    }

    @Test
    @Transactional
    void returnRefundConfirmationPassesMysqlChecksAndReturnsStockOnlyOnce() {
        Fixture fixture = createOrder(OrderStatus.PENDING_RECEIPT, ReservationStatus.DEDUCTED);
        AfterSaleRequest afterSale = insertWaitingReturnAfterSale(fixture);
        WalletAccount walletBefore = walletMapper.selectById(fixture.walletId());
        InventoryStock stockBefore = stockMapper.selectById(fixture.stockId());
        String key = "confirm-" + shortId();
        String path = "/api/shops/" + fixture.shopId() + "/after-sales/" + afterSale.getId()
                + "/confirm-return-received";
        rememberRedisBase(fixture.operatorId(), "POST", path, key);

        var result = shopAfterSaleService.confirmReturnReceived(fixture.shopId(), afterSale.getId(),
                new ConfirmReturnReceivedRequest("真实 MySQL 退货入库测试", afterSale.getVersion()), key);

        AfterSaleRequest saved = afterSaleMapper.selectById(afterSale.getId());
        WalletAccount walletAfter = walletMapper.selectById(fixture.walletId());
        InventoryStock stockAfter = stockMapper.selectById(fixture.stockId());
        OrderInfo orderAfter = orderMapper.selectById(fixture.orderId());

        assertThat(result.status()).isEqualTo(AfterSaleStatus.COMPLETED);
        assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(saved.getReturnReceivedAt()).isNotNull();
        assertThat(walletAfter.getBalance()).isEqualByComparingTo(walletBefore.getBalance().add(ITEM_AMOUNT));
        assertThat(stockAfter.getAvailableQuantity()).isEqualTo(stockBefore.getAvailableQuantity() + 1);
        assertThat(stockAfter.getLockedQuantity()).isEqualTo(stockBefore.getLockedQuantity());
        assertThat(orderAfter.getOrderStatus()).isEqualTo(OrderStatus.PENDING_RECEIPT);
        assertThat(orderAfter.getPaymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);
        assertThat(countWalletRefund(saved.getRefundNo())).isOne();
        assertThat(countInventoryFlow(saved.getAfterSaleNo(), InventoryTransactionType.RETURN)).isOne();
    }

    @Test
    @Transactional
    void failedRefundRetryClearsFailureReasonAndReusesRefundNumber() {
        Fixture fixture = createOrder(OrderStatus.PENDING_SHIPMENT, ReservationStatus.LOCKED);
        AfterSaleRequest afterSale = insertFailedRefundAfterSale(fixture);
        String originalRefundNo = afterSale.getRefundNo();
        String key = "retry-" + shortId();
        String path = "/api/shops/" + fixture.shopId() + "/after-sales/" + afterSale.getId()
                + "/refund/retry";
        rememberRedisBase(fixture.operatorId(), "POST", path, key);

        var result = shopAfterSaleService.retryRefund(fixture.shopId(), afterSale.getId(),
                new RetryRefundRequest("真实 MySQL 退款重试测试", afterSale.getVersion()), key);

        AfterSaleRequest saved = afterSaleMapper.selectById(afterSale.getId());
        assertThat(result.status()).isEqualTo(AfterSaleStatus.COMPLETED);
        assertThat(saved.getRefundNo()).isEqualTo(originalRefundNo);
        assertThat(saved.getRefundFailureReason()).isNull();
        assertThat(countWalletRefund(originalRefundNo)).isOne();
        assertThat(countInventoryFlow(saved.getAfterSaleNo(), InventoryTransactionType.RELEASE)).isOne();
    }

    private Fixture createOrder(OrderStatus status, ReservationStatus reservationStatus) {
        WalletAccount wallet = walletMapper.selectOne(new LambdaQueryWrapper<WalletAccount>()
                .eq(WalletAccount::getStatus, WalletStatus.ACTIVE)
                .orderByAsc(WalletAccount::getId).last("LIMIT 1"));
        InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .ge(InventoryStock::getAvailableQuantity, 1)
                .orderByAsc(InventoryStock::getId).last("LIMIT 1"));
        assertThat(wallet).as("真实测试库需要至少一个 ACTIVE 钱包").isNotNull();
        assertThat(stock).as("真实测试库需要至少一个可用库存").isNotNull();

        ProductSku sku = skuMapper.selectById(stock.getSkuId());
        Shop shop = shopMapper.selectById(sku.getShopId());
        SysUser operator = activeUsers().stream()
                .filter(user -> !user.getId().equals(wallet.getUserId()))
                .findFirst().orElseThrow();
        when(currentUser.id()).thenReturn(operator.getId());
        when(shopAccess.require(eq(shop.getId()), anyString())).thenReturn(shop);

        // 构造与订单状态一致的库存：待发货为 LOCKED，已发货为 DEDUCTED。
        assertThat(stockMapper.reserve(sku.getId(), 1)).isOne();
        if (reservationStatus == ReservationStatus.DEDUCTED) {
            assertThat(stockMapper.deduct(sku.getId(), 1)).isOne();
        }

        String suffix = shortId();
        TradeOrder trade = new TradeOrder();
        trade.setTradeNo("TRIT" + suffix);
        trade.setUserId(wallet.getUserId());
        trade.setTradeStatus(TradeStatus.PAID);
        trade.setPayableAmount(ITEM_AMOUNT);
        trade.setRecipientName("集成测试用户");
        trade.setRecipientPhone("13800000000");
        trade.setProvinceName("上海市");
        trade.setCityName("上海市");
        trade.setDistrictName("杨浦区");
        trade.setDetailAddress("真实基础设施测试地址");
        trade.setPayExpireAt(LocalDateTime.now().plusMinutes(30));
        trade.setPaidAt(LocalDateTime.now());
        trade.setVersion(0);
        assertThat(tradeMapper.insert(trade)).isOne();

        OrderInfo order = new OrderInfo();
        order.setOrderNo("ORIT" + suffix);
        order.setTradeId(trade.getId());
        order.setUserId(wallet.getUserId());
        order.setShopId(shop.getId());
        order.setShopName(shop.getShopName());
        order.setOrderStatus(status);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setItemAmount(ITEM_AMOUNT);
        order.setFreightAmount(new BigDecimal("0.00"));
        order.setPayableAmount(ITEM_AMOUNT);
        order.setRefundAmount(new BigDecimal("0.00"));
        if (status == OrderStatus.PENDING_RECEIPT) {
            order.setCarrierCode("SF");
            order.setCarrierName("顺丰速运");
            order.setTrackingNo("SFIT" + suffix);
            order.setShippedAt(LocalDateTime.now().minusDays(1));
        }
        order.setVersion(0);
        assertThat(orderMapper.insert(order)).isOne();

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setShopId(shop.getId());
        item.setSpuId(sku.getSpuId());
        item.setSkuId(sku.getId());
        item.setSpuNo("SPU-IT");
        item.setSkuNo(sku.getSkuNo());
        item.setProductName("售后集成测试商品");
        item.setSkuName(sku.getSkuName());
        item.setSpecJson(sku.getSpecJson() == null ? Map.of() : sku.getSpecJson());
        item.setImageUrl(sku.getImageUrl());
        item.setUnitPrice(ITEM_AMOUNT);
        item.setQuantity(1);
        item.setOriginalAmount(ITEM_AMOUNT);
        item.setFreightAmount(new BigDecimal("0.00"));
        item.setPayableAmount(ITEM_AMOUNT);
        item.setRefundedQuantity(0);
        item.setRefundedAmount(new BigDecimal("0.00"));
        item.setReservationStatus(reservationStatus);
        assertThat(itemMapper.insert(item)).isOne();

        InventoryStock currentStock = stockMapper.selectById(stock.getId());
        return new Fixture(wallet.getUserId(), operator.getId(), wallet.getId(), shop.getId(),
                currentStock.getId(), order.getId(), item.getId());
    }

    private AfterSaleRequest insertPendingAfterSale(Fixture fixture, AfterSaleType type) {
        AfterSaleRequest value = baseAfterSale(fixture, type);
        value.setStatus(AfterSaleStatus.PENDING);
        value.setRefundStatus(RefundStatus.NOT_STARTED);
        assertThat(afterSaleMapper.insert(value)).isOne();
        return value;
    }

    private AfterSaleRequest insertWaitingReturnAfterSale(Fixture fixture) {
        AfterSaleRequest value = baseAfterSale(fixture, AfterSaleType.RETURN_REFUND);
        value.setApprovedQuantity(1);
        value.setApprovedAmount(ITEM_AMOUNT);
        value.setStatus(AfterSaleStatus.WAITING_RETURN);
        value.setReviewerId(fixture.operatorId());
        value.setReviewComment("同意退货退款");
        value.setReviewedAt(LocalDateTime.now().minusHours(2));
        value.setReturnCarrierCode("SF");
        value.setReturnCarrierName("顺丰速运");
        value.setReturnTrackingNo("RETURN" + shortId());
        value.setReturnedAt(LocalDateTime.now().minusHours(1));
        value.setRefundStatus(RefundStatus.NOT_STARTED);
        assertThat(afterSaleMapper.insert(value)).isOne();
        return value;
    }

    private AfterSaleRequest insertFailedRefundAfterSale(Fixture fixture) {
        AfterSaleRequest value = baseAfterSale(fixture, AfterSaleType.REFUND_ONLY);
        value.setApprovedQuantity(1);
        value.setApprovedAmount(ITEM_AMOUNT);
        value.setStatus(AfterSaleStatus.REFUNDING);
        value.setReviewerId(fixture.operatorId());
        value.setReviewComment("首次退款失败");
        value.setReviewedAt(LocalDateTime.now().minusHours(1));
        value.setRefundNo("RFIT" + shortId());
        value.setRefundStatus(RefundStatus.FAILED);
        value.setRefundFailureReason("模拟依赖不可用");
        assertThat(afterSaleMapper.insert(value)).isOne();
        return value;
    }

    private AfterSaleRequest baseAfterSale(Fixture fixture, AfterSaleType type) {
        AfterSaleRequest value = new AfterSaleRequest();
        value.setAfterSaleNo("ASIT" + shortId());
        value.setOrderId(fixture.orderId());
        value.setOrderItemId(fixture.itemId());
        value.setUserId(fixture.buyerId());
        value.setRequestType(type);
        value.setQuantity(1);
        value.setReasonCode("QUALITY_PROBLEM");
        value.setReasonDescription("真实基础设施测试");
        value.setEvidenceJson(List.of());
        value.setRequestedAmount(ITEM_AMOUNT);
        value.setVersion(0);
        return value;
    }

    private List<SysUser> activeUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, UserStatus.ACTIVE)
                .orderByAsc(SysUser::getId));
    }

    private long countWalletRefund(String refundNo) {
        return walletTransactionMapper.selectCount(new LambdaQueryWrapper<WalletTransaction>()
                .eq(WalletTransaction::getBusinessType, "AFTER_SALE_REFUND")
                .eq(WalletTransaction::getBusinessNo, refundNo));
    }

    private long countInventoryFlow(String afterSaleNo, InventoryTransactionType type) {
        return inventoryTransactionMapper.selectCount(new LambdaQueryWrapper<InventoryTransaction>()
                .eq(InventoryTransaction::getTransactionType, type)
                .eq(InventoryTransaction::getBusinessType, "AFTER_SALE")
                .eq(InventoryTransaction::getBusinessNo, afterSaleNo));
    }

    private void rememberRedisBase(long userId, String method, String path, String key) {
        redisBases.add("market:idem:" + userId + ":" + method + ":" + path + ":" + key);
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(long buyerId, long operatorId, long walletId, long shopId,
                           long stockId, long orderId, long itemId) {
    }
}
