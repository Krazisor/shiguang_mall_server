package org.dhu.shiguang_market.inventory.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;

@Data
@TableName("inventory_transaction")
public class InventoryTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transactionNo;
    private Long skuId;
    private InventoryTransactionType transactionType;
    private Integer availableChange;
    private Integer lockedChange;
    private Integer availableAfter;
    private Integer lockedAfter;
    private String businessType;
    private String businessNo;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;
}
