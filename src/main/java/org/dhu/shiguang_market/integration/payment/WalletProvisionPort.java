package org.dhu.shiguang_market.integration.payment;

import java.math.BigDecimal;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus;

/** A 线注册用户时调用的钱包创建端口，不向调用方暴露钱包数据库实体。 */
public interface WalletProvisionPort {
    WalletProvisionResult provision(long userId);

    record WalletProvisionResult(
            long walletId, long userId, BigDecimal balance, WalletStatus status) {
    }
}
