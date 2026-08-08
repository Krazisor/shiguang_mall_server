package org.dhu.shiguang_market.merchantwallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.merchantwallet.model.MerchantWalletAccount;

public interface MerchantWalletAccountMapper extends BaseMapper<MerchantWalletAccount> {
    @Insert("""
            INSERT INTO merchant_wallet_account
                (shop_id, currency, pending_balance, available_balance, frozen_balance,
                 lifetime_gross_income, lifetime_commission, lifetime_refund, status, version)
            VALUES (#{shopId}, 'CNY', 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 'ACTIVE', 0)
            ON DUPLICATE KEY UPDATE shop_id = shop_id
            """)
    int ensureByShopId(@Param("shopId") long shopId);

    @Select("SELECT * FROM merchant_wallet_account WHERE shop_id = #{shopId} FOR UPDATE")
    MerchantWalletAccount selectByShopIdForUpdate(long shopId);
}
