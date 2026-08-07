package org.dhu.shiguang_market.phasefour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dhu.shiguang_market.common.model.MarketEnums.TransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletTransactionType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.mapper.WalletTransactionMapper;
import org.dhu.shiguang_market.payment.model.WalletAccount;
import org.dhu.shiguang_market.payment.model.WalletTransaction;
import org.dhu.shiguang_market.payment.service.WalletService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 阶段四钱包退款入账测试。 */
class WalletRefundTests {
    private final WalletAccountMapper walletMapper = mock(WalletAccountMapper.class);
    private final WalletTransactionMapper transactionMapper = mock(WalletTransactionMapper.class);
    private final NumberGenerator numbers = mock(NumberGenerator.class);
    private WalletService service;

    @BeforeAll
    static void initializeLambdaMetadata() {
        // refund() 使用 Lambda 条件按业务编号查重，并按用户锁定钱包。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "phase-four-test");
        TableInfoHelper.initTableInfo(assistant, WalletAccount.class);
        TableInfoHelper.initTableInfo(assistant, WalletTransaction.class);
    }

    @BeforeEach
    void setUp() {
        service = new WalletService(walletMapper, transactionMapper,
                mock(CurrentUserService.class), mock(IdempotencyService.class), numbers);
    }

    /** 退款应增加钱包余额，并记录一条 REFUND/CREDIT 流水。 */
    @Test
    void refundCreditsWalletAndWritesTransaction() {
        WalletAccount before = wallet(1L, 101L, "100.00");
        WalletAccount after = wallet(1L, 101L, "120.00");
        when(walletMapper.selectOne(any())).thenReturn(before);
        when(walletMapper.credit(101L, new BigDecimal("20.00"))).thenReturn(1);
        when(walletMapper.selectById(1L)).thenReturn(after);
        when(numbers.next("WT")).thenReturn("WT10001");
        when(transactionMapper.insert(any(WalletTransaction.class))).thenReturn(1);

        service.refund(101L, new BigDecimal("20.00"), "RF10001", "AFTER_SALE_REFUND");

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        WalletTransaction transaction = captor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo(WalletTransactionType.REFUND);
        assertThat(transaction.getDirection()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(transaction.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("120.00");
        assertThat(transaction.getBusinessType()).isEqualTo("AFTER_SALE_REFUND");
        assertThat(transaction.getBusinessNo()).isEqualTo("RF10001");
    }

    /** 相同业务类型和退款编号已存在时直接返回，防止钱包重复入账。 */
    @Test
    void repeatedRefundDoesNotCreditWalletTwice() {
        WalletTransaction existing = new WalletTransaction();
        existing.setBusinessType("AFTER_SALE_REFUND");
        existing.setBusinessNo("RF10001");
        when(transactionMapper.selectOne(any())).thenReturn(existing);

        service.refund(101L, new BigDecimal("20.00"), "RF10001", "AFTER_SALE_REFUND");

        verify(walletMapper, never()).credit(any(Long.class), any(BigDecimal.class));
        verify(transactionMapper, never()).insert(any(WalletTransaction.class));
    }

    private static WalletAccount wallet(long id, long userId, String balance) {
        WalletAccount wallet = new WalletAccount();
        wallet.setId(id);
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal(balance));
        wallet.setStatus(WalletStatus.ACTIVE);
        return wallet;
    }
}
