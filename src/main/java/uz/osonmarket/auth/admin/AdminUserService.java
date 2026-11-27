package uz.osonmarket.auth.admin;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.admin.AdminUserController.Role;
import uz.osonmarket.auth.admin.AdminUserController.Status;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.persistence.UserRepository;

@Service
class AdminUserService {
    private final AdminUserRepository views;
    private final UserRepository users;

    AdminUserService(AdminUserRepository views, UserRepository users) {
        this.views = views;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PagedModel<AdminUserView> list(String q, Role role, Status status, int page, int size) {
        String query = q.strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new PagedModel<>(views.search(role == null ? "" : role.name(), status == null ? "" : status.name(),
                query, pattern, PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public AdminUserView get(UUID id) {
        return views.findView(id).orElseThrow(this::notFound);
    }

    @Transactional
    public AdminUserView updateStatus(UUID id, Status status) {
        var user = users.findForUpdate(id).orElseThrow(this::notFound);
        if (!Role.USER.name().equals(user.getRole()) || "DELETION_PENDING".equals(user.getStatus())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "admin.users.status.forbidden");
        }
        if (!status.name().equals(user.getStatus())) {
            user.setStatus(status.name());
            user.setCredentialVersion(user.getCredentialVersion() + 1);
            user.setUpdatedAt(Instant.now());
        }
        return get(id);
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "admin.users.notFound");
    }
}
