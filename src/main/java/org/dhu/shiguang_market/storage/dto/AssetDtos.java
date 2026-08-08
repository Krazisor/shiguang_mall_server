package org.dhu.shiguang_market.storage.dto;

import java.time.OffsetDateTime;
import org.dhu.shiguang_market.common.model.MarketEnums.AssetPurpose;

public final class AssetDtos {
    private AssetDtos() {
    }

    public record AssetUploadView(
            String id,
            String assetNo,
            AssetPurpose purpose,
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String sha256,
            String url,
            OffsetDateTime createdAt) {
    }
}
