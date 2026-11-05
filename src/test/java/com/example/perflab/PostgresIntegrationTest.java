package com.example.perflab;
import com.example.perflab.generator.DataGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="PERFLAB_INTEGRATION", matches="true")
class PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Test void generatesAppendsReproducesAndServesOrders() throws Exception {
        // 显式启用才执行：会重置本机 perflab 数据。
        DataGenerator.main(new String[] {
            "--count=100","--seed=42","--truncate=true"
        }
        );
        assertEquals(100,jdbc.queryForObject("SELECT count(*) FROM products",Integer.class));
        var first=jdbc.queryForList("SELECT * FROM products ORDER BY id");
        DataGenerator.main(new String[] {
            "--count=1000"
        }
        );
        assertEquals(1100,jdbc.queryForObject("SELECT count(DISTINCT sku) FROM products",Integer.class));
        DataGenerator.main(new String[] {
            "--count=100","--seed=42","--truncate=true"
        }
        );
        assertEquals(first,jdbc.queryForList("SELECT * FROM products ORDER BY id"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/products/1")).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/products/search?keyword=Product-1")).andExpect(status().isOk());
        String body=mvc.perform(post("/api/orders").contentType("application/json").content("{\"productId\":1,\"quantity\":2}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();
        mvc.perform(get("/api/orders/"+id)).andExpect(jsonPath("$.quantity").value(2));
        mvc.perform(get("/api/lab/slow-query?mode=invalid")).andExpect(status().isBadRequest());
    }
}
