package org.dhu.shiguang_market.phasesix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.integration.identity.CurrentUserPort.CurrentUser;
import org.dhu.shiguang_market.integration.identity.CurrentUserPortFake;
import org.dhu.shiguang_market.integration.identity.IdentitySummaryPort.IdentitySummary;
import org.dhu.shiguang_market.integration.identity.IdentitySummaryPortFake;
import org.dhu.shiguang_market.integration.inventory.InventoryReservationPortFake;
import org.dhu.shiguang_market.integration.inventory.InventoryTracePort.InventoryTrace;
import org.dhu.shiguang_market.integration.inventory.InventoryTracePortFake;
import org.dhu.shiguang_market.integration.product.ProductAvailabilityPortFake;
import org.dhu.shiguang_market.integration.product.ProductSnapshotPort.ProductSnapshot;
import org.dhu.shiguang_market.integration.product.ProductSnapshotPortFake;
import org.dhu.shiguang_market.integration.shop.ShopAccessPortFake;
import org.dhu.shiguang_market.integration.shop.ShopSummaryPort.ShopSummary;
import org.dhu.shiguang_market.integration.shop.ShopSummaryPortFake;
import org.junit.jupiter.api.Test;

/** 阶段六：B 线消费 A 线能力时使用的八个内存 Fake 测试。 */
class PhaseSixConsumerFakeTests {

    /** 用户、身份摘要、店铺权限和店铺摘要 Fake 均支持测试场景预设。 */
    @Test
    void identityAndShopFakesReturnConfiguredValues() {
        CurrentUserPortFake currentUser = new CurrentUserPortFake();
        currentUser.setCurrent(new CurrentUser(7L, "buyer", "买家", UserStatus.ACTIVE));
        assertEquals(7L, currentUser.current().userId());

        IdentitySummaryPortFake identities = new IdentitySummaryPortFake();
        identities.put(new IdentitySummary(7L, "buyer", "买家", null, UserStatus.ACTIVE));
        assertEquals("买家", identities.findByIds(List.of(7L)).getFirst().nickname());

        ShopAccessPortFake access = new ShopAccessPortFake();
        access.allow(7L, 3L, "shop:order:manage");
        access.require(7L, 3L, "shop:order:manage");
        assertThrows(BusinessException.class,
                () -> access.require(7L, 3L, "shop:after-sale:manage"));

        ShopSummaryPortFake shops = new ShopSummaryPortFake();
        shops.put(new ShopSummary(3L, "S003", "测试店铺", null, ShopStatus.ACTIVE));
        assertEquals("测试店铺", shops.findByIds(List.of(3L)).getFirst().shopName());
    }

    /** 商品快照按输入 SKU 顺序返回，可购买性能够模拟下架或停用。 */
    @Test
    void productFakesProvideStableSnapshotAndAvailability() {
        ProductSnapshotPortFake snapshots = new ProductSnapshotPortFake();
        snapshots.put(snapshot(20L, "SKU20"));
        snapshots.put(snapshot(10L, "SKU10"));

        List<ProductSnapshot> result = snapshots.findBySkuIds(List.of(10L, 20L));
        assertEquals(List.of(10L, 20L), result.stream().map(ProductSnapshot::skuId).toList());

        ProductAvailabilityPortFake availability = new ProductAvailabilityPortFake();
        availability.setUnavailable(20L, "SKU 已停用");
        assertTrue(availability.check(List.of(10L)).getFirst().purchasable());
        assertFalse(availability.check(List.of(20L)).getFirst().purchasable());
        assertEquals("SKU 已停用", availability.check(List.of(20L)).getFirst().reason());
    }

    /** 库存 Fake 完整模拟 LOCK、RELEASE、DEDUCT、RETURN，并保证相同业务键幂等。 */
    @Test
    void inventoryReservationFakeSupportsLifecycleAndIdempotency() {
        InventoryReservationPortFake inventory = new InventoryReservationPortFake();
        inventory.seed(10L, 10, 0);

        var locked = inventory.lock("trade-1", 10L, 4);
        var repeated = inventory.lock("trade-1", 10L, 4);
        assertEquals(6, locked.availableQuantity());
        assertEquals(4, locked.lockedQuantity());
        assertEquals(locked, repeated);

        var released = inventory.release("cancel-1", 10L, 2);
        var deducted = inventory.deduct("ship-1", 10L, 2);
        var returned = inventory.returnStock("return-1", 10L, 3);
        assertEquals(8, released.availableQuantity());
        assertEquals(0, deducted.lockedQuantity());
        assertEquals(11, returned.availableQuantity());

        BusinessException insufficient = assertThrows(BusinessException.class,
                () -> inventory.lock("trade-2", 10L, 12));
        assertEquals("INVENTORY_NOT_ENOUGH", insufficient.getCode());
    }

    /** 库存追踪 Fake 仅返回业务类型和业务号同时匹配的流水摘要。 */
    @Test
    void inventoryTraceFakeFiltersByBusinessKey() {
        InventoryTracePortFake traces = new InventoryTracePortFake();
        traces.add(new InventoryTrace(1L, "IT001", 10L, InventoryTransactionType.LOCK,
                -2, 2, "TRADE", "T001", OffsetDateTime.now()));
        traces.add(new InventoryTrace(2L, "IT002", 10L, InventoryTransactionType.RELEASE,
                2, -2, "TRADE", "T002", OffsetDateTime.now()));

        List<InventoryTrace> result = traces.findByBusiness("TRADE", "T001");

        assertEquals(1, result.size());
        assertEquals("IT001", result.getFirst().transactionNo());
    }

    private ProductSnapshot snapshot(long skuId, String skuNo) {
        return new ProductSnapshot(skuId, 1L, 3L, "SPU001", skuNo,
                "测试商品", "默认规格", Map.of("颜色", "黑色"), null,
                new BigDecimal("19.90"), ProductStatus.ON_SHELF, EnabledStatus.ENABLED);
    }
}
