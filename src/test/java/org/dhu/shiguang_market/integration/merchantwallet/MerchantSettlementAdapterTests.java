package org.dhu.shiguang_market.integration.merchantwallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.SettlementStatus;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionAllocationMapper;
import org.dhu.shiguang_market.merchantwallet.mapper.MerchantWalletAccountMapper;
import org.dhu.shiguang_market.merchantwallet.mapper.MerchantWalletTransactionMapper;
import org.dhu.shiguang_market.merchantwallet.mapper.ShopSettlementMapper;
import org.dhu.shiguang_market.merchantwallet.model.MerchantWalletAccount;
import org.dhu.shiguang_market.merchantwallet.model.ShopSettlement;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MerchantSettlementAdapterTests {
    private static final long SHOP_ID = 10L;
    private static final long ORDER_ID = 20L;
    private static final BigDecimal REFUND_AMOUNT = new BigDecimal("100.00");

    private final MerchantWalletAccountMapper walletMapper = mock(MerchantWalletAccountMapper.class);
    private final ShopSettlementMapper settlementMapper = mock(ShopSettlementMapper.class);
    private final NumberGenerator numbers = mock(NumberGenerator.class);
    private final MerchantWalletTransactionMapper transactionMapper = mock(MerchantWalletTransactionMapper.class);
    private final CouponRedemptionAllocationMapper couponAllocations = mock(CouponRedemptionAllocationMapper.class);

    private MerchantSettlementAdapter adapter;
    private MerchantWalletAccount wallet;
    private OrderInfo order;

    @BeforeEach
    void setUp() {
        adapter = new MerchantSettlementAdapter(walletMapper, settlementMapper, numbers,
                transactionMapper, couponAllocations);
        wallet = new MerchantWalletAccount();
        wallet.setId(1L);
        wallet.setShopId(SHOP_ID);
        wallet.setStatus(MerchantWalletStatus.ACTIVE);
        wallet.setPendingBalance(REFUND_AMOUNT);
        wallet.setAvailableBalance(BigDecimal.ZERO.setScale(2));
        wallet.setFrozenBalance(BigDecimal.ZERO.setScale(2));
        wallet.setLifetimeRefund(BigDecimal.ZERO.setScale(2));

        order = new OrderInfo();
        order.setId(ORDER_ID);
        order.setShopId(SHOP_ID);

        when(transactionMapper.selectCount(any())).thenReturn(0L);
        when(transactionMapper.selectOne(any())).thenReturn(null);
        when(walletMapper.selectByShopIdForUpdate(SHOP_ID)).thenReturn(wallet);
        when(numbers.next("MWT")).thenReturn("MWT001");
    }

    @ParameterizedTest
    @MethodSource("unsettledStatuses")
    void fullRefundBeforeSettlementDoesNotInventSettledTime(SettlementStatus status,
                                                             LocalDateTime availableAt) {
        ShopSettlement settlement = settlement(status, availableAt, null,
                REFUND_AMOUNT, BigDecimal.ZERO.setScale(2));
        when(settlementMapper.selectByOrderAndShopForUpdate(ORDER_ID, SHOP_ID)).thenReturn(settlement);

        boolean recorded = adapter.recordMerchantRefund(order, REFUND_AMOUNT, "RF001", 99L);

        assertThat(recorded).isTrue();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REFUNDED);
        assertThat(settlement.getSettledAt()).isNull();
    }

    @ParameterizedTest
    @MethodSource("settledStatus")
    void fullRefundAfterSettlementPreservesOriginalSettledTime(LocalDateTime availableAt,
                                                                LocalDateTime settledAt) {
        wallet.setPendingBalance(BigDecimal.ZERO.setScale(2));
        wallet.setAvailableBalance(REFUND_AMOUNT);
        ShopSettlement settlement = settlement(SettlementStatus.SETTLED, availableAt, settledAt,
                BigDecimal.ZERO.setScale(2), REFUND_AMOUNT);
        when(settlementMapper.selectByOrderAndShopForUpdate(ORDER_ID, SHOP_ID)).thenReturn(settlement);

        boolean recorded = adapter.recordMerchantRefund(order, REFUND_AMOUNT, "RF002", 99L);

        assertThat(recorded).isTrue();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REFUNDED);
        assertThat(settlement.getSettledAt()).isEqualTo(settledAt);
    }

    private static Stream<Arguments> unsettledStatuses() {
        return Stream.of(
                Arguments.of(SettlementStatus.PENDING, null),
                Arguments.of(SettlementStatus.READY, LocalDateTime.now().plusDays(7)));
    }

    private static Stream<Arguments> settledStatus() {
        LocalDateTime availableAt = LocalDateTime.now().minusDays(1);
        return Stream.of(Arguments.of(availableAt, availableAt.plusHours(1)));
    }

    private ShopSettlement settlement(SettlementStatus status, LocalDateTime availableAt,
                                      LocalDateTime settledAt, BigDecimal pendingAmount,
                                      BigDecimal releasedAmount) {
        ShopSettlement settlement = new ShopSettlement();
        settlement.setId(2L);
        settlement.setShopId(SHOP_ID);
        settlement.setOrderId(ORDER_ID);
        settlement.setStatus(status);
        settlement.setBuyerRefundAmount(BigDecimal.ZERO.setScale(2));
        settlement.setPlatformSubsidyRefundAmount(BigDecimal.ZERO.setScale(2));
        settlement.setCommissionRefundAmount(BigDecimal.ZERO.setScale(2));
        settlement.setMerchantRefundAmount(BigDecimal.ZERO.setScale(2));
        settlement.setNetAmount(REFUND_AMOUNT);
        settlement.setPendingAmount(pendingAmount);
        settlement.setReleasedAmount(releasedAmount);
        settlement.setAvailableAt(availableAt);
        settlement.setSettledAt(settledAt);
        return settlement;
    }
}
