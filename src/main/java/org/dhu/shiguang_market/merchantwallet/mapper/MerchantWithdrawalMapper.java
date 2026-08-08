package org.dhu.shiguang_market.merchantwallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.merchantwallet.model.MerchantWithdrawal;

public interface MerchantWithdrawalMapper extends BaseMapper<MerchantWithdrawal> {
    @Select("SELECT * FROM merchant_withdrawal WHERE id = #{id} AND shop_id = #{shopId} FOR UPDATE")
    MerchantWithdrawal selectScopedForUpdate(long id, long shopId);
}
