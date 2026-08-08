package org.dhu.shiguang_market.aftersale.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantNotificationType;

@Data
@TableName("merchant_notification")
public class MerchantNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long recipientUserId;
    private Long appealId;
    private Long afterSaleId;
    private MerchantNotificationType notificationType;
    private String title;
    private String content;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
