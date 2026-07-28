package org.dhu.shiguang_market.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.util.RequestContext;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code, String message, List<FieldErrorDetail> details,
        String requestId, OffsetDateTime timestamp) {

    public static ApiErrorResponse of(String code, String message, List<FieldErrorDetail> details) {
        return new ApiErrorResponse(code, message, details, RequestContext.requestId(), RequestContext.now());
    }
}
