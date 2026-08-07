package org.dhu.shiguang_market.phasesix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus;
import org.dhu.shiguang_market.integration.order.ActiveShopBusinessAdapter;
import org.dhu.shiguang_market.integration.order.LockedReservationQueryAdapter;
import org.dhu.shiguang_market.integration.payment.WalletProvisionAdapter;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.model.WalletAccount;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 阶段六：B 线提供给 A 线的三个正式端口测试。 */
@ExtendWith(MockitoExtension.class)
class PhaseSixProvidedPortTests {
    @Mock private WalletAccountMapper walletMapper;
    @Mock private OrderInfoMapper orderMapper;
    @Mock private AfterSaleRequestMapper afterSaleMapper;
    @Mock private OrderItemMapper itemMapper;

    @BeforeEach
    void initializeMybatisMetadata() {
        // 纯 Mockito 测试不会启动 MyBatis，上层 Lambda 查询需要显式初始化实体元数据。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "phase-six-provided-port-test");
        TableInfoHelper.initTableInfo(assistant, WalletAccount.class);
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
        TableInfoHelper.initTableInfo(assistant, AfterSaleRequest.class);
    }

    /** 首次注册用户时创建 ACTIVE 零余额钱包，并返回内部端口值对象。 */
    @Test
    void walletProvisionCreatesZeroBalanceWallet() {
        doAnswer(invocation -> {
            WalletAccount wallet = invocation.getArgument(0);
            wallet.setId(11L);
            return 1;
        }).when(walletMapper).insert(any(WalletAccount.class));

        var result = new WalletProvisionAdapter(walletMapper).provision(7L);

        assertEquals(11L, result.walletId());
        assertEquals(7L, result.userId());
        assertEquals(new BigDecimal("0.00"), result.balance());
        assertEquals(WalletStatus.ACTIVE, result.status());
        verify(walletMapper).insert(any(WalletAccount.class));
    }

    /** 重复调用时直接返回已有钱包，不重复插入。 */
    @Test
    void walletProvisionReturnsExistingWallet() {
        WalletAccount existing = new WalletAccount();
        existing.setId(12L);
        existing.setUserId(7L);
        existing.setBalance(new BigDecimal("8.50"));
        existing.setStatus(WalletStatus.ACTIVE);
        when(walletMapper.selectOne(any())).thenReturn(existing);

        var result = new WalletProvisionAdapter(walletMapper).provision(7L);

        assertEquals(12L, result.walletId());
        assertEquals(new BigDecimal("8.50"), result.balance());
        verify(walletMapper, never()).insert(any(WalletAccount.class));
    }

    /** 未完结订单可直接判定店铺存在活跃业务，无需继续扫描售后表。 */
    @Test
    void activeShopBusinessFindsOpenOrderFirst() {
        when(orderMapper.exists(any())).thenReturn(true);

        boolean active = new ActiveShopBusinessAdapter(orderMapper, afterSaleMapper)
                .hasActiveBusiness(3L);

        assertTrue(active);
        verify(afterSaleMapper, never()).selectCount(any());
    }

    /** 没有未完结订单时继续检查活跃售后；两者都没有则返回 false。 */
    @Test
    void activeShopBusinessChecksAfterSale() {
        when(orderMapper.exists(any())).thenReturn(false);
        when(afterSaleMapper.selectCount(any())).thenReturn(1L, 0L);
        ActiveShopBusinessAdapter adapter = new ActiveShopBusinessAdapter(orderMapper, afterSaleMapper);

        assertTrue(adapter.hasActiveBusiness(3L));
        assertFalse(adapter.hasActiveBusiness(4L));
    }

    /** 锁定预占查询端口直接返回指定 SKU 的 LOCKED 数量汇总。 */
    @Test
    void lockedReservationQueryReturnsMapperAggregation() {
        when(itemMapper.sumLockedQuantityBySkuId(20L)).thenReturn(6);

        int quantity = new LockedReservationQueryAdapter(itemMapper).lockedQuantity(20L);

        assertEquals(6, quantity);
    }
}
