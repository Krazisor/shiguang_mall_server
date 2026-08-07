package org.dhu.shiguang_market.integration.shop;

/** B 线校验用户店铺成员身份与店铺权限的跨线端口。 */
public interface ShopAccessPort {
    void require(long userId, long shopId, String permission);
}
