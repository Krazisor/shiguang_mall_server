package org.dhu.shiguang_market.address.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.dhu.shiguang_market.address.service.AddressService;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.CommonViews.AddressView;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.AddressUpsertRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<List<AddressView>> list() {
        return ApiResponse.success(addressService.list());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressView>> create(@Valid @RequestBody AddressUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(addressService.create(request)));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<AddressView> update(@PathVariable long addressId,
                                           @Valid @RequestBody AddressUpsertRequest request) {
        return ApiResponse.success(addressService.update(addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(@PathVariable long addressId) {
        addressService.delete(addressId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{addressId}/default")
    public ApiResponse<AddressView> makeDefault(@PathVariable long addressId) {
        return ApiResponse.success(addressService.makeDefault(addressId));
    }
}
