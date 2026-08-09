package org.dhu.shiguang_market.integration.merchantwallet;

import org.dhu.shiguang_market.merchantwallet.mapper.MerchantWalletAccountMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotently creates the zero-balance wallet for a shop. */
@Component
public class MerchantWalletProvisionAdapter implements MerchantWalletProvisionPort {
    private final MerchantWalletAccountMapper walletMapper;

    public MerchantWalletProvisionAdapter(MerchantWalletAccountMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    @Override
    @Transactional
    public void provision(long shopId) {
        walletMapper.ensureByShopId(shopId);
    }
}
