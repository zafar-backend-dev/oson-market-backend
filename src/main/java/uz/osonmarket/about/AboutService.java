package uz.osonmarket.about;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.about.AboutAdminController.Input;
import uz.osonmarket.about.AboutAdminController.Translation;
import uz.osonmarket.about.AboutAdminController.View;
import uz.osonmarket.auth.persistence.AuthLocks;

@Service
class AboutService {
    private final EntityManager entities;
    private final AuthLocks locks;

    AboutService(EntityManager entities, AuthLocks locks) {
        this.entities = entities;
        this.locks = locks;
    }

    @Transactional(readOnly = true)
    public View get() {
        AboutPage page = entities.find(AboutPage.class, AboutPage.ID);
        return page == null ? new View(null, null, null, Map.of()) : view(page);
    }

    @Transactional
    public View save(Input input) {
        locks.lock("site-about");
        AboutPage current = entities.find(AboutPage.class, AboutPage.ID);
        AboutPage page = current == null ? new AboutPage() : current;
        page.titleUz = input.uz().title().strip();
        page.descriptionUz = input.uz().description().strip();
        page.titleRu = input.ru().title().strip();
        page.descriptionRu = input.ru().description().strip();
        if (input.socialLinks() != null) {
            page.socialLinks.clear();
            page.socialLinks.putAll(input.socialLinks());
        }
        page.updatedAt = Instant.now();
        if (current == null) {
            entities.persist(page);
        }
        return view(page);
    }

    private View view(AboutPage page) {
        return new View(new Translation(page.titleUz, page.descriptionUz),
                new Translation(page.titleRu, page.descriptionRu), page.updatedAt, Map.copyOf(page.socialLinks));
    }
}
