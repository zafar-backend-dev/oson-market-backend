package uz.osonmarket.auth.mail;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.api.AuthMessages;

@Component
public class BrevoMailer {
    private final RestClient client;
    private final String sender;
    private final AuthMessages messages;
    private final AuthEmailTemplate authTemplate;

    public BrevoMailer(RestClient.Builder builder, AuthMessages messages, AuthEmailTemplate authTemplate,
                       @Value("${brevo.base-url}") String baseUrl,
                       @Value("${brevo.api-key}") String apiKey,
                       @Value("${brevo.sender-email}") String sender) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(8000);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory)
                .defaultHeader("api-key", apiKey).build();
        this.messages = messages;
        this.authTemplate = authTemplate;
        this.sender = sender;
    }

    public void sendCode(String email, String purpose, String code, long ttlSeconds, Locale locale) {
        try {
            client.post().uri("/smtp/email").body(Map.of(
                    "sender", Map.of("email", sender, "name", "Oson Market"),
                    "to", List.of(Map.of("email", email)),
                    "subject", messages.text("mail." + purpose, locale),
                    "textContent", messages.text("mail.body", locale, code, ttlSeconds),
                    "htmlContent", authTemplate.render(purpose, code, ttlSeconds, locale)
            )).retrieve().toBodilessEntity();
        } catch (RestClientException ignored) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "mail.unavailable");
        }
    }

    public void sendHtml(String email, String subject, String html, UUID deliveryId) {
        client.post().uri("/smtp/email").body(Map.of(
                "sender", Map.of("email", sender, "name", "Oson Market"),
                "to", List.of(Map.of("email", email)),
                "subject", subject,
                "htmlContent", html,
                "headers", Map.of("X-Order-Delivery-Id", deliveryId.toString())
        )).retrieve().toBodilessEntity();
    }
}
