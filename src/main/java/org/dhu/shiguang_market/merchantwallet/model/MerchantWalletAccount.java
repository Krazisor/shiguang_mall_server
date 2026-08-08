package org.dhu.shiguang_market.merchantwallet.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletStatus;

@Data
@TableName("merchant_wallet_account")
public class MerchantWalletAccount {
    @TableId(type = IdType.AUTO) private Long id;
    private Long shopId;
    private String currency;
    private BigDecimal pendingBalance;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private BigDecimal lifetimeGrossIncome;
    private BigDecimal lifetimeCommission;
    private BigDecimal lifetimeRefund;
    private MerchantWalletStatus status;
    @Version private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
