package org.dhu.shiguang_market.identity.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
