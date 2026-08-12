package org.dhu.shiguang_market.merchantwallet.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.SettlementStatus;

@Data
@TableName("shop_settlement")
public class ShopSettlement {
    @TableId(type = IdType.AUTO) private Long id;
    private String settlementNo;
    private Long shopId;
    private Long walletId;
    private Long tradeId;
    private Long orderId;
    private SettlementStatus status;
    private BigDecimal grossAmount;
    private BigDecimal buyerPaidAmount;
    private BigDecimal platformCouponSubsidyAmount;
    private BigDecimal shopCouponDiscountAmount;
    private BigDecimal commissionRate;
    private Boolean commissionRefundable;
    private BigDecimal commissionAmount;
    private BigDecimal buyerRefundAmount;
    private BigDecimal platformSubsidyRefundAmount;
    private BigDecimal commissionRefundAmount;
    private BigDecimal merchantRefundAmount;
    private BigDecimal netAmount;
    private BigDecimal pendingAmount;
    private BigDecimal releasedAmount;
    private LocalDateTime availableAt;
    private LocalDateTime settledAt;
    @Version private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
