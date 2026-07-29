package org.dhu.shiguang_market.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.AttributeValueType;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;

@Data
@TableName(value = "product_category_attribute", autoResultMap = true)
public class ProductCategoryAttribute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String attributeName;
    private AttributeValueType valueType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unit;
    private Boolean isRequired;
    private Boolean isFilterable;
    @TableField(typeHandler = Jackson3TypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private List<String> optionsJson;
    private Integer sortOrder;
    private EnabledStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
