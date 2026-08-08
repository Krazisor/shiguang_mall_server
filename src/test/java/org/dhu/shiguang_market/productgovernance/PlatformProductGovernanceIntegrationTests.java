package org.dhu.shiguang_market.productgovernance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.product.controller.ProductReviewController;
import org.dhu.shiguang_market.product.controller.PlatformProductGovernanceController;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductGovernanceRequest;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.mapper.ProductStatusHistoryMapper;
import org.dhu.shiguang_market.product.model.ProductCategory;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.product.model.ProductStatusHistory;
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
 * 平台商品治理集成测试。
 *
 * <p>测试连接真实 Controller、Service、Mapper 和 MySQL，事务结束后自动回滚，
 * 可以直接运行并查看接口到数据库的完整调用结果。</p>
 */
@SpringBootTest
@Transactional
class PlatformProductGovernanceIntegrationTests {
    @Autowired private ProductReviewController reviewController;
    @Autowired private PlatformProductGovernanceController governanceController;
    @Autowired private ProductSpuMapper spuMapper;
    @Autowired private ProductStatusHistoryMapper historyMapper;
    @Autowired private ProductCategoryMapper categoryMapper;
    @Autowired private ShopMapper shopMapper;
    @Autowired private SysUserMapper userMapper;

    @MockitoBean private CurrentUserService currentUser;
    /** 防止测试启动项目定时任务，避免访问与商品治理无关的数据。 */
    @MockitoBean private TaskExecutionService taskExecutionService;

    private SysUser operator;
    private Shop shop;
    private ProductCategory category;

    @BeforeEach
    void setUp() {
        operator = insertUser();
        shop = insertShop();
        category = insertCategory();
        when(currentUser.id()).thenReturn(operator.getId());
    }

    /** 治理历史接口应从真实 Mapper 分页读取状态变更记录，并返回操作人信息。 */
    @Test
    void historyConnectsControllerServiceAndMapper() {
        ProductSpu spu = insertSpu(ProductStatus.BANNED);
        insertHistory(spu, ProductStatus.ON_SHELF, ProductStatus.BANNED,
                ProductOperationType.BAN, "平台禁售测试");

        var page = reviewController.history(spu.getId(), 1, 20).data();

        assertThat(page.total()).isOne();
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().operationType()).isEqualTo(ProductOperationType.BAN);
        assertThat(page.items().getFirst().operator().username()).isEqualTo(operator.getUsername());
    }

    /** 上架商品禁售后应变为 BANNED，解禁后统一回到 OFF_SHELF，并写入两条历史。 */
    @Test
    void banAndRevokeFlowIsConnected() {
        ProductSpu spu = insertSpu(ProductStatus.ON_SHELF);

        var banned = governanceController.ban(spu.getId(),
                new ProductGovernanceRequest(3, "存在平台违规内容")).data();
        var revoked = governanceController.revoke(spu.getId(),
                new ProductGovernanceRequest(3, "复核通过，解除禁售")).data();

        assertThat(banned.status()).isEqualTo(ProductStatus.BANNED);
        assertThat(revoked.status()).isEqualTo(ProductStatus.OFF_SHELF);
        assertThat(spuMapper.selectById(spu.getId()).getStatus()).isEqualTo(ProductStatus.OFF_SHELF);

        var history = historyMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductStatusHistory>()
                .eq(ProductStatusHistory::getSpuId, spu.getId())
                .orderByAsc(ProductStatusHistory::getId));
        assertThat(history).extracting(ProductStatusHistory::getOperationType)
                .containsExactly(ProductOperationType.BAN, ProductOperationType.UNBAN);
    }

    /** 平台只能强制下架正在销售的商品，重复下架应返回明确的业务错误。 */
    @Test
    void takeOffShelfFlowIsConnectedAndChecksStatus() {
        ProductSpu spu = insertSpu(ProductStatus.ON_SHELF);
        ProductGovernanceRequest request = new ProductGovernanceRequest(3, "平台巡检要求下架整改");

        var result = governanceController.takeOffShelf(spu.getId(), request).data();

        assertThat(result.status()).isEqualTo(ProductStatus.OFF_SHELF);
        assertThat(spuMapper.selectById(spu.getId()).getStatus()).isEqualTo(ProductStatus.OFF_SHELF);
        assertThat(result.history()).extracting(item -> item.operationType())
                .containsExactly(ProductOperationType.TAKE_OFF_SHELF);

        assertThatThrownBy(() -> governanceController.takeOffShelf(spu.getId(), request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PRODUCT_NOT_ON_SHELF"));
    }

    private SysUser insertUser() {
        SysUser value = new SysUser();
        value.setUsername("governance_" + suffix());
        value.setPasswordHash("integration-test-only");
        value.setNickname("平台治理测试员");
        value.setStatus(UserStatus.ACTIVE);
        assertThat(userMapper.insert(value)).isOne();
        return value;
    }

    private Shop insertShop() {
        Shop value = new Shop();
        value.setShopNo("GOV-SHOP-" + suffix());
        value.setShopName("商品治理测试店铺");
        value.setStatus(ShopStatus.ACTIVE);
        assertThat(shopMapper.insert(value)).isOne();
        return value;
    }

    private ProductCategory insertCategory() {
        ProductCategory value = new ProductCategory();
        value.setCategoryName("商品治理测试分类");
        value.setCategoryCode("GOV-CAT-" + suffix());
        value.setSortOrder(0);
        value.setStatus(EnabledStatus.ENABLED);
        assertThat(categoryMapper.insert(value)).isOne();
        return value;
    }

    private ProductSpu insertSpu(ProductStatus status) {
        ProductSpu value = new ProductSpu();
        value.setShopId(shop.getId());
        value.setCategoryId(category.getId());
        value.setSpuNo("GOV-SPU-" + suffix());
        value.setProductName("平台治理测试商品");
        value.setStatus(status);
        value.setContentVersion(3);
        value.setCreatedBy(operator.getId());
        value.setUpdatedBy(operator.getId());
        assertThat(spuMapper.insert(value)).isOne();
        return value;
    }

    private ProductStatusHistory insertHistory(ProductSpu spu, ProductStatus fromStatus,
                                               ProductStatus toStatus, ProductOperationType operation,
                                               String reason) {
        ProductStatusHistory value = new ProductStatusHistory();
        value.setSpuId(spu.getId());
        value.setFromStatus(fromStatus);
        value.setToStatus(toStatus);
        value.setOperationType(operation);
        value.setContentVersion(spu.getContentVersion());
        value.setOperatorType(OperatorType.PLATFORM);
        value.setOperatorId(operator.getId());
        value.setReason(reason);
        assertThat(historyMapper.insert(value)).isOne();
        return value;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
