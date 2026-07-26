package org.dhu.shiguang_market.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;

@Data
@TableName("order_info")
public class OrderInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long tradeId;
    private Long userId;
    private Long shopId;
    private String shopName;
    private OrderStatus orderStatus;
    private OrderPaymentStatus paymentStatus;
    private BigDecimal itemAmount;
    private BigDecimal freightAmount;
    private BigDecimal payableAmount;
    private BigDecimal refundAmount;
    private String buyerRemark;
    private String cancelReason;
    private String carrierCode;
    private String carrierName;
    private String trackingNo;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
