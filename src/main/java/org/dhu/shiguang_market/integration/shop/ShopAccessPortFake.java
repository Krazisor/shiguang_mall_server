package org.dhu.shiguang_market.integration.shop;

import java.util.HashSet;
import java.util.Set;
import org.dhu.shiguang_market.common.exception.BusinessException;

/** 使用内存权限集合模拟店铺访问校验。 */
public class ShopAccessPortFake implements ShopAccessPort {
    private final Set<PermissionKey> allowed = new HashSet<>();

    public void allow(long userId, long shopId, String permission) {
        allowed.add(new PermissionKey(userId, shopId, permission));
    }

    public void revoke(long userId, long shopId, String permission) {
        allowed.remove(new PermissionKey(userId, shopId, permission));
    }

    @Override
    public void require(long userId, long shopId, String permission) {
        if (!allowed.contains(new PermissionKey(userId, shopId, permission))) {
            throw BusinessException.forbidden("SHOP_ACCESS_DENIED", "无权访问该店铺资源");
        }
    }

    private record PermissionKey(long userId, long shopId, String permission) {
    }
}
