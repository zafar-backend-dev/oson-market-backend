//package uz.osonmarket.catalog.seed;
//
//import java.awt.image.BufferedImage;
//import java.io.*;
//import java.math.BigDecimal;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Path;
//import java.util.*;
//import javax.imageio.ImageIO;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.web.multipart.MultipartFile;
//import uz.osonmarket.auth.persistence.AuthLocks;
//import uz.osonmarket.catalog.admin.category.*;
//import uz.osonmarket.catalog.admin.product.*;
//import uz.osonmarket.catalog.persistence.CategoryRepository;
//import uz.osonmarket.catalog.persistence.ProductRepository;
//import uz.osonmarket.catalog.persistence.ProductTypeRepository;
//import uz.osonmarket.catalog.persistence.ProductTypeImageRepository;
//import uz.osonmarket.catalog.media.CatalogImages;
//import uz.osonmarket.profile.AvatarImages.Image;
//
//@Component
//@ConditionalOnProperty(name = "app.catalog.seed-demo", havingValue = "true")
//public class DemoCatalogImport implements ApplicationRunner {
//    private static final Logger log = LoggerFactory.getLogger(DemoCatalogImport.class);
//    private final CategoryRepository categories;
//    private final CategoryAdminService categoryAdmin;
//    private final ProductRepository products;
//    private final ProductFormService productForms;
//    private final AuthLocks locks;
//    private final ProductTypeRepository variants;
//    private final ProductTypeImageRepository productImages;
//    private final CatalogImages storage;
//
//    public DemoCatalogImport(CategoryRepository categories, CategoryAdminService categoryAdmin,
//                             ProductRepository products, ProductFormService productForms, AuthLocks locks,
//                             ProductTypeRepository variants, ProductTypeImageRepository productImages, CatalogImages storage) {
//        this.categories = categories;
//        this.categoryAdmin = categoryAdmin;
//        this.products = products;
//        this.productForms = productForms;
//        this.locks = locks;
//        this.variants = variants;
//        this.productImages = productImages;
//        this.storage = storage;
//    }
//
//    @Override
//    @Transactional(rollbackFor = IOException.class)
//    public void run(ApplicationArguments args) throws IOException {
//        locks.lock("catalog:demo-import");
//        var legacy = readSheet(new ClassPathResource("catalog-demo/products.png"));
//        var sheets = new HashMap<Integer, BufferedImage>();
//        int created = 0;
//        int upgraded = 0;
//        var resource = new ClassPathResource("catalog-demo/products.txt");
//        try (var lines = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
//            for (String line; (line = lines.readLine()) != null;) {
//                String[] row = line.split("\\|");
//                int group = Integer.parseInt(row[0]);
//                int position = Integer.parseInt(row[1]);
//                String slug = "demo-" + group + "-" + position;
//                if (!sheets.containsKey(group)) {
//                    var resourcePath = new ClassPathResource("catalog-demo/group-" + group + ".png");
//                    sheets.put(group, resourcePath.exists() ? readSheet(resourcePath) : null);
//                }
//                var sheet = sheets.get(group);
//                BufferedImage primary = sheet == null ? picture(legacy, group - 1, position - 1, 6, 6)
//                        : picture(sheet, (position - 1) / 3, (position - 1) % 3, 3, 4);
//                BufferedImage alternate = sheet == null ? legacyDetail(primary)
//                        : picture(sheet, 2 + (position - 1) / 3, (position - 1) % 3, 3, 4);
//                var existing = products.findBySlug(slug);
//                if (existing.isPresent()) {
//                    if (sheet != null) upgraded += upgrade(existing.get().getId(), slug, legacy, group, position, primary, alternate);
//                    continue;
//                }
//                UUID category = category(group, row[2], row[3]);
//                save(slug, category, row[4], row[5], new BigDecimal(row[6]), primary, alternate);
//                created++;
//            }
//        }
//        log.info("Demo catalog import added {} products and upgraded {} galleries", created, upgraded);
//    }
//
//    private UUID category(int group, String uz, String ru) {
//        String slug = "demo-katalog-" + group;
//        return categories.findBySlug(slug).map(category -> category.getId()).orElseGet(() ->
//                categoryAdmin.saveCategory(null, new CategoryInput(slug, null, null, 100 + group, true,
//                        new Translation(uz, null, null, null), new Translation(ru, null, null, null))).id());
//    }
//
//    private int upgrade(UUID productId, String slug, BufferedImage legacy, int group, int position,
//                        BufferedImage primary, BufferedImage alternate) throws IOException {
//        locks.lock("catalog:product:" + productId);
//        var variant = variants.findByProductIdOrderByIdAsc(productId).stream()
//                .filter(item -> slug.toUpperCase(Locale.ROOT).equals(item.getSku())).findFirst();
//        if (variant.isEmpty()) return 0;
//        var current = productImages.findByProductTypeIdOrderBySortOrderAscIdAsc(variant.get().getId());
//        if (current.size() != 2 || current.stream().anyMatch(item -> item.getMedia() == null)) return 0;
//        var original = picture(legacy, group - 1, position - 1, 6, 6);
//        var detail = legacyDetail(original);
//        if (!Objects.equals(current.get(0).getMedia().getWidth(), original.getWidth())
//                || !Objects.equals(current.get(0).getMedia().getHeight(), original.getHeight())) return 0;
//        if (!samePixels(original, storage.readMedia(current.get(0).getMedia()))
//                || !samePixels(detail, storage.readMedia(current.get(1).getMedia()))) return 0;
//        current.get(0).setMedia(storage.store(new Image(png(primary), primary.getWidth(), primary.getHeight()), "product"));
//        current.get(1).setMedia(storage.store(new Image(png(alternate), alternate.getWidth(), alternate.getHeight()), "product"));
//        productImages.flush();
//        // Product media can be reused across galleries, so retain the original media records.
//        return 1;
//    }
//
//    private static boolean samePixels(BufferedImage expected, byte[] bytes) throws IOException {
//        var actual = ImageIO.read(new ByteArrayInputStream(bytes));
//        int w = expected.getWidth();
//        int h = expected.getHeight();
//        return actual != null && actual.getWidth() == w && actual.getHeight() == h
//                && Arrays.equals(expected.getRGB(0, 0, w, h, null, 0, w), actual.getRGB(0, 0, w, h, null, 0, w));
//    }
//
//    private static BufferedImage legacyDetail(BufferedImage original) {
//        int inset = Math.min(original.getWidth(), original.getHeight()) / 8;
//        return original.getSubimage(inset, inset, original.getWidth() - 2 * inset, original.getHeight() - 2 * inset);
//    }
//
//    private void save(String slug, UUID category, String uz, String ru, BigDecimal price,
//                      BufferedImage primary, BufferedImage alternate) throws IOException {
//        var product = new ProductInput(slug, null, Set.of(category), "ACTIVE", null, null,
//                new ProductInput.Text(uz, null, null, null, null), new ProductInput.Text(ru, null, null, null, null));
//        var files = new LinkedMultiValueMap<String, MultipartFile>();
//        files.add("file_full", new PictureFile("file_full", png(primary)));
//        files.add("file_detail", new PictureFile("file_detail", png(alternate)));
//        var pictures = List.of(new ProductForm.Picture(null, "file_full", 0, true),
//                new ProductForm.Picture(null, "file_detail", 1, false));
//        var variant = new ProductForm.Variant(null, slug.toUpperCase(Locale.ROOT), null, "ACTIVE", null,
//                null, null, null, true, price, null, pictures);
//        productForms.save(null, new ProductForm(product, List.of(variant)), files);
//    }
//
//    private static BufferedImage readSheet(ClassPathResource resource) throws IOException {
//        try (var input = resource.getInputStream()) {
//            var image = ImageIO.read(input);
//            if (image == null || image.getWidth() < 600 || image.getHeight() < 600) {
//                throw new IOException("Invalid demo product image sheet");
//            }
//            return image;
//        }
//    }
//
//    private static BufferedImage picture(BufferedImage sheet, int row, int column, int columns, int rows) {
//        int width = sheet.getWidth() / columns;
//        int height = sheet.getHeight() / rows;
//        return sheet.getSubimage(column * width + 2, row * height + 2, width - 4, height - 4);
//    }
//
//    private static byte[] png(BufferedImage image) throws IOException {
//        var output = new ByteArrayOutputStream();
//        ImageIO.write(image, "PNG", output);
//        return output.toByteArray();
//    }
//
//    private record PictureFile(String getName, byte[] getBytes) implements MultipartFile {
//        public String getOriginalFilename() { return getName + ".png"; }
//        public String getContentType() { return "image/png"; }
//        public boolean isEmpty() { return getBytes.length == 0; }
//        public long getSize() { return getBytes.length; }
//        public InputStream getInputStream() { return new ByteArrayInputStream(getBytes); }
//        public void transferTo(File destination) throws IOException { transferTo(destination.toPath()); }
//        public void transferTo(Path destination) throws IOException { java.nio.file.Files.write(destination, getBytes); }
//    }
//}
