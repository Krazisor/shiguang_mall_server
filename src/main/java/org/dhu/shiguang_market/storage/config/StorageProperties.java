package org.dhu.shiguang_market.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market.storage")
public record StorageProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String publicBaseUrl,
        String objectPrefix,
        boolean autoCreateBucket,
        boolean publicRead,
        long maxFileSizeBytes) {
}
