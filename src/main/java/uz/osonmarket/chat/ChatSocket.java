package uz.osonmarket.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.server.ResponseStatusException;

@Configuration(proxyBeanMethods = false)
class ChatSocket {
    private final Map<Session, Client> clients = new ConcurrentHashMap<>();
    private final Map<Session, Activity> activities = new ConcurrentHashMap<>();
    private final ChatService chat;
    private final JwtDecoder decoder;
    private final ObjectMapper json;
    private final List<String> origins;
    private final List<String> adminOrigins;

    ChatSocket(ChatService chat, JwtDecoder decoder, ObjectMapper json,
               @Value("${app.mobile-frontend-origins}") List<String> origins,
               @Value("${app.cors-origin}") String webOrigin,
               @Value("${app.local-frontend-origin}") String localOrigin) {
        this.chat = chat;
        this.decoder = decoder;
        this.json = json;
        this.origins = origins;
        this.adminOrigins = List.of(webOrigin, localOrigin);
    }

    @Bean
    ServletListenerRegistrationBean<ServletContextListener> chatSocketRegistration() {
        return new ServletListenerRegistrationBean<>(new ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                var container = (ServerContainer) event.getServletContext().getAttribute(ServerContainer.class.getName());
                try {
                    register(container, "/ws/mobile/chat", origins, false);
                    register(container, "/ws/admin/chat", adminOrigins, true);
                } catch (DeploymentException error) {
                    throw new IllegalStateException("Cannot register chat WebSocket", error);
                }
            }
        });
    }

    private void register(ServerContainer container, String path, List<String> allowed, boolean adminOnly)
            throws DeploymentException {
                    container.addEndpoint(ServerEndpointConfig.Builder.create(Connection.class, path)
                            .configurator(new ServerEndpointConfig.Configurator() {
                                @Override
                                public boolean checkOrigin(String origin) {
                                    return origin != null && allowed.contains(origin);
                                }

                                @Override
                                public <T> T getEndpointInstance(Class<T> type) {
                                    return type.cast(new Connection(adminOnly));
                                }
                            }).build());
    }

    public final class Connection extends Endpoint {
        private final boolean adminOnly;

        Connection(boolean adminOnly) {
            this.adminOnly = adminOnly;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.setMaxTextMessageBufferSize(32768);
            session.setMaxIdleTimeout(120_000);
            session.getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 3000L);
            clients.put(session, new Client(null, null, Instant.now().plusSeconds(10)));
            session.addMessageHandler(String.class, frame -> receive(session, frame, adminOnly));
        }

        @Override
        public void onClose(Session session, CloseReason reason) {
            clients.remove(session);
            stopActivity(session);
        }

        @Override
        public void onError(Session session, Throwable failure) {
            close(session);
        }
    }

    private void receive(Session session, String frame, boolean adminOnly) {
        var client = clients.get(session);
        if (client == null || !client.expires().isAfter(Instant.now())) {
            close(session);
            return;
        }
        String requestId = "";
        try {
            JsonNode input = json.readTree(frame);
            if (input == null || !input.isObject()) throw new IllegalArgumentException();
            String type = input.path("type").asText();
            if (client.actor() == null) {
                if (!"AUTH".equals(type)) {
                    close(session);
                    return;
                }
                String token = input.path("accessToken").asText();
                var jwt = decoder.decode(token);
                if (jwt.getExpiresAt() == null) throw new IllegalArgumentException();
                var actor = chat.actor(UUID.fromString(jwt.getSubject()));
                if (adminOnly && !actor.admin()) {
                    close(session);
                    return;
                }
                if (clients.replace(session, client, new Client(actor, token, jwt.getExpiresAt()))) {
                    send(session, Map.of("type", "READY"));
                }
                return;
            }
            requestId = input.path("requestId").asText();
            var actor = currentActor(client);
            UUID userId = input.hasNonNull("userId") ? UUID.fromString(input.path("userId").asText()) : null;
            switch (type) {
                case "SEND_TEXT" -> {
                    var saved = chat.send(actor, userId, UUID.fromString(requestId),
                            input.path("text").isTextual() ? input.path("text").textValue() : null, null);
                    stopActivity(session);
                    send(session, Map.of("type", "ACK", "requestId", requestId, "data", saved));
                }
                case "ACTIVITY" -> updateActivity(session, actor, userId, input.path("activity").asText());
                case "MARK_READ" -> {
                    var receipt = chat.read(actor, userId, input.path("throughId").asLong());
                    send(session, Map.of("type", "ACK", "requestId", requestId, "data", receipt));
                }
                case "HISTORY" -> {
                    JsonNode after = input.path("afterId");
                    JsonNode before = input.path("beforeId");
                    if (!after.isMissingNode() && !before.isMissingNode()) throw new IllegalArgumentException();
                    long cursor = after.isMissingNode() ? before.asLong(Long.MAX_VALUE) : after.asLong(0);
                    var messages = chat.history(actor, userId, cursor, !after.isMissingNode(), pageSize(input));
                    send(session, Map.of("type", "HISTORY", "requestId", requestId, "data", messages));
                }
                case "CONVERSATIONS" -> {
                    var conversations = chat.conversations(actor, input.path("beforeId").asLong(Long.MAX_VALUE),
                            pageSize(input));
                    send(session, Map.of("type", "CONVERSATIONS", "requestId", requestId, "data", conversations));
                }
                case "UNREAD_COUNT" -> send(session, Map.of("type", "UNREAD_COUNT", "requestId", requestId,
                        "count", chat.unread(actor, userId)));
                case "PING" -> send(session, Map.of("type", "PONG"));
                default -> throw new IllegalArgumentException();
            }
        } catch (JwtException error) {
            close(session);
        } catch (ResponseStatusException error) {
            if (client.actor() == null) close(session);
            else sendError(session, requestId, error.getStatusCode().value());
        } catch (JsonProcessingException | IllegalArgumentException error) {
            if (client.actor() == null) close(session);
            else sendError(session, requestId, 400);
        } catch (RuntimeException error) {
            if (client.actor() == null) close(session);
            else sendError(session, requestId, 500);
        }
    }

    private ChatService.Actor currentActor(Client client) {
        decoder.decode(client.token());
        var actor = chat.actor(client.actor().id());
        if (actor.admin() != client.actor().admin()) throw new JwtException("Chat role changed");
        return actor;
    }

    private int pageSize(JsonNode input) {
        return Math.max(1, Math.min(input.path("limit").asInt(30), 100));
    }

    private void updateActivity(Session session, ChatService.Actor actor, UUID requested, String kind) {
        UUID owner = chat.owner(actor, requested);
        if ("IDLE".equals(kind)) {
            stopActivity(session, owner);
            return;
        }
        if (!"TYPING".equals(kind) && !"RECORDING_VOICE".equals(kind)) {
            throw new IllegalArgumentException();
        }
        Activity previous = activities.get(session);
        if (previous != null && previous.owner().equals(owner) && previous.kind().equals(kind)
                && previous.expires().isAfter(Instant.now().plusSeconds(5))) return;
        if (previous != null && !previous.owner().equals(owner)) stopActivity(session, previous.owner());
        var activity = new Activity(owner, actor.admin(), kind, Instant.now().plusSeconds(8));
        activities.put(session, activity);
        if (!clients.containsKey(session)) {
            stopActivity(session);
            return;
        }
        sendActivity(session, activity, activity.kind());
    }

    private void stopActivity(Session session) {
        var activity = activities.remove(session);
        if (activity != null) sendActivity(session, activity, "IDLE");
    }

    private void stopActivity(Session session, UUID owner) {
        var activity = activities.get(session);
        if (activity != null && activity.owner().equals(owner) && activities.remove(session, activity)) {
            sendActivity(session, activity, "IDLE");
        }
    }

    private void sendActivity(Session source, Activity activity, String kind) {
        var frame = Map.of("type", "ACTIVITY", "userId", activity.owner(),
                "activity", kind, "activityId", source.getId(), "expiresAt", activity.expires());
        clients.forEach((session, client) -> {
            var actor = client.actor();
            if (actor == null || actor.admin() == activity.senderAdmin()
                    || (!actor.admin() && !actor.id().equals(activity.owner()))
                    || !client.expires().isAfter(Instant.now())) return;
            try {
                var current = currentActor(client);
                if (current.admin() != activity.senderAdmin()
                        && (current.admin() || current.id().equals(activity.owner()))) send(session, frame);
            } catch (RuntimeException error) {
                close(session);
            }
        });
    }

    @TransactionalEventListener
    void publish(ChatService.Changed changed) {
        clients.forEach((session, client) -> {
            if (client.actor() == null || !client.expires().isAfter(Instant.now())
                    || (!client.actor().admin() && !client.actor().id().equals(changed.owner()))) return;
            try {
                var actor = currentActor(client);
                if (actor.admin() || actor.id().equals(changed.owner())) {
                    send(session, Map.of("type", changed.type(), "data", changed.data()));
                }
            } catch (RuntimeException error) {
                close(session);
            }
        });
    }

    @Scheduled(fixedDelay = 5000)
    void closeExpired() {
        var now = Instant.now();
        clients.forEach((session, client) -> {
            if (!client.expires().isAfter(now)) close(session);
        });
    }

    @Scheduled(fixedDelay = 1000)
    void expireActivities() {
        var now = Instant.now();
        activities.forEach((session, activity) -> {
            if (!activity.expires().isAfter(now)) activities.remove(session, activity);
        });
    }

    private void sendError(Session session, String requestId, int status) {
        send(session, Map.of("type", "ERROR", "requestId", requestId, "status", status));
    }

    private void send(Session session, Object message) {
        try {
            synchronized (session) {
                if (session.isOpen()) session.getBasicRemote().sendText(json.writeValueAsString(message));
            }
        } catch (IOException | IllegalStateException error) {
            close(session);
        }
    }

    private void close(Session session) {
        clients.remove(session);
        stopActivity(session);
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Reconnect with a valid session"));
        } catch (IOException | IllegalStateException ignored) {
            // The connection may already be closed.
        }
    }

    private record Client(ChatService.Actor actor, String token, Instant expires) {}
    private record Activity(UUID owner, boolean senderAdmin, String kind, Instant expires) {}
}
