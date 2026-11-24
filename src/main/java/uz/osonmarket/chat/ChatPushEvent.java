package uz.osonmarket.chat;

import java.util.UUID;

public record ChatPushEvent(UUID recipientId, long messageId) {}
