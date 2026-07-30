package org.dhu.shiguang_market.common.controller;

import org.dhu.shiguang_market.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceStatusController {

    @GetMapping("/")
    public ApiResponse<ServiceStatus> status() {
        return ApiResponse.success(new ServiceStatus("shiguang-market", "UP"));
    }

    public record ServiceStatus(String service, String status) {
    }
}
