package uz.osonmarket.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.osonmarket.auth.persistence.UserRepository;

@Configuration(proxyBeanMethods = false)
class OrderSocket {
    private final Map<Session, Client> clients = new ConcurrentHashMap<>();
    private final JwtDecoder decoder;
    private final UserRepository users;
    private final ObjectMapper json;
    private final Set<String> allowedOrigins;

    OrderSocket(JwtDecoder decoder, ObjectMapper json, UserRepository users, @Value("${app.cors-origin}") String origin,
                @Value("${app.local-frontend-origin}") String localOrigin,
                @Value("${app.mobile-frontend-origins}") List<String> mobileOrigins) {
        this.decoder = decoder;
        this.users = users;
        this.json = json;
        var origins = new LinkedHashSet<>(mobileOrigins);
        origins.add(origin);
        origins.add(localOrigin);
        this.allowedOrigins = Set.copyOf(origins);
    }

    @Bean
    ServletListenerRegistrationBean<ServletContextListener> orderSocketRegistration() {
        return new ServletListenerRegistrationBean<>(new ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                var container = (ServerContainer) event.getServletContext().getAttribute(ServerContainer.class.getName());
                if (container == null) throw new IllegalStateException("WebSocket container unavailable");
                try {
                    container.addEndpoint(ServerEndpointConfig.Builder.create(Connection.class, "/ws/orders")
                            .configurator(new ServerEndpointConfig.Configurator() {
                                @Override
                                public boolean checkOrigin(String offered) {
                                    return offered == null || allowedOrigins.contains(offered);
                                }

                                @Override
                                public <T> T getEndpointInstance(Class<T> type) {
                                    return type.cast(new Connection());
                                }
                            }).build());
                } catch (DeploymentException error) {
                    throw new IllegalStateException("Cannot register order WebSocket", error);
                }
            }
        });
    }

    public final class Connection extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.setMaxTextMessageBufferSize(8192);
            session.setMaxIdleTimeout(300_000);
            session.getAsyncRemote().setSendTimeout(5000);
            clients.put(session, new Client(null, Instant.now().plusSeconds(10), false));
            session.addMessageHandler(String.class, message -> authenticate(session, message));
        }

        @Override
        public void onClose(Session session, CloseReason reason) {
            clients.remove(session);
        }

        @Override
        public void onError(Session session, Throwable failure) {
            close(session);
        }
    }

    private void authenticate(Session session, String message) {
        var pending = clients.get(session);
        if (pending == null || pending.owner() != null || !pending.expiresAt().isAfter(Instant.now())) {
            close(session);
            return;
        }
        try {
            var input = json.readTree(message);
            if (input == null || !"AUTH".equals(input.path("type").asText())) {
                close(session);
                return;
            }
            var jwt = decoder.decode(input.path("accessToken").asText());
            if (jwt.getExpiresAt() == null) {
                close(session);
                return;
            }
            UUID owner = UUID.fromString(jwt.getSubject());
            boolean admin = users.findById(owner).filter(user -> "ADMIN".equals(user.getRole())).isPresent();
            var client = new Client(owner, jwt.getExpiresAt(), admin);
            if (clients.replace(session, pending, client)) {
                send(session, "{\"type\":\"READY\"}");
            }
        } catch (JsonProcessingException | JwtException | IllegalArgumentException error) {
            close(session);
        }
    }

    @TransactionalEventListener
    void publish(OrderOperationsService.OrderStatusEvent event) {
        String message = "{\"type\":\"ORDER_STATUS_CHANGED\",\"notificationId\":\"%s\",\"orderId\":\"%s\","
                .formatted(event.notificationId(), event.orderId())
                + "\"status\":\"%s\",\"createdAt\":\"%s\"".formatted(event.status(), event.createdAt())
                + (event.status() == OrderStatus.DELIVERED ? ",\"action\":\"RATE_SERVICE\"}" : "}");
        var now = Instant.now();
        String adminUpdate = "{\"type\":\"ORDER_CHANGED\",\"orderId\":\"%s\"}".formatted(event.orderId());
        clients.forEach((session, client) -> {
            if (!client.expiresAt().isAfter(now)) return;
            if (event.owner().equals(client.owner())) {
                send(session, message);
            } else if (client.admin()) {
                send(session, adminUpdate);
            }
        });
    }

    @TransactionalEventListener
    void publish(OrderOperationsService.OrderChangedEvent event) {
        String update = "{\"type\":\"ORDER_CHANGED\",\"orderId\":\"%s\"}".formatted(event.orderId());
        Instant now = Instant.now();
        clients.forEach((session, client) -> {
            if (client.expiresAt().isAfter(now)
                    && (client.admin() || (event.owner() != null && event.owner().equals(client.owner())))) {
                send(session, update);
            }
        });
    }

    @Scheduled(fixedDelay = 5000)
    void closeExpired() {
        Instant now = Instant.now();
        clients.forEach((session, client) -> {
            if (!client.expiresAt().isAfter(now)) close(session);
        });
    }

    private void send(Session session, String message) {
        try {
            session.getAsyncRemote().sendText(message, result -> {
                if (!result.isOK()) close(session);
            });
        } catch (IllegalStateException error) {
            close(session);
        }
    }

    private void close(Session session) {
        clients.remove(session);
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Reconnect with a valid session"));
        } catch (IOException | IllegalStateException ignored) {
            // The transport may already be closed.
        }
    }

    private record Client(UUID owner, Instant expiresAt, boolean admin) {}
}
