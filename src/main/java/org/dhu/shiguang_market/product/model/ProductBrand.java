package org.dhu.shiguang_market.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;

@Data
@TableName("product_brand")
public class ProductBrand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String brandName;
    private String brandCode;
    private String logoUrl;
    private EnabledStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
