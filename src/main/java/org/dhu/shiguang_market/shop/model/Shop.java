package org.dhu.shiguang_market.shop.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;

@Data
@TableName("shop")
public class Shop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shopNo;
    private String shopName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String logoUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactPhone;
    private ShopStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
