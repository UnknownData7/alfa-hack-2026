package ru.hackathon.credit.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI creditOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Умный кредитный кабинет предпринимателя")
                        .version("0.1.0")
                        .description("API цифрового кредитного конвейера: клиентский путь и обработка заявки банком. "
                                + "Все данные синтетические, внешние системы смоделированы."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
