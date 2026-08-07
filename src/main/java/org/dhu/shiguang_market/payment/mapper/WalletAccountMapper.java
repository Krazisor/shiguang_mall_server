package org.dhu.shiguang_market.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dhu.shiguang_market.payment.model.WalletAccount;

public interface WalletAccountMapper extends BaseMapper<WalletAccount> {
    /** 对比钱包余额与不可变流水的净额，只读统计差异，不自动修正。 */
    @Select("""
            SELECT COUNT(*) FROM wallet_account wallet
            WHERE wallet.balance <> COALESCE((
                SELECT SUM(CASE WHEN tx.direction = 'CREDIT' THEN tx.amount ELSE -tx.amount END)
                FROM wallet_transaction tx WHERE tx.wallet_id = wallet.id
            ), 0)
            """)
    int countReconciliationMismatches();

    @Update("""
            UPDATE wallet_account SET balance = balance - #{amount}, version = version + 1
            WHERE user_id = #{userId} AND status = 'ACTIVE' AND balance >= #{amount}
            """)
    int debit(@Param("userId") long userId, @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE wallet_account SET balance = balance + #{amount}, version = version + 1
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            """)
    int credit(@Param("userId") long userId, @Param("amount") BigDecimal amount);
}
