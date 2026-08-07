package org.dhu.shiguang_market.aftersale.model;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;

@Data
@TableName(value = "after_sale_request", autoResultMap = true)
public class AfterSaleRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String afterSaleNo;
    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private AfterSaleType requestType;
    private Integer quantity;
    private String reasonCode;
    private String reasonDescription;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> evidenceJson;
    private BigDecimal requestedAmount;
    private Integer approvedQuantity;
    private BigDecimal approvedAmount;
    private AfterSaleStatus status;
    private Long reviewerId;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private String returnCarrierCode;
    private String returnCarrierName;
    private String returnTrackingNo;
    private LocalDateTime returnedAt;
    private LocalDateTime returnReceivedAt;
    private String refundNo;
    private RefundStatus refundStatus;
    // FAILED -> PROCESSING/SUCCESS 时必须把失败原因更新为 NULL，以满足数据库状态约束。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String refundFailureReason;
    private LocalDateTime refundedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
