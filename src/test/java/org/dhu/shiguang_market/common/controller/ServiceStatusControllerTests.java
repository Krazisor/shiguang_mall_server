package org.dhu.shiguang_market.common.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.dhu.shiguang_market.common.util.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServiceStatusControllerTests {

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }

    @Test
    void reportsTheServiceAsRunningWithoutCheckingExternalDependencies() {
        RequestContext.setRequestId("root-test");

        var response = new ServiceStatusController().status();

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data().service()).isEqualTo("shiguang-market");
        assertThat(response.data().status()).isEqualTo("UP");
        assertThat(response.requestId()).isEqualTo("root-test");
    }
}
