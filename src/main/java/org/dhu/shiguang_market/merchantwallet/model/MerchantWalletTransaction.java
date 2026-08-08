package org.dhu.shiguang_market.merchantwallet.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantTransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletBucket;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletTransactionType;

@Data
@TableName("merchant_wallet_transaction")
public class MerchantWalletTransaction {
    @TableId(type = IdType.AUTO) private Long id;
    private String transactionNo;
    private Long walletId;
    private Long shopId;
    private MerchantWalletTransactionType transactionType;
    private MerchantTransactionDirection direction;
    private MerchantWalletBucket sourceBucket;
    private MerchantWalletBucket targetBucket;
    private BigDecimal amount;
    private BigDecimal pendingBefore;
    private BigDecimal pendingAfter;
    private BigDecimal availableBefore;
    private BigDecimal availableAfter;
    private BigDecimal frozenBefore;
    private BigDecimal frozenAfter;
    private String businessType;
    private String businessNo;
    private Long settlementId;
    private Long orderId;
    private Long withdrawalId;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;
}
