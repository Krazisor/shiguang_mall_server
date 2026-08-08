package org.dhu.shiguang_market.storage.controller;

import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.model.MarketEnums.AssetPurpose;
import org.dhu.shiguang_market.storage.dto.AssetDtos.AssetUploadView;
import org.dhu.shiguang_market.storage.service.ObjectStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assets")
public class ObjectAssetController {
    private final ObjectStorageService service;

    public ObjectAssetController(ObjectStorageService service) {
        this.service = service;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssetUploadView>> uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") AssetPurpose purpose,
            @RequestParam(value = "shopId", required = false) Long shopId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.uploadImage(file, purpose, shopId)));
    }
}
