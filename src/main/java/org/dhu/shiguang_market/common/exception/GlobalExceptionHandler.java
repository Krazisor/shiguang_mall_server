package org.dhu.shiguang_market.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import java.util.List;
import org.dhu.shiguang_market.common.api.ApiErrorResponse;
import org.dhu.shiguang_market.common.api.FieldErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> business(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiErrorResponse> validation(Exception ex) {
        var binding = ex instanceof MethodArgumentNotValidException manv
                ? manv.getBindingResult() : ((BindException) ex).getBindingResult();
        List<FieldErrorDetail> details = binding.getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_FAILED", "请求参数校验失败", details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> badRequest(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("BAD_REQUEST", "请求参数格式错误", null));
    }

    @ExceptionHandler(NotLoginException.class)
    ResponseEntity<ApiErrorResponse> notLogin(NotLoginException ex) {
        String code = switch (ex.getType()) {
            case NotLoginException.TOKEN_TIMEOUT -> "AUTH_TOKEN_EXPIRED";
            case NotLoginException.BE_REPLACED -> "AUTH_TOKEN_REPLACED";
            case NotLoginException.KICK_OUT -> "AUTH_TOKEN_KICKED_OUT";
            default -> "AUTH_NOT_LOGGED_IN";
        };
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(code, "登录状态无效", null));
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    ResponseEntity<ApiErrorResponse> denied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("AUTH_PERMISSION_DENIED", "权限不足", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("RESOURCE_CONFLICT", "资源关系或唯一字段冲突", null));
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    ResponseEntity<ApiErrorResponse> dependencyUnavailable(RuntimeException ex) {
        log.warn("Required dependency is temporarily unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of("DEPENDENCY_UNAVAILABLE", "必要依赖暂时不可用", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
        log.error("Unhandled request failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "服务端处理失败", null));
    }
}
