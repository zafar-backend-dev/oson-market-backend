package uz.osonmarket.branch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.catalog.media.CatalogImages;
import uz.osonmarket.profile.AvatarImages.Image;

@Service
public class BranchService {
    private final BranchRepository branches;
    private final CatalogImages images;

    BranchService(BranchRepository branches, CatalogImages images) {
        this.branches = branches;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public PagedModel<View> list(Pageable pageable) {
        var page = PageRequest.of(pageable.getPageNumber(), Math.min(100, pageable.getPageSize()), Sort.by("code"));
        return new PagedModel<>(branches.findAll(page).map(BranchService::view));
    }

    @Transactional(readOnly = true)
    public View get(UUID id) {
        return view(find(id));
    }

    @Transactional
    public View save(UUID id, BranchAdminController.Input input, Image image) {
        var branch = id == null ? new Branch() : find(id);
        boolean duplicate = id == null ? branches.existsByCode(input.code()) : branches.existsByCodeAndIdNot(input.code(), id);
        if (duplicate) throw duplicateCode();
        String phone = input.phone().replaceAll("[ ()-]", "");
        if (!phone.matches("\\+?[0-9]{7,15}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "branch.phone.invalid");
        }
        branch.code = input.code();
        branch.nameUz = input.nameUz().strip();
        branch.nameRu = input.nameRu().strip();
        branch.addressLine = input.addressLine().strip();
        branch.city = input.city().strip();
        branch.phone = phone;
        branch.email = input.email();
        branch.workingHours = input.workingHours();
        branch.latitude = input.location().latitude();
        branch.longitude = input.location().longitude();
        branch.status = input.active() ? "ACTIVE" : "INACTIVE";
        branch.pickupEnabled = input.pickupEnabled();
        branch.deliveryEnabled = input.deliveryEnabled();
        branch.pricePerKm = input.pricePerKm();
        branch.updatedAt = Instant.now();
        if (id == null) branch.createdAt = branch.updatedAt;
        if (image != null) branch.image = images.store(image, "branch");
        try {
            return view(branches.saveAndFlush(branch));
        } catch (DataIntegrityViolationException error) {
            throw duplicateCode();
        }
    }

    @Transactional
    public View image(UUID id, Image replacement) {
        var branch = find(id);
        var old = branch.image;
        branch.image = replacement == null ? null : images.store(replacement, "branch");
        branch.updatedAt = Instant.now();
        branches.flush();
        images.deleteOwned(old, "branch");
        return view(branch);
    }

    @Transactional(readOnly = true)
    public byte[] image(UUID id) {
        var branch = find(id);
        if (branch.image == null) throw new AuthException(HttpStatus.NOT_FOUND, "branch.image.notFound");
        return images.readMedia(branch.image);
    }

    @Transactional
    public void archive(UUID id) {
        var branch = find(id);
        branch.status = "INACTIVE";
        branch.updatedAt = Instant.now();
    }

    @Transactional(readOnly = true)
    public PagedModel<CustomerView> available(Pageable pageable, String language) {
        var page = PageRequest.of(pageable.getPageNumber(), Math.min(100, pageable.getPageSize()), Sort.by("code"));
        return new PagedModel<>(branches.findByStatus("ACTIVE", page).map(b -> customerView(b, language)));
    }

    @Transactional(readOnly = true)
    public byte[] publicImage(UUID id) {
        var branch = find(id);
        if (!"ACTIVE".equals(branch.status) || branch.image == null) {
            throw new AuthException(HttpStatus.NOT_FOUND, "branch.image.notFound");
        }
        return images.readMedia(branch.image);
    }

    private static CustomerView customerView(Branch b, String language) {
        String name = "ru".equals(language) ? b.nameRu : b.nameUz;
        return new CustomerView(b.id, name == null ? b.code : name, b.addressLine, b.city, b.phone, b.email,
                b.workingHours, b.latitude, b.longitude, Boolean.TRUE.equals(b.pickupEnabled),
                Boolean.TRUE.equals(b.deliveryEnabled), b.image == null ? null : "/api/branches/" + b.id + "/image");
    }

    public record CustomerView(UUID id, String name, String addressLine, String city, String phone, String email,
                               String workingHours, BigDecimal latitude, BigDecimal longitude, boolean pickupEnabled,
                               boolean deliveryEnabled, String imageUrl) {}

    @Transactional(readOnly = true)
    public void requirePickup(UUID id) {
        var branch = find(id);
        if (!"ACTIVE".equals(branch.status) || !Boolean.TRUE.equals(branch.pickupEnabled)) {
            throw new AuthException(HttpStatus.CONFLICT, "branch.pickup.unavailable");
        }
    }

    @Transactional(readOnly = true)
    public Quote quote(UUID id, BranchAdminController.Location destination) {
        var branch = find(id);
        if (!"ACTIVE".equals(branch.status) || !Boolean.TRUE.equals(branch.deliveryEnabled)
                || branch.pricePerKm == null || branch.latitude == null || branch.longitude == null) {
            throw new AuthException(HttpStatus.CONFLICT, "branch.delivery.unavailable");
        }
        double lat1 = Math.toRadians(branch.latitude.doubleValue());
        double lat2 = Math.toRadians(destination.latitude().doubleValue());
        double dLat = Math.sin((lat2 - lat1) / 2);
        double dLon = Math.sin(Math.toRadians(destination.longitude().subtract(branch.longitude).doubleValue()) / 2);
        double a = dLat * dLat + Math.cos(lat1) * Math.cos(lat2) * dLon * dLon;
        BigDecimal km = BigDecimal.valueOf(6371.0088 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a)))))
                .setScale(3, RoundingMode.HALF_UP);
        return new Quote(id, km, branch.pricePerKm,
                km.multiply(branch.pricePerKm).setScale(2, RoundingMode.HALF_UP), "UZS", "STRAIGHT_LINE");
    }

    private Branch find(UUID id) {
        return branches.findById(id).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "branch.notFound"));
    }

    private static AuthException duplicateCode() {
        return new AuthException(HttpStatus.CONFLICT, "branch.code.duplicate");
    }

    private static View view(Branch b) {
        return new View(b.id, b.code, b.nameUz, b.nameRu, b.addressLine, b.city, b.phone, b.email, b.workingHours,
                b.latitude, b.longitude, b.status, b.pickupEnabled, b.deliveryEnabled, b.pricePerKm,
                b.image == null ? null : "/api/admin/branches/" + b.id + "/image");
    }

    public record View(UUID id, String code, String nameUz, String nameRu, String addressLine, String city,
                       String phone, String email, String workingHours, BigDecimal latitude, BigDecimal longitude,
                       String status, Boolean pickupEnabled, Boolean deliveryEnabled, BigDecimal pricePerKm, String imageUrl) {}

    public record Quote(UUID branchId, BigDecimal distanceKm, BigDecimal pricePerKm, BigDecimal deliveryPrice,
                        String currency, String distanceMethod) {}
}
