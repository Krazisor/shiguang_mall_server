package org.dhu.shiguang_market.storage.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AssetPurpose;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.storage.config.StorageProperties;
import org.dhu.shiguang_market.storage.dto.AssetDtos.AssetUploadView;
import org.dhu.shiguang_market.storage.mapper.ObjectAssetMapper;
import org.dhu.shiguang_market.storage.model.ObjectAsset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ObjectStorageService {
    private static final String PRODUCT_PERMISSION = "shop:product:manage";
    private static final String PLATFORM_SHOP_PERMISSION = "platform:shop:manage";
    private static final String PLATFORM_CATALOG_PERMISSION = "platform:catalog:manage";
    private static final String[] IMAGE_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp"};

    private final CurrentUserService currentUser;
    private final ShopAccessService shopAccess;
    private final ObjectProvider<MinioClient> minioProvider;
    private final StorageProperties properties;
    private final ObjectAssetMapper assetMapper;
    private final NumberGenerator numberGenerator;

    public ObjectStorageService(CurrentUserService currentUser, ShopAccessService shopAccess,
                                ObjectProvider<MinioClient> minioProvider, StorageProperties properties,
                                ObjectAssetMapper assetMapper, NumberGenerator numberGenerator) {
        this.currentUser = currentUser;
        this.shopAccess = shopAccess;
        this.minioProvider = minioProvider;
        this.properties = properties;
        this.assetMapper = assetMapper;
        this.numberGenerator = numberGenerator;
    }

    public AssetUploadView uploadImage(MultipartFile file, AssetPurpose purpose, Long shopId) {
        long userId = currentUser.id();
        authorize(purpose, shopId);
        if (!properties.enabled() || minioProvider.getIfAvailable() == null) {
            throw BusinessException.unprocessable("STORAGE_NOT_CONFIGURED", "MinIO 对象存储未启用");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("UPLOAD_FILE_REQUIRED", "必须上传图片文件");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw BusinessException.payloadTooLarge("UPLOAD_FILE_TOO_LARGE", "图片大小超过限制");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.unavailable("DEPENDENCY_UNAVAILABLE", "读取上传文件失败");
        }
        DetectedImage detected = detect(bytes);
        String suppliedType = Formatters.trimToNull(file.getContentType());
        if (suppliedType != null && !detected.contentType().equalsIgnoreCase(suppliedType)) {
            throw BusinessException.badRequest("UPLOAD_CONTENT_TYPE_MISMATCH", "文件类型与内容不一致");
        }
        String objectKey = objectKey(purpose, detected.extension());
        MinioClient client = minioProvider.getIfAvailable();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(detected.contentType())
                    .build());
            String assetUrl = url(client, objectKey);
            ObjectAsset asset = new ObjectAsset();
            asset.setAssetNo(numberGenerator.next("ASSET"));
            asset.setBucket(properties.bucket());
            asset.setObjectKey(objectKey);
            asset.setOriginalFilename(safeFilename(file.getOriginalFilename()));
            asset.setContentType(detected.contentType());
            asset.setSizeBytes((long) bytes.length);
            asset.setSha256(sha256(bytes));
            asset.setPurpose(purpose);
            asset.setOwnerUserId(userId);
            asset.setShopId(shopId);
            asset.setPublicUrl(properties.publicRead() ? publicUrl(objectKey) : null);
            asset.setStatus(org.dhu.shiguang_market.common.model.MarketEnums.AssetStatus.ACTIVE);
            if (assetMapper.insert(asset) != 1) {
                throw new IllegalStateException("对象元数据写入失败");
            }
            ObjectAsset persisted = assetMapper.selectById(asset.getId());
            if (persisted == null) {
                throw new IllegalStateException("对象元数据回查失败");
            }
            return new AssetUploadView(Formatters.id(persisted.getId()), persisted.getAssetNo(), purpose,
                    persisted.getBucket(), persisted.getObjectKey(), persisted.getOriginalFilename(),
                    persisted.getContentType(), persisted.getSizeBytes(), persisted.getSha256(),
                    assetUrl, Formatters.time(persisted.getCreatedAt()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            removeQuietly(client, objectKey);
            throw BusinessException.unavailable("DEPENDENCY_UNAVAILABLE", "对象存储上传失败");
        }
    }

    private void authorize(AssetPurpose purpose, Long shopId) {
        boolean requiresPlatformUploadPermission = true;
        switch (purpose) {
            case AVATAR, AFTER_SALE_EVIDENCE, APPEAL_EVIDENCE -> {
                if (shopId != null) {
                    throw BusinessException.badRequest("UPLOAD_SCOPE_INVALID", "该用途不能提交 shopId");
                }
            }
            case SHOP_LOGO -> {
                if (shopId != null) throw BusinessException.badRequest("UPLOAD_SCOPE_INVALID", "店铺 Logo 不能提交 shopId");
                currentUser.requirePermission(PLATFORM_SHOP_PERMISSION);
            }
            case BRAND_LOGO -> {
                if (shopId != null) throw BusinessException.badRequest("UPLOAD_SCOPE_INVALID", "品牌 Logo 不能提交 shopId");
                currentUser.requirePermission(PLATFORM_CATALOG_PERMISSION);
            }
            case PRODUCT_COVER, PRODUCT_GALLERY, SKU_IMAGE, RICH_TEXT_IMAGE -> {
                requiresPlatformUploadPermission = false;
                if (shopId == null) throw BusinessException.badRequest("UPLOAD_SCOPE_REQUIRED", "商品图片必须提交 shopId");
                shopAccess.require(shopId, PRODUCT_PERMISSION);
            }
        }
        if (requiresPlatformUploadPermission) {
            currentUser.requirePermission("asset:upload");
        }
    }

    private String objectKey(AssetPurpose purpose, String extension) {
        String prefix = Formatters.trimToNull(properties.objectPrefix());
        String root = prefix == null ? "assets" : prefix.replaceAll("^/+|/+$", "");
        return root + "/" + LocalDate.now(ZoneOffset.ofHours(8)) + "/"
                + purpose.name().toLowerCase(Locale.ROOT) + "/"
                + UUID.randomUUID() + "." + extension;
    }

    private String url(MinioClient client, String objectKey) throws Exception {
        if (properties.publicRead()) return publicUrl(objectKey);
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(properties.bucket()).object(objectKey)
                .expiry(1, TimeUnit.HOURS).build());
    }

    private String publicUrl(String objectKey) {
        String base = Objects.requireNonNullElse(properties.publicBaseUrl(), properties.endpoint())
                .replaceAll("/+$", "");
        return URI.create(base + "/" + properties.bucket() + "/" + objectKey).toString();
    }

    private String safeFilename(String filename) {
        String value = Formatters.trimToNull(filename);
        if (value == null) return "upload";
        String normalized = value.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算文件摘要", ex);
        }
    }

    private DetectedImage detect(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return new DetectedImage("image/png", "png");
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return new DetectedImage("image/jpeg", "jpg");
        if (bytes.length >= 6 && (bytes[0] == 'G') && (bytes[1] == 'I') && (bytes[2] == 'F')
                && bytes[3] == '8') return new DetectedImage("image/gif", "gif");
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B'
                && bytes[11] == 'P') return new DetectedImage("image/webp", "webp");
        throw BusinessException.badRequest("UPLOAD_IMAGE_TYPE_UNSUPPORTED", "只支持 JPEG、PNG、GIF、WEBP 图片");
    }

    private void removeQuietly(MinioClient client, String objectKey) {
        try {
            client.removeObject(io.minio.RemoveObjectArgs.builder()
                    .bucket(properties.bucket()).object(objectKey).build());
        } catch (Exception ignored) {
            // The failed upload is still reported; a later cleanup job can remove this orphan.
        }
    }

    private record DetectedImage(String contentType, String extension) {
    }
}
