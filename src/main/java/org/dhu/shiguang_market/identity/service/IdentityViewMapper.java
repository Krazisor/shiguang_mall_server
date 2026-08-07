package org.dhu.shiguang_market.identity.service;

import org.dhu.shiguang_market.common.api.CommonViews.AddressView;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.address.model.UserAddress;
import org.dhu.shiguang_market.shop.model.Shop;

public final class IdentityViewMapper {
    private IdentityViewMapper() {
    }

    public static UserSummary user(SysUser user) {
        return new UserSummary(Formatters.id(user.getId()), user.getUsername(), user.getNickname(),
                user.getAvatarUrl(), user.getStatus());
    }

    public static ShopSummary shop(Shop shop) {
        return new ShopSummary(Formatters.id(shop.getId()), shop.getShopNo(), shop.getShopName(),
                shop.getLogoUrl(), shop.getStatus());
    }

    public static AddressView address(UserAddress address) {
        return new AddressView(Formatters.id(address.getId()), address.getRecipientName(),
                address.getRecipientPhone(), address.getProvinceName(), address.getCityName(),
                address.getDistrictName(), address.getDetailAddress(), Boolean.TRUE.equals(address.getIsDefault()),
                Formatters.time(address.getCreatedAt()), Formatters.time(address.getUpdatedAt()));
    }
}
