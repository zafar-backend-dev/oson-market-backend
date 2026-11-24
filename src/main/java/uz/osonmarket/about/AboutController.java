package uz.osonmarket.about;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AboutController {
    private final AboutService about;

    public AboutController(AboutService about) {
        this.about = about;
    }

    @GetMapping("/api/site/about")
    public AboutAdminController.View get() {
        return about.get();
    }
}
