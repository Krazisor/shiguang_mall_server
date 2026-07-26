package org.dhu.shiguang_market.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;

@Data
@TableName("product_status_history")
public class ProductStatusHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spuId;
    private ProductStatus fromStatus;
    private ProductStatus toStatus;
    private ProductOperationType operationType;
    private Integer contentVersion;
    private OperatorType operatorType;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;
}
