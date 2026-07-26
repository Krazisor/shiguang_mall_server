package org.dhu.shiguang_market.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import java.lang.reflect.Field;
import java.util.List;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.model.UserAddress;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.payment.model.PaymentOrder;
import org.dhu.shiguang_market.payment.model.WalletAccount;
import org.dhu.shiguang_market.product.model.ProductCategoryAttribute;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.junit.jupiter.api.Test;

class EntityMappingTests {

    @Test
    void marksAllTemporalSoftDeleteFieldsAsLogicDelete() throws Exception {
        for (Class<?> entity : List.of(SysUser.class, UserAddress.class, ProductSpu.class, ProductSku.class)) {
            assertThat(entity.getDeclaredField("deletedAt").isAnnotationPresent(TableLogic.class)).isTrue();
        }
    }

    @Test
    void marksAllVersionColumnsForOptimisticLocking() throws Exception {
        for (Class<?> entity : List.of(
                ProductSku.class, InventoryStock.class, TradeOrder.class, OrderInfo.class,
                WalletAccount.class, AfterSaleRequest.class)) {
            Field version = entity.getDeclaredField("version");
            assertThat(version.isAnnotationPresent(Version.class)).isTrue();
        }
    }

    @Test
    void exposesGeneratedColumnsAsReadOnlyFields() throws Exception {
        assertThat(UserAddress.class.getDeclaredField("defaultUserId")).isNotNull();
        assertThat(PaymentOrder.class.getDeclaredField("successGuard")).isNotNull();
    }

    @Test
    void usesBoot4Jackson3HandlerForJsonColumns() throws Exception {
        assertThat(ProductCategoryAttribute.class.getDeclaredField("optionsJson")
                .getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class).typeHandler())
                .isEqualTo(Jackson3TypeHandler.class);
    }
}
