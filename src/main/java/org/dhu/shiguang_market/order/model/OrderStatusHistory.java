package org.dhu.shiguang_market.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;

@Data
@TableName("order_status_history")
public class OrderStatusHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private OrderOperationType operationType;
    private OperatorType operatorType;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;
}
