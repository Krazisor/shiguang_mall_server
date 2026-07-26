package org.dhu.shiguang_market.shop.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;

/** Composite key: use mapper methods constrained by both shopId and userId. */
@Data
@TableName("shop_user")
public class ShopUser {
    private Long shopId;
    private Long userId;
    private Long roleId;
    private ScopeType roleScope;
    private ActiveStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
