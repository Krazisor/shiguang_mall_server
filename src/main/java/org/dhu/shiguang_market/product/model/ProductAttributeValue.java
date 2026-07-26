package org.dhu.shiguang_market.product.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Composite key: use mapper methods constrained by both spuId and attributeId. */
@Data
@TableName("product_attribute_value")
public class ProductAttributeValue {
    private Long spuId;
    private Long categoryId;
    private Long attributeId;
    private String attributeValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
