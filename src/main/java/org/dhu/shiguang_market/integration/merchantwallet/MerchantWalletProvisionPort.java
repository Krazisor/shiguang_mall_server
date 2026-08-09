package org.dhu.shiguang_market.integration.merchantwallet;

/** Provides the shop-owned merchant wallet without exposing wallet persistence to the shop module. */
public interface MerchantWalletProvisionPort {
    void provision(long shopId);
}
