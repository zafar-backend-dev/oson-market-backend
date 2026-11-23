package uz.osonmarket.order.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class TelegramOrderSender {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramOrderSender.class);
    private final RestClient client;
    private final String token;

    TelegramOrderSender(RestClient.Builder builder, @Value("${app.order-telegram.bot-token:}") String token) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.client = builder.clone().baseUrl("https://api.telegram.org").requestFactory(factory).build();
        this.token = token;
    }

    boolean configured() {
        return !token.isBlank();
    }

    boolean send(long chatId, String message) {
        try {
            var response = client.post().uri("/bot{token}/sendMessage", token)
                    .body(Map.of("chat_id", chatId, "text", message))
                    .retrieve().body(JsonNode.class);
            if (response != null && response.path("ok").asBoolean()) {
                return true;
            }
        } catch (RestClientException ex) {
            // Exception details may include the bot token in the request URL.
        }
        LOG.warn("Order notification could not be delivered to a Telegram recipient");
        return false;
    }
}
