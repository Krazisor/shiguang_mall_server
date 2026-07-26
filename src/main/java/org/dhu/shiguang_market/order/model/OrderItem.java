package org.dhu.shiguang_market.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;

@Data
@TableName(value = "order_item", autoResultMap = true)
public class OrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long shopId;
    private Long spuId;
    private Long skuId;
    private String spuNo;
    private String skuNo;
    private String productName;
    private String skuName;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, String> specJson;
    private String imageUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal originalAmount;
    private BigDecimal freightAmount;
    private BigDecimal payableAmount;
    private Integer refundedQuantity;
    private BigDecimal refundedAmount;
    private ReservationStatus reservationStatus;
    private LocalDateTime createdAt;
}
