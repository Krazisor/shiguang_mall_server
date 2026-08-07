package org.dhu.shiguang_market.integration.order;

/** A 线关闭店铺前用于判断是否存在未完结订单或售后的只读端口。 */
public interface ActiveShopBusinessPort {
    boolean hasActiveBusiness(long shopId);
}
