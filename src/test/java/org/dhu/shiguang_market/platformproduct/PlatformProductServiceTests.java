package org.dhu.shiguang_market.platformproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.product.mapper.ProductAttributeValueMapper;
import org.dhu.shiguang_market.product.mapper.ProductBrandMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryAttributeMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.mapper.ProductStatusHistoryMapper;
import org.dhu.shiguang_market.product.model.ProductCategory;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.product.service.PlatformProductService;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformProductServiceTests {
    @Mock ProductSpuMapper spuMapper;
    @Mock ProductSkuMapper skuMapper;
    @Mock ProductCategoryMapper categoryMapper;
    @Mock ProductCategoryAttributeMapper attributeMapper;
    @Mock ProductAttributeValueMapper valueMapper;
    @Mock ProductBrandMapper brandMapper;
    @Mock ProductStatusHistoryMapper historyMapper;
    @Mock ShopMapper shopMapper;
    @Mock SysUserMapper userMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock CurrentUserService currentUser;

    private PlatformProductService service;

    @BeforeEach
    void setUp() {
        service = new PlatformProductService(spuMapper, skuMapper, categoryMapper, attributeMapper,
                valueMapper, brandMapper, historyMapper, shopMapper, userMapper, stockMapper, currentUser);
    }

    @Test
    void listUsesPlatformReadPermissionAndReturnsCrossShopSummary() {
        ProductSpu spu = spu(10L, 20L, ProductStatus.ON_SHELF);
        Shop shop = shop(20L);
        ProductCategory category = category(30L);
        when(spuMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ProductSpu> page = invocation.getArgument(0);
            page.setRecords(List.of(spu));
            page.setTotal(1);
            return page;
        });
        when(shopMapper.selectById(20L)).thenReturn(shop);
        when(categoryMapper.selectById(30L)).thenReturn(category);
        when(skuMapper.selectList(any())).thenReturn(List.of());

        var result = service.list(null, null, null, null, null, "", 1, 20, "updatedAt,desc");

        verify(currentUser).requirePermission("platform:product:read");
        assertThat(result.total()).isOne();
        assertThat(result.items()).extracting(item -> item.spuNo()).containsExactly("SPU-10");
        assertThat(result.items().getFirst().shop().id()).isEqualTo("20");
    }

    @Test
    void detailAndHistoryUseReadPermissionAndRejectMissingProduct() {
        when(spuMapper.selectById(99L)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOf(org.dhu.shiguang_market.common.exception.BusinessException.class)
                .hasMessageContaining("商品不存在");
        verify(currentUser).requirePermission("platform:product:read");
    }

    private ProductSpu spu(long id, long shopId, ProductStatus status) {
        ProductSpu value = new ProductSpu();
        value.setId(id);
        value.setShopId(shopId);
        value.setCategoryId(30L);
        value.setSpuNo("SPU-" + id);
        value.setProductName("测试商品");
        value.setStatus(status);
        value.setContentVersion(1);
        value.setCreatedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        return value;
    }

    private Shop shop(long id) {
        Shop value = new Shop();
        value.setId(id);
        value.setShopNo("SHOP-" + id);
        value.setShopName("测试店铺");
        value.setStatus(ShopStatus.ACTIVE);
        return value;
    }

    private ProductCategory category(long id) {
        ProductCategory value = new ProductCategory();
        value.setId(id);
        value.setCategoryCode("TEST");
        value.setCategoryName("测试类目");
        value.setStatus(EnabledStatus.ENABLED);
        return value;
    }
}
