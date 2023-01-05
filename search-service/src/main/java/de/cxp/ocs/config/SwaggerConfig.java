package de.cxp.ocs.config;

import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {

	@Bean
	public Docket produceApi() {
		return new Docket(DocumentationType.SWAGGER_2)
				.apiInfo(apiInfo())
				.produces(Collections.singleton("application/json"))
				.select()
				.apis(RequestHandlerSelectors.basePackage("de.cxp.ocs"))
				.paths(PathSelectors.regex("/search-api/.*"))
				.build()
				.pathMapping("/") ;
	}

	private ApiInfo apiInfo() {
		return new ApiInfoBuilder()
				.title("Open Commerce Search API")
				.description("A common product search API that separates its usage from required search expertise")
				.version("1.0-TODO")
				.build();
	}

}
