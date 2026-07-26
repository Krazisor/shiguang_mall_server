package org.dhu.shiguang_market.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;

@Data
@TableName(value = "product_spu", autoResultMap = true)
public class ProductSpu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long categoryId;
    private Long brandId;
    private String spuNo;
    private String productName;
    private String subtitle;
    private String coverUrl;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> galleryJson;
    private String detailHtml;
    private String packingList;
    private String serviceNote;
    private ProductStatus status;
    private Integer contentVersion;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
