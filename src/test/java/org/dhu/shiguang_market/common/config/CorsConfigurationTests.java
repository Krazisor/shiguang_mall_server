package org.dhu.shiguang_market.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class CorsConfigurationTests {
    private static final String DEPLOYED_FRONTEND_ORIGIN = "https://shiguangmallweb.zeabur.app";

    @Test
    void packagedDefaultsAllowTheDeployedFrontend() throws IOException {
        var environment = new MockEnvironment();
        var loader = new YamlPropertySourceLoader();
        loader.load("application", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("market.cors.allowed-origins"))
                .contains(DEPLOYED_FRONTEND_ORIGIN);
    }
}
