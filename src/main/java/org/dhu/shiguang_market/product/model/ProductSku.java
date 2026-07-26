package org.dhu.shiguang_market.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;

@Data
@TableName(value = "product_sku", autoResultMap = true)
public class ProductSku {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spuId;
    private Long shopId;
    private String skuNo;
    private String skuName;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, String> specJson;
    private String specKey;
    private BigDecimal salePrice;
    private BigDecimal marketPrice;
    private String barcode;
    private String imageUrl;
    private EnabledStatus status;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
