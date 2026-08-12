package org.dhu.shiguang_market.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;

@Data
@TableName("trade_order")
public class TradeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tradeNo;
    private Long userId;
    private TradeStatus tradeStatus;
    private BigDecimal grossAmount;
    private BigDecimal couponDiscountAmount;
    private Boolean usesFirstOrderCoupon;
    private BigDecimal payableAmount;
    private String recipientName;
    private String recipientPhone;
    private String provinceName;
    private String cityName;
    private String districtName;
    private String detailAddress;
    private LocalDateTime payExpireAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
