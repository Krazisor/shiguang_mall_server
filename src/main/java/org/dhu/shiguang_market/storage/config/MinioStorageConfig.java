package org.dhu.shiguang_market.storage.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class MinioStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "market.storage", name = "enabled", havingValue = "true")
    public MinioClient minioClient(StorageProperties properties) {
        if (!StringUtils.hasText(properties.endpoint())
                || !StringUtils.hasText(properties.accessKey())
                || !StringUtils.hasText(properties.secretKey())
                || !StringUtils.hasText(properties.bucket())) {
            throw new IllegalStateException("MinIO enabled but endpoint, credentials or bucket is missing");
        }
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "market.storage", name = "enabled", havingValue = "true")
    public MinioBucketInitializer minioBucketInitializer(MinioClient client, StorageProperties properties) {
        return new MinioBucketInitializer(client, properties);
    }

    public static final class MinioBucketInitializer {
        private final MinioClient client;
        private final StorageProperties properties;

        public MinioBucketInitializer(MinioClient client, StorageProperties properties) {
            this.client = Objects.requireNonNull(client);
            this.properties = Objects.requireNonNull(properties);
            initialize();
        }

        private void initialize() {
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder()
                        .bucket(properties.bucket()).build());
                if (!exists) {
                    if (!properties.autoCreateBucket()) {
                        throw new IllegalStateException("MinIO bucket does not exist: " + properties.bucket());
                    }
                    client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                }
                if (properties.publicRead()) {
                    String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{"
                            + "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                            + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
                            + properties.bucket() + "/*\"]}]}";
                    client.setBucketPolicy(SetBucketPolicyArgs.builder()
                            .bucket(properties.bucket()).config(policy).build());
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to initialize MinIO bucket " + properties.bucket(), ex);
            }
        }
    }
}
