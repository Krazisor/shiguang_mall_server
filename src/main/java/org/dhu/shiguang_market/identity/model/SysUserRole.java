package org.dhu.shiguang_market.identity.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;

/** Composite key: use mapper methods constrained by both userId and roleId. */
@Data
@TableName("sys_user_role")
public class SysUserRole {
    private Long userId;
    private Long roleId;
    private ScopeType roleScope;
    private LocalDateTime createdAt;
}
