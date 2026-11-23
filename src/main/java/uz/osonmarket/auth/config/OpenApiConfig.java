package uz.osonmarket.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import uz.osonmarket.auth.api.AuthController;
import uz.osonmarket.profile.ProfileController;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Oson Market Auth and Profile API",
        version = "1.0.0",
        description = "Send Accept-Language: uz or ru to localize responses. "
                + "To sign in with Google, open [Google authorization](/oauth2/authorization/google) in a browser."
))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
    @Bean
    OperationCustomizer authLanguageHeader() {
        return (operation, handler) -> {
            if (AuthController.class.isAssignableFrom(handler.getBeanType())
                    || ProfileController.class.isAssignableFrom(handler.getBeanType())) {
                var languages = new StringSchema();
                languages.setEnum(List.of("uz", "ru"));
                languages.setDefault("uz");
                operation.addParametersItem(new HeaderParameter()
                        .name(HttpHeaders.ACCEPT_LANGUAGE)
                        .description("Auth messages and verification email language.")
                        .schema(languages));
            }
            return operation;
        };
    }
}
