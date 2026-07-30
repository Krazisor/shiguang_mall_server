package org.dhu.shiguang_market.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryInboundRequest;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.service.InventoryService;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.product.service.ShopProductService;
import org.dhu.shiguang_market.shop.model.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceTests {
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final InventoryTransactionMapper transactionMapper = mock(InventoryTransactionMapper.class);
    private final ProductSkuMapper skuMapper = mock(ProductSkuMapper.class);
    private final ProductSpuMapper spuMapper = mock(ProductSpuMapper.class);
    private final ShopProductService productService = mock(ShopProductService.class);
    private final ShopAccessService shopAccess = mock(ShopAccessService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final NumberGenerator numbers = mock(NumberGenerator.class);

    private InventoryService service;
    private Shop closedShop;

    @BeforeEach
    void setUp() {
        service = new InventoryService(stockMapper, transactionMapper, skuMapper, spuMapper,
                productService, shopAccess, currentUser, idempotency, numbers);
        closedShop = new Shop();
        closedShop.setId(10L);
        closedShop.setStatus(ShopStatus.CLOSED);
        when(shopAccess.require(10L, "shop:inventory:manage")).thenReturn(closedShop);
    }

    @Test
    void closedShopAllowsHistoricalInventoryReads() {
        ProductSku sku = new ProductSku();
        sku.setId(20L);
        sku.setSpuId(30L);
        ProductSpu spu = new ProductSpu();
        spu.setId(30L);
        spu.setSpuNo("SPU-30");
        spu.setProductName("历史商品");
        when(skuMapper.selectOne(any())).thenReturn(sku);
        when(spuMapper.selectById(30L)).thenReturn(spu);

        var result = service.detail(10L, 20L);

        assertThat(result.spuId()).isEqualTo("30");
        assertThat(result.productName()).isEqualTo("历史商品");
    }

    @Test
    void closedShopRejectsInventoryWrites() {
        assertThatThrownBy(() -> service.inbound(
                10L, 20L, new InventoryInboundRequest(1, null), "closed-shop-inbound"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("STATE_CONFLICT");
                    assertThat(ex.getMessage()).isEqualTo("已关闭店铺仅允许读取历史数据");
                });
        verifyNoInteractions(currentUser, idempotency, stockMapper, transactionMapper);
    }

    @Test
    void inventoryFiltersAreAppliedBeforePagination() {
        Page<ProductSku> databasePage = Page.of(2, 10, 37);
        when(skuMapper.selectInventoryPage(any(), eq(10L), isNull(), eq("phone"), eq("LOW_STOCK")))
                .thenReturn(databasePage);

        var result = service.list(10L, " phone ", null, " LOW_STOCK ", 2, 10);

        assertThat(result.total()).isEqualTo(37);
        assertThat(result.items()).isEmpty();
        verify(skuMapper).selectInventoryPage(any(), eq(10L), isNull(), eq("phone"), eq("LOW_STOCK"));
    }

    @Test
    void invalidStockStateIsRejectedBeforeQuerying() {
        assertThatThrownBy(() -> service.list(10L, null, null, "UNKNOWN", 1, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("BAD_REQUEST"));
        verifyNoInteractions(skuMapper);
    }
}
