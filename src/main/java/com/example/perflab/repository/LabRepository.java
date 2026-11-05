package com.example.perflab.repository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class LabRepository {
    private final JdbcTemplate jdbc;
    public LabRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public long productCount() {
        return jdbc.queryForObject("SELECT count(*) FROM products",Long.class);
    }
    public Map<String,Object> product(long id) {
        return jdbc.queryForMap("SELECT * FROM products WHERE id=?", id);
    }
    public List<Map<String,Object>> products(int page,int size) {
        return jdbc.queryForList("SELECT * FROM products ORDER BY id LIMIT ? OFFSET ?",size,(long)page*size);
    }
    public List<Map<String,Object>> search(String keyword) {
        return jdbc.queryForList("SELECT * FROM products WHERE name LIKE ? ESCAPE '!' ORDER BY name LIMIT 100", keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
    }
    public Map<String,Object> order(long id) {
        return jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
    }
    public Map<String,Object> createOrder(long productId,int quantity) {
        return jdbc.queryForMap("INSERT INTO orders(product_id,quantity) VALUES (?,?) RETURNING *",productId,quantity);
    }
    public List<Map<String,Object>> slow(String mode) {
        return switch(mode) {
            case "indexed" -> jdbc.queryForList("SELECT id,name FROM products WHERE name LIKE 'Product-1%' LIMIT 100");
            case "scan" -> jdbc.queryForList("SELECT id,name FROM products WHERE lower(name) LIKE '%product-1%' LIMIT 100");
            case "sleep" -> {
                jdbc.execute("SELECT pg_sleep(0.1)");
                yield List.of(Map.of("delayMs",100));
            }
            default -> throw new IllegalArgumentException("mode 必须为 indexed、scan 或 sleep");
        };
    }
}
