package org.dhu.shiguang_market.inventoryadjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.inventory.controller.InventoryAdjustmentController;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryAdjustmentRequest;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductCategory;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存调整与流水集成测试。
 *
 * <p>测试使用真实 Controller、Service、Mapper 和 MySQL；每个用例结束后事务自动回滚，
 * 可以直接运行，不会在开发数据库中留下测试库存或流水。</p>
 */
@SpringBootTest
@Transactional
class InventoryAdjustmentIntegrationTests {
    @Autowired private InventoryAdjustmentController controller;
    @Autowired private InventoryStockMapper stockMapper;
    @Autowired private InventoryTransactionMapper transactionMapper;
    @Autowired private ProductSkuMapper skuMapper;
    @Autowired private ProductSpuMapper spuMapper;
    @Autowired private ProductCategoryMapper categoryMapper;
    @Autowired private ShopMapper shopMapper;
    @Autowired private SysUserMapper userMapper;

    @MockitoBean private ShopAccessService shopAccess;
    @MockitoBean private CurrentUserService currentUser;
    /** 防止测试启动定时任务，避免访问库存调整以外的业务数据。 */
    @MockitoBean private TaskExecutionService taskExecutionService;

    private SysUser operator;
    private Shop shop;
    private ProductSku sku;
    private InventoryStock stock;

    @BeforeEach
    void setUp() {
        operator = insertUser();
        shop = insertShop();
        ProductCategory category = insertCategory();
        ProductSpu spu = insertSpu(category);
        sku = insertSku(spu);
        stock = insertStock(sku, 20, 3);
        when(shopAccess.require(shop.getId(), "shop:inventory:manage")).thenReturn(shop);
        when(currentUser.id()).thenReturn(operator.getId());
    }

    /** 流水列表应按店铺和筛选条件查询，并返回变化前后的库存及操作人。 */
    @Test
    void transactionListConnectsControllerServiceAndMapper() {
        insertTransaction(InventoryTransactionType.ADJUST, -2, 0,
                18, 3, "MANUAL_ADJUSTMENT", "IA-" + suffix(), "盘点调整");

        var page = controller.transactions(shop.getId(), sku.getId(),
                InventoryTransactionType.ADJUST, "MANUAL_ADJUSTMENT", null,
                null, null, 1, 20).data();

        assertThat(page.total()).isOne();
        assertThat(page.items()).hasSize(1);
        var item = page.items().getFirst();
        assertThat(item.availableBefore()).isEqualTo(20);
        assertThat(item.availableAfter()).isEqualTo(18);
        assertThat(item.operator().username()).isEqualTo(operator.getUsername());
    }

    /** 人工调整应在同一事务内更新库存版本，并写入一条可追溯的 ADJUST 流水。 */
    @Test
    void adjustmentConnectsControllerServiceAndMapper() {
        var response = controller.adjust(shop.getId(), sku.getId(),
                new InventoryAdjustmentRequest(-2, 1, stock.getVersion(), "盘点发现库存差异"),
                "inventory-adjust-" + suffix());

        assertThat(response.getBody()).isNotNull();
        var result = response.getBody().data();
        InventoryStock saved = stockMapper.selectById(stock.getId());
        assertThat(result.transactionType()).isEqualTo(InventoryTransactionType.ADJUST);
        assertThat(saved.getAvailableQuantity()).isEqualTo(18);
        assertThat(saved.getLockedQuantity()).isEqualTo(4);
        assertThat(saved.getVersion()).isEqualTo(1);

        InventoryTransaction transaction = transactionMapper.selectOne(
                new LambdaQueryWrapper<InventoryTransaction>()
                        .eq(InventoryTransaction::getSkuId, sku.getId())
                        .eq(InventoryTransaction::getTransactionType, InventoryTransactionType.ADJUST));
        assertThat(transaction.getBusinessType()).isEqualTo("MANUAL_ADJUSTMENT");
        assertThat(transaction.getRemark()).isEqualTo("盘点发现库存差异");
    }

