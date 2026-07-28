package org.dhu.shiguang_market.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.mapper.ShopUserMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.dhu.shiguang_market.shop.model.ShopUser;
import org.springframework.stereotype.Service;

@Service
public class ShopAccessService {
    private final CurrentUserService currentUser;
    private final ShopMapper shopMapper;
    private final ShopUserMapper shopUserMapper;

    public ShopAccessService(CurrentUserService currentUser, ShopMapper shopMapper,
                             ShopUserMapper shopUserMapper) {
        this.currentUser = currentUser;
        this.shopMapper = shopMapper;
        this.shopUserMapper = shopUserMapper;
    }

    public Shop require(long shopId, String permission) {
        long userId = currentUser.id();
        Shop shop = shopMapper.selectById(shopId);
        ShopUser member = shopUserMapper.selectOne(new LambdaQueryWrapper<ShopUser>()
                .eq(ShopUser::getShopId, shopId)
                .eq(ShopUser::getUserId, userId)
                .eq(ShopUser::getStatus, ActiveStatus.ACTIVE));
        List<String> permissions = shopUserMapper.selectPermissions(shopId, userId);
        if (shop == null || member == null || !permissions.contains(permission)) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "资源不存在");
        }
        return shop;
    }
}
