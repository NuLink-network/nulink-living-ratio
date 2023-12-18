package com.nulink.livingratio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

@Configuration
@EnableSwagger2WebMvc
public class Knife4jConfiguration {

    @Bean
    public Docket docket() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(new ApiInfoBuilder()
                        .description("NULink Living Ratio APIs")
                        .termsOfServiceUrl("")
                        .version("1.0")
                        .build())
                .groupName("1.0")
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.nulink.livingratio.controller"))
                .build();
    }
}
