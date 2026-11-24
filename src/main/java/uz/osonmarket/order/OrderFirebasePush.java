package uz.osonmarket.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class OrderFirebasePush {
    private static final URI TOKEN_URL = URI.create("https://oauth2.googleapis.com/token");
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(OrderFirebasePush.class);

    private final OrderPushDevices devices;
    private final ObjectMapper json;
    private final TaskExecutor executor;
    private final Account account;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String accessToken;
    private Instant refreshAt = Instant.EPOCH;

    OrderFirebasePush(OrderPushDevices devices, ObjectMapper json,
                      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                      @Value("${app.push.credentials-file:}") String credentialsFile) {
        this.devices = devices;
        this.json = json;
        this.executor = executor;
        this.account = credentialsFile.isBlank() ? null : readAccount(Path.of(credentialsFile));
    }

    @TransactionalEventListener
    public void publish(OrderOperationsService.OrderStatusEvent event) {
        if (account == null) return;
        try {
            executor.execute(() -> deliver(event));
        } catch (RuntimeException ex) {
            LOG.warn("Push delivery could not be queued ({})", ex.getClass().getSimpleName());
        }
    }

    private void deliver(OrderOperationsService.OrderStatusEvent event) {
        try {
            for (var device : devices.find(event.owner())) {
                try {
                    send(device, event);
                } catch (IOException | JOSEException ex) {
                    LOG.warn("Order push delivery failed ({})", ex.getClass().getSimpleName());
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ex) {
            LOG.warn("Order push processing failed ({})", ex.getClass().getSimpleName());
        }
    }

    private void send(OrderPushDevice device, OrderOperationsService.OrderStatusEvent event)
            throws IOException, InterruptedException, JOSEException {
        String body = "ru".equals(device.language)
                ? "\u0421\u0442\u0430\u0442\u0443\u0441 \u0432\u0430\u0448\u0435\u0433\u043e \u0437\u0430\u043a\u0430\u0437\u0430 \u043e\u0431\u043d\u043e\u0432\u0438\u043b\u0441\u044f."
                : "Buyurtmangiz holati yangilandi.";
        var payload = Map.of("message", Map.of(
                "token", device.token,
                "notification", Map.of("title", "Oson Market", "body", body),
                "data", Map.of("orderId", event.orderId().toString(),
                        "notificationId", event.notificationId().toString(), "status", event.status().name()),
                "android", Map.of("priority", "HIGH", "notification", Map.of(
                        "channel_id", "oson_order_updates", "sound", "default",
                        "tag", event.notificationId().toString()))));
        var request = HttpRequest.newBuilder(account.sendUrl()).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) return;
        if (response.statusCode() == 404) {
            for (var detail : json.readTree(response.body()).path("error").path("details")) {
                if ("UNREGISTERED".equals(detail.path("errorCode").asText())) {
                    devices.remove(device.userId, device.token);
                    return;
                }
            }
        }
        LOG.warn("Firebase rejected order push with HTTP {}", response.statusCode());
    }

    private synchronized String token() throws IOException, InterruptedException, JOSEException {
        Instant now = Instant.now();
        if (now.isBefore(refreshAt)) return accessToken;
        var assertion = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                .issuer(account.email()).audience(TOKEN_URL.toString()).claim("scope", SCOPE)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).build());
        assertion.sign(new RSASSASigner(account.key()));
        String form = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion="
                + URLEncoder.encode(assertion.serialize(), StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(TOKEN_URL).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Firebase OAuth HTTP " + response.statusCode());
        var result = json.readTree(response.body());
        accessToken = result.path("access_token").asText();
        if (accessToken.isBlank()) throw new IOException("Firebase OAuth did not return a token");
        refreshAt = now.plusSeconds(Math.max(0, result.path("expires_in").asLong() - 60));
        return accessToken;
    }

    private Account readAccount(Path path) {
        try (var input = Files.newInputStream(path)) {
            var config = json.readTree(input);
            String project = config.path("project_id").asText();
            String email = config.path("client_email").asText();
            if (!"service_account".equals(config.path("type").asText())
                    || !project.matches("[a-z0-9-]+") || email.isBlank()) {
                throw new IllegalArgumentException("Invalid service account");
            }
            String pem = config.path("private_key").asText().replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem));
            var key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
            return new Account(email, key, URI.create("https://fcm.googleapis.com/v1/projects/" + project + "/messages:send"));
        } catch (Exception ex) {
            throw new IllegalStateException("Firebase service account configuration is invalid");
        }
    }

    private record Account(String email, RSAPrivateKey key, URI sendUrl) {}
}
