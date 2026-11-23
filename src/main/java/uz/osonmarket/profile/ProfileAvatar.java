package uz.osonmarket.profile;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "profile_avatars")
class ProfileAvatar {
    @Id
    private UUID id;

    @Column(nullable = false)
    private byte[] content;

    protected ProfileAvatar() {}

    ProfileAvatar(UUID id, byte[] content) {
        this.id = id;
        this.content = content;
    }

    byte[] content() {
        return content;
    }
}
