package org.dhu.shiguang_market.integration.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus;
import org.dhu.shiguang_market.integration.payment.WalletProvisionPort.WalletProvisionResult;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.model.WalletAccount;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 使用 B 线钱包表实现注册钱包创建能力。 */
@Component
public class WalletProvisionAdapter implements WalletProvisionPort {
    private final WalletAccountMapper walletMapper;

    public WalletProvisionAdapter(WalletAccountMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    /**
     * 为用户创建零余额钱包。若钱包已经存在则直接返回，保证顺序重复调用幂等。
     * 默认事务传播级别会加入注册事务，使用户、角色和钱包共同提交或回滚。
     */
    @Override
    @Transactional
    public WalletProvisionResult provision(long userId) {
        WalletAccount existing = walletMapper.selectOne(new LambdaQueryWrapper<WalletAccount>()
                .eq(WalletAccount::getUserId, userId));
        if (existing != null) return view(existing);

        WalletAccount wallet = new WalletAccount();
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("0.00"));
        wallet.setStatus(WalletStatus.ACTIVE);
        wallet.setVersion(0);
        walletMapper.insert(wallet);
        return view(wallet);
    }

    private WalletProvisionResult view(WalletAccount wallet) {
        return new WalletProvisionResult(wallet.getId(), wallet.getUserId(),
                wallet.getBalance(), wallet.getStatus());
    }
}
