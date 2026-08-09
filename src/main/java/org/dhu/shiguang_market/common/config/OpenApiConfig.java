package org.dhu.shiguang_market.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI shiguangMarketOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("时光商城 API")
                        .description("时光商城后端接口文档")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("satoken", new SecurityScheme()
                                .name("satoken")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("登录后获得的 Sa-Token。仅需要登录的接口才需填写。")))
                .addSecurityItem(new SecurityRequirement().addList("satoken"));
    }
}
