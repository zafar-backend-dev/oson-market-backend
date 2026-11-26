package uz.osonmarket.release;

import com.nimbusds.jose.JOSEException;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uz.osonmarket.order.OrderFirebasePush;

@RestController
public class AndroidReleaseController {
    private final AndroidRelease release;
    private final OrderFirebasePush push;

    public AndroidReleaseController(OrderFirebasePush push,
                                    @Value("${app.android-release.version-code:2}") int versionCode,
                                    @Value("${app.android-release.version-name:1.1}") String versionName,
                                    @Value("${app.android-release.minimum-version-code:0}") int minimumVersionCode) {
        if (versionCode < 1 || versionName.isBlank() || minimumVersionCode < 0 || minimumVersionCode > versionCode) {
            throw new IllegalArgumentException("Invalid Android release configuration");
        }
        this.release = new AndroidRelease(versionCode, versionName, minimumVersionCode);
        this.push = push;
    }

    @GetMapping("/api/site/android-release")
    public ResponseEntity<AndroidRelease> current() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(release);
    }

    @PostMapping("/api/admin/android-release/announce")
    public ResponseEntity<Void> announce() {
        try {
            int status = push.sendAndroidUpdate(release.versionCode());
            if (status == 503) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Firebase unavailable");
            if (status < 200 || status >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Firebase rejected the update announcement");
            }
            return ResponseEntity.noContent().build();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Update announcement interrupted", error);
        } catch (IOException | JOSEException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send update announcement", error);
        }
    }

    public record AndroidRelease(int versionCode, String versionName, int minimumVersionCode) {}
}
