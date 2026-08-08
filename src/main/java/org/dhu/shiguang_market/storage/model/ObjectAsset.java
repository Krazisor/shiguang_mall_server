package org.dhu.shiguang_market.storage.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.AssetPurpose;
import org.dhu.shiguang_market.common.model.MarketEnums.AssetStatus;

@Data
@TableName("object_asset")
public class ObjectAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String assetNo;
    private String bucket;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private AssetPurpose purpose;
    private Long ownerUserId;
    private Long shopId;
    private String publicUrl;
    private AssetStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
