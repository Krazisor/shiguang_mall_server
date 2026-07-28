package org.dhu.shiguang_market.common.api;

import java.time.OffsetDateTime;
import org.dhu.shiguang_market.common.util.RequestContext;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data,
                RequestContext.requestId(), RequestContext.now());
    }
}
