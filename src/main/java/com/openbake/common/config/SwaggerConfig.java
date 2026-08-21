package com.openbake.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
//        SecurityScheme bearerScheme = new SecurityScheme()
//                .type(SecurityScheme.Type.HTTP)
//                .scheme("bearer")
//                .bearerFormat("JWT");
        Components components = new Components()
                .addSecuritySchemes(
                        "memberId",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Openbake-Member-Id")
                )
                .addSecuritySchemes(
                        "memberRole",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Openbake-Member-Role")
                )
                .addSecuritySchemes(
                        "authSource",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Openbake-Auth-Source")
                );

        SecurityRequirement securityRequirement =
                new SecurityRequirement()
                        .addList("memberId")
                        .addList("memberRole")
                        .addList("authSource");

        return new OpenAPI()
                .info(new Info()
                        .title("OpenBake API")
                        .description("OpenBake 백엔드 API 명세")
                        .version("v1"))
                // gateway가 프록시 시 Host 헤더를 내부 서비스명(예: backend:8080)으로 바꾸는데,
                // springdoc이 이를 그대로 servers URL로 써버려 브라우저에서 이름 해석이 안 되는 문제를 막기 위해 상대경로 고정
                .servers(List.of(new Server().url("/")))
//                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme))
//                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(components)
                .addSecurityItem(securityRequirement);

    }
}
