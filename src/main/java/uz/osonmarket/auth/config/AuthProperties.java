package uz.osonmarket.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(long codeTtlSeconds, long resendSeconds, int maxAttempts) {
    public AuthProperties {
        if (codeTtlSeconds <= 0 || resendSeconds <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("Invalid app.auth settings");
        }
    }
}
