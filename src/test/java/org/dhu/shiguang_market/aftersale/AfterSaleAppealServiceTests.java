package org.dhu.shiguang_market.aftersale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.DecideAfterSaleAppealRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.PlatformAfterSaleAppealDetailView;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleAppealMapper;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.mapper.MerchantNotificationMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleAppeal;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.aftersale.service.AfterSaleAppealService;
import org.dhu.shiguang_market.aftersale.service.ShopAfterSaleService;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealDecision;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.mapper.ShopUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AfterSaleAppealServiceTests {
    private static final long APPEAL_ID = 10L;
    private static final long AFTER_SALE_ID = 20L;
    private static final long ITEM_ID = 30L;
    private static final long SHOP_ID = 40L;
    private static final long OPERATOR_ID = 50L;

    private final AfterSaleAppealMapper appealMapper = mock(AfterSaleAppealMapper.class);
    private final AfterSaleRequestMapper afterSaleMapper = mock(AfterSaleRequestMapper.class);
    private final MerchantNotificationMapper notificationMapper = mock(MerchantNotificationMapper.class);
    private final OrderInfoMapper orderMapper = mock(OrderInfoMapper.class);
    private final OrderItemMapper itemMapper = mock(OrderItemMapper.class);
    private final ShopMapper shopMapper = mock(ShopMapper.class);
    private final ShopUserMapper shopUserMapper = mock(ShopUserMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final ShopAccessService shopAccess = mock(ShopAccessService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final NumberGenerator numbers = mock(NumberGenerator.class);
    private final ContentSafety contentSafety = mock(ContentSafety.class);
    private final ShopAfterSaleService shopAfterSaleService = mock(ShopAfterSaleService.class);

    private AfterSaleAppeal appeal;
    private AfterSaleRequest afterSale;
    private OrderItem item;
    private AfterSaleAppealService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "after-sale-appeal-service-test");
        TableInfoHelper.initTableInfo(assistant, AfterSaleAppeal.class);
        TableInfoHelper.initTableInfo(assistant, AfterSaleRequest.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new AfterSaleAppealService(appealMapper, afterSaleMapper, notificationMapper,
                orderMapper, itemMapper, shopMapper, shopUserMapper, userMapper, currentUser,
                shopAccess, idempotency, numbers, contentSafety, shopAfterSaleService);

        appeal = new AfterSaleAppeal();
        appeal.setId(APPEAL_ID);
        appeal.setAppealNo("AP001");
        appeal.setAfterSaleId(AFTER_SALE_ID);
        appeal.setShopId(SHOP_ID);
        appeal.setStatus(AfterSaleAppealStatus.PENDING);
        appeal.setVersion(0);

        afterSale = new AfterSaleRequest();
        afterSale.setId(AFTER_SALE_ID);
        afterSale.setOrderItemId(ITEM_ID);
        afterSale.setRequestType(AfterSaleType.REFUND_ONLY);
        afterSale.setQuantity(2);
        afterSale.setRequestedAmount(new BigDecimal("200.00"));
        afterSale.setStatus(AfterSaleStatus.REJECTED);
        afterSale.setRefundStatus(RefundStatus.NOT_STARTED);
        afterSale.setVersion(0);

        item = new OrderItem();
        item.setId(ITEM_ID);
        item.setQuantity(2);
        item.setPayableAmount(new BigDecimal("200.00"));
        item.setRefundedQuantity(0);
        item.setRefundedAmount(BigDecimal.ZERO);

        when(currentUser.id()).thenReturn(OPERATOR_ID);
        when(appealMapper.selectOne(any())).thenReturn(appeal);
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(afterSaleMapper.selectList(any())).thenReturn(List.of());
        when(shopUserMapper.selectActiveUserIdsByPermission(SHOP_ID, "shop:after-sale:manage"))
                .thenReturn(List.of());
        when(numbers.next("RF")).thenReturn("RF001");
        when(idempotency.execute(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<PlatformAfterSaleAppealDetailView> action = invocation.getArgument(6);
                    return action.get();
                });
    }

    @Test
    void approveRefundOnlyCreatesRefundNumberBeforeProcessingStateIsStored() {
        when(afterSaleMapper.updateById(any(AfterSaleRequest.class))).thenAnswer(invocation -> {
            AfterSaleRequest updated = invocation.getArgument(0);
            if (updated.getRefundStatus() == RefundStatus.PROCESSING && updated.getRefundNo() == null) {
                throw new IllegalStateException("database requires refund_no for PROCESSING");
            }
            return 1;
        });
        doAnswer(invocation -> {
            assertThat(afterSale.getRefundNo()).isEqualTo("RF001");
            throw new RefundDispatchReached();
        }).when(shopAfterSaleService).executePlatformRefund(SHOP_ID, AFTER_SALE_ID, OPERATOR_ID);

        assertThatThrownBy(() -> service.decide(APPEAL_ID, approve(1, "100.00"), "key-1"))
                .isInstanceOf(RefundDispatchReached.class);

        verify(afterSaleMapper).updateById(same(afterSale));
        verify(shopAfterSaleService).executePlatformRefund(SHOP_ID, AFTER_SALE_ID, OPERATOR_ID);
    }

    @Test
    void approveRejectsAmountAndQuantityAlreadyRefundedFromOrderItem() {
        item.setRefundedQuantity(2);
        item.setRefundedAmount(new BigDecimal("200.00"));

        assertApprovalExceeded();
    }

    @Test
    void approveRejectsAllowanceOccupiedByAnotherActiveAfterSale() {
        AfterSaleRequest other = new AfterSaleRequest();
        other.setId(99L);
        other.setApprovedQuantity(2);
        other.setApprovedAmount(new BigDecimal("200.00"));
        other.setStatus(AfterSaleStatus.WAITING_RETURN);
        when(afterSaleMapper.selectList(any())).thenReturn(List.of(other));

        assertApprovalExceeded();
    }

    private void assertApprovalExceeded() {
        assertThatThrownBy(() -> service.decide(APPEAL_ID, approve(1, "100.00"), "key-2"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("AFTER_SALE_APPROVAL_EXCEEDED"));
        verify(appealMapper, never()).updateById(any(AfterSaleAppeal.class));
        verify(afterSaleMapper, never()).updateById(any(AfterSaleRequest.class));
        verify(shopAfterSaleService, never()).executePlatformRefund(anyLong(), anyLong(), anyLong());
    }

    private DecideAfterSaleAppealRequest approve(int quantity, String amount) {
        return new DecideAfterSaleAppealRequest(AfterSaleAppealDecision.APPROVE,
                quantity, amount, "平台同意申诉", 0);
    }

    private static final class RefundDispatchReached extends RuntimeException {
    }
}