    /** 调整后的可用库存不能为负，失败时不得更新库存或写入流水。 */
    @Test
    void adjustmentRejectsNegativeResult() {
        assertThatThrownBy(() -> controller.adjust(shop.getId(), sku.getId(),
                new InventoryAdjustmentRequest(-21, 0, stock.getVersion(), "错误盘点数据"),
                "inventory-negative-" + suffix()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("INVENTORY_ADJUSTMENT_NEGATIVE_RESULT"));

        assertThat(stockMapper.selectById(stock.getId()).getAvailableQuantity()).isEqualTo(20);
        assertThat(transactionMapper.selectList(new LambdaQueryWrapper<InventoryTransaction>()
                .eq(InventoryTransaction::getSkuId, sku.getId()))).isEmpty();
    }

    private SysUser insertUser() {
        SysUser value = new SysUser();
        value.setUsername("inventory_" + suffix());
        value.setPasswordHash("integration-test-only");
        value.setNickname("库存测试员");
        value.setStatus(UserStatus.ACTIVE);
        assertThat(userMapper.insert(value)).isOne();
        return value;
    }

    private Shop insertShop() {
        Shop value = new Shop();
        value.setShopNo("INV-SHOP-" + suffix());
        value.setShopName("库存调整测试店铺");
        value.setStatus(ShopStatus.ACTIVE);
        assertThat(shopMapper.insert(value)).isOne();
        return value;
    }

    private ProductCategory insertCategory() {
        ProductCategory value = new ProductCategory();
        value.setCategoryName("库存测试分类");
        value.setCategoryCode("INV_CAT_" + suffix().toUpperCase());
        value.setSortOrder(0);
        value.setStatus(EnabledStatus.ENABLED);
        assertThat(categoryMapper.insert(value)).isOne();
        return value;
    }

    private ProductSpu insertSpu(ProductCategory category) {
        ProductSpu value = new ProductSpu();
        value.setShopId(shop.getId());
        value.setCategoryId(category.getId());
        value.setSpuNo("INV-SPU-" + suffix());
        value.setProductName("库存调整测试商品");
        value.setStatus(ProductStatus.ON_SHELF);
        value.setContentVersion(1);
        value.setCreatedBy(operator.getId());
        value.setUpdatedBy(operator.getId());
        assertThat(spuMapper.insert(value)).isOne();
        return value;
    }

    private ProductSku insertSku(ProductSpu spu) {
        ProductSku value = new ProductSku();
        value.setSpuId(spu.getId());
        value.setShopId(shop.getId());
        value.setSkuNo("INV-SKU-" + suffix());
        value.setSkuName("黑色测试规格");
        value.setSpecJson(Map.of("颜色", "黑色"));
        value.setSpecKey("a".repeat(64));
        value.setSalePrice(new BigDecimal("99.00"));
        value.setStatus(EnabledStatus.ENABLED);
        value.setVersion(0);
        assertThat(skuMapper.insert(value)).isOne();
        return value;
    }

    private InventoryStock insertStock(ProductSku sku, int available, int locked) {
        InventoryStock value = new InventoryStock();
        value.setSkuId(sku.getId());
        value.setAvailableQuantity(available);
        value.setLockedQuantity(locked);
        value.setVersion(0);
        assertThat(stockMapper.insert(value)).isOne();
        return value;
    }

    private InventoryTransaction insertTransaction(InventoryTransactionType type,
                                                   int availableChange, int lockedChange,
                                                   int availableAfter, int lockedAfter,
                                                   String businessType, String businessNo,
                                                   String remark) {
        InventoryTransaction value = new InventoryTransaction();
        value.setTransactionNo("IT" + suffix());
        value.setSkuId(sku.getId());
        value.setTransactionType(type);
        value.setAvailableChange(availableChange);
        value.setLockedChange(lockedChange);
        value.setAvailableAfter(availableAfter);
        value.setLockedAfter(lockedAfter);
        value.setBusinessType(businessType);
        value.setBusinessNo(businessNo);
        value.setOperatorId(operator.getId());
        value.setRemark(remark);
        assertThat(transactionMapper.insert(value)).isOne();
        return value;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
