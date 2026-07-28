package org.dhu.shiguang_market.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dhu.shiguang_market.payment.model.WalletAccount;

public interface WalletAccountMapper extends BaseMapper<WalletAccount> {
    @Update("""
            UPDATE wallet_account SET balance = balance - #{amount}, version = version + 1
            WHERE user_id = #{userId} AND status = 'ACTIVE' AND balance >= #{amount}
            """)
    int debit(@Param("userId") long userId, @Param("amount") java.math.BigDecimal amount);
}
