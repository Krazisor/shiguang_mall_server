package org.dhu.shiguang_market.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.dhu.shiguang_market.cart.dto.CartDtos.UpdateCartItemRequest;
import org.dhu.shiguang_market.cart.mapper.CartItemMapper;
import org.dhu.shiguang_market.cart.model.CartItem;
import org.dhu.shiguang_market.cart.service.CartService;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.service.AddressService;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartServiceTests {
    private final CartItemMapper cartMapper = mock(CartItemMapper.class);
    private final ProductSkuMapper skuMapper = mock(ProductSkuMapper.class);
    private final ProductSpuMapper spuMapper = mock(ProductSpuMapper.class);
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final ShopMapper shopMapper = mock(ShopMapper.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(cartMapper, skuMapper, spuMapper, stockMapper, shopMapper,
                mock(AddressService.class), currentUser);
        when(currentUser.id()).thenReturn(100L);
    }

    @Test
    void patchRejectsExplicitNullInsteadOfTreatingItAsMissing() throws Exception {
        UpdateCartItemRequest request = new ObjectMapper().readValue("{\"quantity\":null}", UpdateCartItemRequest.class);

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_FAILED");
                    assertThat(ex.getMessage()).contains("quantity 不允许为 null");
                });
        verifyNoInteractions(skuMapper, spuMapper, stockMapper, shopMapper);
    }

    @Test
    void invalidCartItemCannotBeSelected() throws Exception {
        CartItem item = new CartItem();
        item.setId(10L);
        item.setUserId(100L);
        item.setSkuId(20L);
        item.setQuantity(1);
        item.setSelected(false);
        ProductSku sku = new ProductSku();
        sku.setId(20L);
        sku.setSpuId(30L);
        sku.setShopId(40L);
        sku.setStatus(EnabledStatus.ENABLED);
        sku.setSalePrice(new BigDecimal("10.00"));
        ProductSpu spu = new ProductSpu();
        spu.setId(30L);
        spu.setStatus(ProductStatus.OFF_SHELF);
        Shop shop = new Shop();
        shop.setId(40L);
        shop.setStatus(ShopStatus.ACTIVE);
        InventoryStock stock = new InventoryStock();
        stock.setSkuId(20L);
        stock.setAvailableQuantity(10);
        when(cartMapper.selectOne(any())).thenReturn(item);
        when(skuMapper.selectById(20L)).thenReturn(sku);
        when(spuMapper.selectById(30L)).thenReturn(spu);
        when(shopMapper.selectById(40L)).thenReturn(shop);
        when(stockMapper.selectOne(any())).thenReturn(stock);
        UpdateCartItemRequest request = new ObjectMapper().readValue("{\"selected\":true}", UpdateCartItemRequest.class);

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("CHECKOUT_ITEMS_INVALID");
                    assertThat(ex.getMessage()).contains("失效购物车项不可选中");
                });
    }
}
