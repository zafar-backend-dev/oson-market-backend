package uz.osonmarket.auth.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminUserView(UUID id, String username, String email, String role, String status,
                            String firstName, String lastName, String phone, String language,
                            Instant emailVerifiedAt, Instant createdAt, Instant updatedAt) {}
