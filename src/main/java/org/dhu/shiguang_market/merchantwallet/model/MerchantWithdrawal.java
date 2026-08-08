package org.dhu.shiguang_market.merchantwallet.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWithdrawalStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WithdrawalDestinationType;

@Data
@TableName("merchant_withdrawal")
public class MerchantWithdrawal {
    @TableId(type = IdType.AUTO) private Long id;
    private String withdrawalNo;
    private Long walletId;
    private Long shopId;
    private MerchantWithdrawalStatus status;
    private BigDecimal amount;
    private BigDecimal feeAmount;
    private BigDecimal netAmount;
    private WithdrawalDestinationType destinationType;
    private String destinationAccount;
    private String remark;
    private String failureReason;
    private Long requestedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    @Version private Integer version;
    private String businessNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
