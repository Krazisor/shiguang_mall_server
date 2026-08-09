package org.dhu.shiguang_market.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.dhu.shiguang_market.common.util.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[\\x21-\\x7E]{1,64}$");
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-Id");
        String requestId = supplied != null && REQUEST_ID_PATTERN.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        RequestContext.setRequestId(requestId);
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        long started = System.nanoTime();
        log.info("HTTP request started method={} path={}", request.getMethod(), request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            log.info("HTTP request completed method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            MDC.remove("userId");
            MDC.remove("requestId");
            RequestContext.clear();
        }
    }
}
