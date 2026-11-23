package uz.osonmarket.catalog.customer.category;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<CategoryView> list(@RequestParam(required = false) UUID parentId, Locale locale) {
        return categories.list(parentId, locale.getLanguage());
    }

    @GetMapping("/{id}")
    public CategoryView get(@PathVariable UUID id, Locale locale) {
        return categories.get(id, locale.getLanguage());
    }

    @GetMapping(value = "/{id}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] image(@PathVariable UUID id) {
        return categories.image(id);
    }
}