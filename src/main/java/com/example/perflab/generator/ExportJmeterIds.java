package com.example.perflab.generator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;

/** Export only existing product IDs to a small JMeter input file. */
public class ExportJmeterIds {
    public static void main(String[] args) throws Exception {
        int limit = args.length == 0 ? 1000 : Integer.parseInt(args[0]);
        if (args.length > 1 || limit < 1 || limit > 100000) {
            throw new IllegalArgumentException("limit 必须为 1..100000");
        }
        Path output = Path.of("build/jmeter-data/product-ids.csv").toAbsolutePath();
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), "product-ids-", ".tmp");
        int exported = 0;
        try {
            try (var connection = DriverManager.getConnection(
                    "jdbc:postgresql://127.0.0.1:5432/perflab", "perflab",
                    System.getenv().getOrDefault("DB_PASSWORD", ""));
                 var statement = connection.prepareStatement("SELECT id FROM products ORDER BY id LIMIT ?")) {
                statement.setInt(1, limit);
                statement.setQueryTimeout(10);
                try (var rows = statement.executeQuery();
                     var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    writer.write("productId,quantity\n");
                    while (rows.next()) {
                        writer.write(rows.getLong(1) + ",1\n");
                        exported++;
                    }
                }
            }
            if (exported == 0) {
                throw new IllegalStateException("商品表为空，请先运行 generateData；未覆盖已有 CSV");
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            System.out.printf("已导出 %d 个真实商品 ID：%s%n", exported, output);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
