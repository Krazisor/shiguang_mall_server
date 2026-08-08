package org.dhu.shiguang_market.aftersale.model;

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
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealDecision;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealTriggerType;

@Data
@TableName(value = "after_sale_appeal", autoResultMap = true)
public class AfterSaleAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appealNo;
    private Long afterSaleId;
    private Long shopId;
    private Long appellantUserId;
    private AfterSaleAppealTriggerType triggerType;
    private AfterSaleAppealStatus status;
    private String reasonCode;
    private String reasonDescription;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> evidenceJson;
    private Long merchantReviewerId;
    private String merchantReviewComment;
    private LocalDateTime merchantReviewedAt;
    private AfterSaleAppealDecision decision;
    private Integer approvedQuantity;
    private BigDecimal approvedAmount;
    private Long decidedBy;
    private String decisionComment;
    private LocalDateTime decidedAt;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
