package uz.osonmarket.order;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.auth.persistence.AuthLocks;

@Service
class OrderPushDevices {
    private final EntityManager entities;
    private final AuthLocks locks;

    OrderPushDevices(EntityManager entities, AuthLocks locks) {
        this.entities = entities;
        this.locks = locks;
    }

    @Transactional
    public void register(UUID owner, String token, String language) {
        locks.lock("order-push:" + token);
        var device = entities.find(OrderPushDevice.class, token);
        if (device == null) {
            device = new OrderPushDevice();
            device.token = token;
            device.userId = owner;
            device.language = language;
            entities.persist(device);
        } else {
            device.userId = owner;
            device.language = language;
        }
    }

    @Transactional
    public void remove(UUID owner, String token) {
        entities.createQuery("delete from OrderPushDevice d where d.token = :token and d.userId = :owner")
                .setParameter("token", token).setParameter("owner", owner).executeUpdate();
    }

    @Transactional(readOnly = true)
    public List<OrderPushDevice> find(UUID owner) {
        return entities.createQuery("select d from OrderPushDevice d where d.userId = :owner", OrderPushDevice.class)
                .setParameter("owner", owner).getResultList();
    }
}

@RestController
@RequestMapping("/api/orders/notifications/device")
class OrderPushDeviceController {
    private final OrderPushDevices devices;

    OrderPushDeviceController(OrderPushDevices devices) {
        this.devices = devices;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Registration input) {
        devices.register(UUID.fromString(jwt.getSubject()), input.token(), input.language());
    }

    @PostMapping("/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Token input) {
        devices.remove(UUID.fromString(jwt.getSubject()), input.token());
    }

    public record Registration(@NotBlank @Size(max = 512) String token,
                               @NotBlank @Pattern(regexp = "uz|ru") String language) {}
    public record Token(@NotBlank @Size(max = 512) String token) {}
}
