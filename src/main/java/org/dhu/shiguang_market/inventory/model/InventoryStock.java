package org.dhu.shiguang_market.inventory.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("inventory_stock")
public class InventoryStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private Integer availableQuantity;
    private Integer lockedQuantity;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
