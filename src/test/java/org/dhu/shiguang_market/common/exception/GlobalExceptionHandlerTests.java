package org.dhu.shiguang_market.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.dhu.shiguang_market.common.util.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }

    @Test
    void mapsUnavailableDatabaseOrRedisToThePublic503Contract() {
        RequestContext.setRequestId("dependency-test");

        var connectionFailure = handler.dependencyUnavailable(
                new DataAccessResourceFailureException("connection refused"));
        var timeout = handler.dependencyUnavailable(new QueryTimeoutException("timed out"));

        assertThat(connectionFailure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(connectionFailure.getBody().code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(connectionFailure.getBody().requestId()).isEqualTo("dependency-test");
        assertThat(timeout.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(timeout.getBody().code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
    }
}
