package com.example.ecomerce.config;

import com.example.ecomerce.models.AttributeDefinition;
import com.example.ecomerce.models.AttributeType;
import com.example.ecomerce.models.Category;
import com.example.ecomerce.models.CategoryAttribute;
import com.example.ecomerce.models.Product;
import com.example.ecomerce.models.ProductAttributeValue;
import com.example.ecomerce.repository.AttributeDefinitionRepository;
import com.example.ecomerce.repository.CategoryAttributeRepository;
import com.example.ecomerce.repository.CategoryRepository;
import com.example.ecomerce.repository.ProductAttributeValueRepository;
import com.example.ecomerce.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.db.seed-electronics", havingValue = "true")
public class ElectronicsSeedRunner implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AttributeDefinitionRepository attributeRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final ProductAttributeValueRepository productAttributeValueRepository;

    public ElectronicsSeedRunner(
        JdbcTemplate jdbc,
        ProductRepository productRepository,
        CategoryRepository categoryRepository,
        AttributeDefinitionRepository attributeRepository,
        CategoryAttributeRepository categoryAttributeRepository,
        ProductAttributeValueRepository productAttributeValueRepository
    ) {
        this.jdbc = jdbc;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.attributeRepository = attributeRepository;
        this.categoryAttributeRepository = categoryAttributeRepository;
        this.productAttributeValueRepository = productAttributeValueRepository;
    }

    @Override
    public void run(String... args) {
        wipeAllAppData();
        seedElectronicsCatalog();
    }

    private void execIgnore(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception ignored) {
        }
    }

    private void wipeAllAppData() {
        execIgnore("SET FOREIGN_KEY_CHECKS = 0");

        execIgnore("DELETE FROM product_attribute_values");
        execIgnore("DELETE FROM category_attributes");
        execIgnore("DELETE FROM attribute_definitions");
        execIgnore("DELETE FROM products");
        execIgnore("DELETE FROM categories");

        execIgnore("SET FOREIGN_KEY_CHECKS = 1");

        execIgnore("ALTER TABLE product_attribute_values AUTO_INCREMENT = 1");
        execIgnore("ALTER TABLE category_attributes AUTO_INCREMENT = 1");
        execIgnore("ALTER TABLE attribute_definitions AUTO_INCREMENT = 1");
        execIgnore("ALTER TABLE products AUTO_INCREMENT = 1");
        execIgnore("ALTER TABLE categories AUTO_INCREMENT = 1");
    }

    private void seedElectronicsCatalog() {
        // 1) Categories (hierarchical)
        Category pc = categoryRepository.save(new Category(null, "PC Components", null));
        Category phone = categoryRepository.save(new Category(null, "Phone Accessories", null));

        Category cpu = categoryRepository.save(new Category(null, "CPU", pc));
        Category mainboard = categoryRepository.save(new Category(null, "Mainboard", pc));
        Category gpu = categoryRepository.save(new Category(null, "GPU", pc));
        Category ram = categoryRepository.save(new Category(null, "RAM", pc));
        Category ramDesktop = categoryRepository.save(new Category(null, "Desktop RAM", ram));
        Category ramLaptop = categoryRepository.save(new Category(null, "Laptop RAM", ram));
        Category ssd = categoryRepository.save(new Category(null, "SSD", pc));
        Category ssdNvme = categoryRepository.save(new Category(null, "NVMe SSD", ssd));
        Category ssdSata = categoryRepository.save(new Category(null, "SATA SSD", ssd));
        Category psu = categoryRepository.save(new Category(null, "PSU", pc));

        Category phoneCase = categoryRepository.save(new Category(null, "Cases", phone));
        Category charger = categoryRepository.save(new Category(null, "Chargers", phone));
        Category cable = categoryRepository.save(new Category(null, "Cables", phone));
        Category screenProtector = categoryRepository.save(new Category(null, "Screen Protectors", phone));
        Category powerBank = categoryRepository.save(new Category(null, "Power Banks", phone));
        Category earphones = categoryRepository.save(new Category(null, "Earphones", phone));

        // 2) Attribute definitions (Dynamic Attributes)
        var attrs = attributeRepository.saveAll(List.of(
            new AttributeDefinition(null, "BRAND", "Brand", AttributeType.STRING, null),
            new AttributeDefinition(null, "MODEL", "Model", AttributeType.STRING, null),
            new AttributeDefinition(null, "WARRANTY_MONTHS", "Warranty (months)", AttributeType.NUMBER, "months"),
            new AttributeDefinition(null, "BARCODE", "Barcode", AttributeType.STRING, null),

            new AttributeDefinition(null, "RAM_CAPACITY_GB", "RAM capacity", AttributeType.NUMBER, "GB"),
            new AttributeDefinition(null, "RAM_TYPE", "RAM type", AttributeType.STRING, null),
            new AttributeDefinition(null, "RAM_BUS_MHZ", "RAM bus", AttributeType.NUMBER, "MHz"),
            new AttributeDefinition(null, "RAM_FORM_FACTOR", "Form factor", AttributeType.STRING, null),

            new AttributeDefinition(null, "SSD_CAPACITY_GB", "SSD capacity", AttributeType.NUMBER, "GB"),
            new AttributeDefinition(null, "SSD_INTERFACE", "SSD interface", AttributeType.STRING, null),
            new AttributeDefinition(null, "SSD_FORM_FACTOR", "SSD form factor", AttributeType.STRING, null),
            new AttributeDefinition(null, "SSD_READ_MB_S", "Read speed", AttributeType.NUMBER, "MB/s"),
            new AttributeDefinition(null, "SSD_WRITE_MB_S", "Write speed", AttributeType.NUMBER, "MB/s"),

            new AttributeDefinition(null, "CPU_SOCKET", "CPU socket", AttributeType.STRING, null),
            new AttributeDefinition(null, "CPU_CORES", "CPU cores", AttributeType.NUMBER, null),
            new AttributeDefinition(null, "CPU_THREADS", "CPU threads", AttributeType.NUMBER, null),
            new AttributeDefinition(null, "CPU_BASE_GHZ", "Base clock", AttributeType.NUMBER, "GHz"),
            new AttributeDefinition(null, "CPU_BOOST_GHZ", "Boost clock", AttributeType.NUMBER, "GHz"),

            new AttributeDefinition(null, "GPU_VRAM_GB", "VRAM", AttributeType.NUMBER, "GB"),
            new AttributeDefinition(null, "GPU_MEMORY_TYPE", "VRAM type", AttributeType.STRING, null),

            new AttributeDefinition(null, "CHARGER_WATT", "Charger power", AttributeType.NUMBER, "W"),
            new AttributeDefinition(null, "CABLE_LENGTH_M", "Cable length", AttributeType.NUMBER, "m"),
            new AttributeDefinition(null, "POWERBANK_CAPACITY_MAH", "Power bank capacity", AttributeType.NUMBER, "mAh"),
            new AttributeDefinition(null, "PHONE_MODEL", "Phone model", AttributeType.STRING, null)
        ));

        Map<String, AttributeDefinition> byCode = attrs.stream()
            .collect(java.util.stream.Collectors.toMap(AttributeDefinition::getCode, a -> a));

        // 3) Category -> attributes mapping
        categoryAttributeRepository.saveAll(List.of(
            new CategoryAttribute(null, cpu, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, cpu, byCode.get("MODEL"), true, 2),
            new CategoryAttribute(null, cpu, byCode.get("CPU_SOCKET"), true, 3),
            new CategoryAttribute(null, cpu, byCode.get("CPU_CORES"), true, 4),
            new CategoryAttribute(null, cpu, byCode.get("CPU_THREADS"), true, 5),
            new CategoryAttribute(null, cpu, byCode.get("CPU_BASE_GHZ"), false, 6),
            new CategoryAttribute(null, cpu, byCode.get("CPU_BOOST_GHZ"), false, 7),

            new CategoryAttribute(null, ramDesktop, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, ramDesktop, byCode.get("RAM_CAPACITY_GB"), true, 2),
            new CategoryAttribute(null, ramDesktop, byCode.get("RAM_TYPE"), true, 3),
            new CategoryAttribute(null, ramDesktop, byCode.get("RAM_BUS_MHZ"), true, 4),
            new CategoryAttribute(null, ramDesktop, byCode.get("RAM_FORM_FACTOR"), true, 5),

            new CategoryAttribute(null, ramLaptop, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, ramLaptop, byCode.get("RAM_CAPACITY_GB"), true, 2),
            new CategoryAttribute(null, ramLaptop, byCode.get("RAM_TYPE"), true, 3),
            new CategoryAttribute(null, ramLaptop, byCode.get("RAM_BUS_MHZ"), true, 4),
            new CategoryAttribute(null, ramLaptop, byCode.get("RAM_FORM_FACTOR"), true, 5),

            new CategoryAttribute(null, ssdNvme, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, ssdNvme, byCode.get("SSD_CAPACITY_GB"), true, 2),
            new CategoryAttribute(null, ssdNvme, byCode.get("SSD_INTERFACE"), true, 3),
            new CategoryAttribute(null, ssdNvme, byCode.get("SSD_FORM_FACTOR"), true, 4),
            new CategoryAttribute(null, ssdNvme, byCode.get("SSD_READ_MB_S"), false, 5),
            new CategoryAttribute(null, ssdNvme, byCode.get("SSD_WRITE_MB_S"), false, 6),

            new CategoryAttribute(null, ssdSata, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, ssdSata, byCode.get("SSD_CAPACITY_GB"), true, 2),
            new CategoryAttribute(null, ssdSata, byCode.get("SSD_INTERFACE"), true, 3),
            new CategoryAttribute(null, ssdSata, byCode.get("SSD_FORM_FACTOR"), true, 4),

            new CategoryAttribute(null, gpu, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, gpu, byCode.get("MODEL"), true, 2),
            new CategoryAttribute(null, gpu, byCode.get("GPU_VRAM_GB"), true, 3),
            new CategoryAttribute(null, gpu, byCode.get("GPU_MEMORY_TYPE"), false, 4),

            new CategoryAttribute(null, charger, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, charger, byCode.get("CHARGER_WATT"), true, 2),

            new CategoryAttribute(null, cable, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, cable, byCode.get("CABLE_LENGTH_M"), true, 2),

            new CategoryAttribute(null, powerBank, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, powerBank, byCode.get("POWERBANK_CAPACITY_MAH"), true, 2),

            new CategoryAttribute(null, phoneCase, byCode.get("BRAND"), true, 1),
            new CategoryAttribute(null, phoneCase, byCode.get("PHONE_MODEL"), true, 2)
        ));

        // 4) Products
        Product p1 = new Product();
        p1.setName("Kingston Fury Beast DDR4 16GB 3200MHz (1x16GB)");
        p1.setPrice(850_000.0);
        p1.setDescription("RAM desktop DDR4, bus 3200MHz, form factor DIMM.\nPhù hợp PC văn phòng / gaming phổ thông.");
        p1.setSku("RAM-KINGSTON-FURY-DDR4-16-3200");
        p1.setBarcode("8930000000001");
        p1.setWarrantyMonths(36);
        p1.setCategory(ramDesktop);
        p1 = productRepository.save(p1);

        Product p2 = new Product();
        p2.setName("Corsair Vengeance DDR5 32GB 6000MHz (2x16GB)");
        p2.setPrice(2_790_000.0);
        p2.setDescription("RAM desktop DDR5, bus 6000MHz, kit 2x16GB, phù hợp cấu hình Intel/AMD đời mới.");
        p2.setSku("RAM-CORSAIR-VENGEANCE-DDR5-32-6000");
        p2.setBarcode("8930000000002");
        p2.setWarrantyMonths(36);
        p2.setCategory(ramDesktop);
        p2 = productRepository.save(p2);

        Product p3 = new Product();
        p3.setName("Samsung 970 EVO Plus 1TB NVMe M.2");
        p3.setPrice(1_890_000.0);
        p3.setDescription("SSD NVMe M.2 2280, tốc độ đọc/ghi cao, phù hợp nâng cấp laptop/PC.");
        p3.setSku("SSD-SAMSUNG-970EVO-PLUS-1TB");
        p3.setBarcode("8930000000003");
        p3.setWarrantyMonths(60);
        p3.setCategory(ssdNvme);
        p3 = productRepository.save(p3);

        Product p4 = new Product();
        p4.setName("Crucial MX500 500GB SATA 2.5\"");
        p4.setPrice(990_000.0);
        p4.setDescription("SSD SATA 2.5\" bền bỉ, phù hợp máy đời cũ cần tăng tốc khởi động.");
        p4.setSku("SSD-CRUCIAL-MX500-500GB");
        p4.setBarcode("8930000000004");
        p4.setWarrantyMonths(60);
        p4.setCategory(ssdSata);
        p4 = productRepository.save(p4);

        Product p5 = new Product();
        p5.setName("AMD Ryzen 5 5600 (6C/12T) Socket AM4");
        p5.setPrice(2_890_000.0);
        p5.setDescription("CPU AMD Ryzen 5 5600, 6 nhân 12 luồng, hiệu năng/giá tốt cho gaming 1080p.");
        p5.setSku("CPU-AMD-R5-5600");
        p5.setBarcode("8930000000005");
        p5.setWarrantyMonths(36);
        p5.setCategory(cpu);
        p5 = productRepository.save(p5);

        Product p6 = new Product();
        p6.setName("Intel Core i5-12400F (6C/12T) LGA1700");
        p6.setPrice(3_590_000.0);
        p6.setDescription("CPU Intel i5-12400F, 6 nhân 12 luồng, phù hợp build PC gaming/đồ họa cơ bản.");
        p6.setSku("CPU-INTEL-I5-12400F");
        p6.setBarcode("8930000000006");
        p6.setWarrantyMonths(36);
        p6.setCategory(cpu);
        p6 = productRepository.save(p6);

        Product p7 = new Product();
        p7.setName("NVIDIA GeForce RTX 4060 8GB");
        p7.setPrice(8_990_000.0);
        p7.setDescription("GPU RTX 4060 8GB, tối ưu cho gaming 1080p/1440p, hỗ trợ DLSS.");
        p7.setSku("GPU-RTX-4060-8GB");
        p7.setBarcode("8930000000007");
        p7.setWarrantyMonths(36);
        p7.setCategory(gpu);
        p7 = productRepository.save(p7);

        Product p8 = new Product();
        p8.setName("MSI B550M PRO-VDH WIFI (AM4)");
        p8.setPrice(2_350_000.0);
        p8.setDescription("Mainboard B550M hỗ trợ Ryzen AM4, có WIFI, phù hợp build PC gọn nhẹ.");
        p8.setSku("MB-MSI-B550M-PRO-VDH-WIFI");
        p8.setBarcode("8930000000008");
        p8.setWarrantyMonths(36);
        p8.setCategory(mainboard);
        p8 = productRepository.save(p8);

        Product p9 = new Product();
        p9.setName("Cooler Master MWE 650 Bronze V2");
        p9.setPrice(1_390_000.0);
        p9.setDescription("Nguồn 650W chuẩn 80 Plus Bronze, phù hợp cấu hình tầm trung.");
        p9.setSku("PSU-CM-MWE-650-BRONZE-V2");
        p9.setBarcode("8930000000009");
        p9.setWarrantyMonths(60);
        p9.setCategory(psu);
        p9 = productRepository.save(p9);

        Product p10 = new Product();
        p10.setName("Anker 20W USB-C Charger (PD)");
        p10.setPrice(390_000.0);
        p10.setDescription("Sạc nhanh 20W chuẩn Power Delivery, phù hợp iPhone/iPad và Android hỗ trợ PD.");
        p10.setSku("CHARGER-ANKER-20W-PD");
        p10.setBarcode("8930000000010");
        p10.setWarrantyMonths(18);
        p10.setCategory(charger);
        p10 = productRepository.save(p10);

        Product p11 = new Product();
        p11.setName("UGREEN USB-C to USB-C 100W Cable 2m");
        p11.setPrice(220_000.0);
        p11.setDescription("Cáp USB-C to USB-C hỗ trợ sạc nhanh tới 100W, dài 2m.");
        p11.setSku("CABLE-UGREEN-CC-100W-2M");
        p11.setBarcode("8930000000011");
        p11.setWarrantyMonths(12);
        p11.setCategory(cable);
        p11 = productRepository.save(p11);

        Product p12 = new Product();
        p12.setName("Baseus Power Bank 20000mAh 65W");
        p12.setPrice(1_190_000.0);
        p12.setDescription("Pin dự phòng 20000mAh, công suất tối đa 65W, có thể sạc laptop/điện thoại.");
        p12.setSku("PB-BASEUS-20000-65W");
        p12.setBarcode("8930000000012");
        p12.setWarrantyMonths(12);
        p12.setCategory(powerBank);
        p12 = productRepository.save(p12);

        Product p13 = new Product();
        p13.setName("Spigen Rugged Armor Case - iPhone 15 Pro");
        p13.setPrice(450_000.0);
        p13.setDescription("Ốp lưng chống sốc, thiết kế mỏng nhẹ, bề mặt chống bám vân tay.");
        p13.setSku("CASE-SPIGEN-RUGGED-IPH15PRO");
        p13.setBarcode("8930000000013");
        p13.setWarrantyMonths(6);
        p13.setCategory(phoneCase);
        p13 = productRepository.save(p13);

        Product p14 = new Product();
        p14.setName("Tempered Glass Screen Protector - Samsung S24");
        p14.setPrice(120_000.0);
        p14.setDescription("Kính cường lực trong suốt, chống xước, dễ dán.");
        p14.setSku("SP-GLASS-SAMSUNG-S24");
        p14.setBarcode("8930000000014");
        p14.setWarrantyMonths(1);
        p14.setCategory(screenProtector);
        p14 = productRepository.save(p14);

        Product p15 = new Product();
        p15.setName("Xiaomi In-Ear Earphones (Type-C)");
        p15.setPrice(160_000.0);
        p15.setDescription("Tai nghe in-ear cổng Type-C, phù hợp nhiều dòng Android.");
        p15.setSku("EAR-XIAOMI-IN-EAR-TYPEC");
        p15.setBarcode("8930000000015");
        p15.setWarrantyMonths(6);
        p15.setCategory(earphones);
        p15 = productRepository.save(p15);

        // 5) Product attribute values
        productAttributeValueRepository.saveAll(List.of(
            // p1 RAM DDR4 16GB 3200
            pav(p1, byCode.get("BRAND"), "Kingston", null, null),
            pav(p1, byCode.get("RAM_CAPACITY_GB"), null, 16.0, null),
            pav(p1, byCode.get("RAM_TYPE"), "DDR4", null, null),
            pav(p1, byCode.get("RAM_BUS_MHZ"), null, 3200.0, null),
            pav(p1, byCode.get("RAM_FORM_FACTOR"), "DIMM", null, null),
            pav(p1, byCode.get("WARRANTY_MONTHS"), null, 36.0, null),
            pav(p1, byCode.get("BARCODE"), "8930000000001", null, null),

            // p3 SSD NVMe 1TB
            pav(p3, byCode.get("BRAND"), "Samsung", null, null),
            pav(p3, byCode.get("SSD_CAPACITY_GB"), null, 1024.0, null),
            pav(p3, byCode.get("SSD_INTERFACE"), "NVMe PCIe", null, null),
            pav(p3, byCode.get("SSD_FORM_FACTOR"), "M.2 2280", null, null),
            pav(p3, byCode.get("SSD_READ_MB_S"), null, 3500.0, null),
            pav(p3, byCode.get("SSD_WRITE_MB_S"), null, 3300.0, null),
            pav(p3, byCode.get("WARRANTY_MONTHS"), null, 60.0, null),
            pav(p3, byCode.get("BARCODE"), "8930000000003", null, null),

            // p5 CPU Ryzen 5 5600
            pav(p5, byCode.get("BRAND"), "AMD", null, null),
            pav(p5, byCode.get("MODEL"), "Ryzen 5 5600", null, null),
            pav(p5, byCode.get("CPU_SOCKET"), "AM4", null, null),
            pav(p5, byCode.get("CPU_CORES"), null, 6.0, null),
            pav(p5, byCode.get("CPU_THREADS"), null, 12.0, null),
            pav(p5, byCode.get("CPU_BASE_GHZ"), null, 3.5, null),
            pav(p5, byCode.get("CPU_BOOST_GHZ"), null, 4.4, null),
            pav(p5, byCode.get("WARRANTY_MONTHS"), null, 36.0, null),

            // p7 GPU RTX 4060
            pav(p7, byCode.get("BRAND"), "NVIDIA", null, null),
            pav(p7, byCode.get("MODEL"), "RTX 4060", null, null),
            pav(p7, byCode.get("GPU_VRAM_GB"), null, 8.0, null),
            pav(p7, byCode.get("GPU_MEMORY_TYPE"), "GDDR6", null, null),
            pav(p7, byCode.get("WARRANTY_MONTHS"), null, 36.0, null),

            // p10 charger
            pav(p10, byCode.get("BRAND"), "Anker", null, null),
            pav(p10, byCode.get("CHARGER_WATT"), null, 20.0, null),
            pav(p10, byCode.get("WARRANTY_MONTHS"), null, 18.0, null),

            // p11 cable
            pav(p11, byCode.get("BRAND"), "UGREEN", null, null),
            pav(p11, byCode.get("CABLE_LENGTH_M"), null, 2.0, null),

            // p12 powerbank
            pav(p12, byCode.get("BRAND"), "Baseus", null, null),
            pav(p12, byCode.get("POWERBANK_CAPACITY_MAH"), null, 20000.0, null),

            // p13 phone case
            pav(p13, byCode.get("BRAND"), "Spigen", null, null),
            pav(p13, byCode.get("PHONE_MODEL"), "iPhone 15 Pro", null, null)
        ));
    }

    private static ProductAttributeValue pav(
        Product product,
        AttributeDefinition attribute,
        String text,
        Double number,
        Boolean bool
    ) {
        return new ProductAttributeValue(null, product, attribute, text, number, bool);
    }
}
