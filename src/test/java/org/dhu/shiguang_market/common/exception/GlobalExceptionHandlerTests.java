package org.dhu.shiguang_market.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.dhu.shiguang_market.common.util.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @Test
    void mapsMissingStaticResourcesToThePublic404Contract() {
        RequestContext.setRequestId("missing-resource-test");

        var response = handler.notFound(
                new NoResourceFoundException(HttpMethod.GET, "/favicon.ico", "favicon.ico"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().requestId()).isEqualTo("missing-resource-test");
    }
}
