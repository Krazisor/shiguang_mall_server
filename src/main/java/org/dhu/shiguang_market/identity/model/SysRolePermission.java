package org.dhu.shiguang_market.identity.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;

/** Composite key: use mapper methods constrained by both roleId and permissionId. */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {
    private Long roleId;
    private Long permissionId;
    private ScopeType scopeType;
    private LocalDateTime createdAt;
}
