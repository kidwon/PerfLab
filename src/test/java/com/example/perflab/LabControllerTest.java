package com.example.perflab;
import com.example.perflab.api.LabController;
import com.example.perflab.repository.LabRepository;
import com.example.perflab.service.LabWork;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(LabController.class)
class LabControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean LabRepository repo;
    @MockitoBean LabWork work;
    @Test void readsProductAndMapsMissingRecord() throws Exception {
        when(repo.product(1)).thenReturn(Map.of("id",1,"sku","LAB-1"));
        mvc.perform(get("/api/products/1")).andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("LAB-1"));
        when(repo.product(2)).thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        mvc.perform(get("/api/products/2")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
    @Test void rejectsUnsafeInputsBeforeWork() throws Exception {
        for(String path:new String[] {
            "/api/lab/cpu?iterations=5000001","/api/lab/alloc?kb=4097","/api/products?size=101","/api/products/-1","/api/products/search?keyword="
        }
        ) mvc.perform(get(path)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").contentType("application/json").content("{\"productId\":1,\"quantity\":0}")).andExpect(status().isBadRequest());
        verifyNoInteractions(repo,work);
    }
    @Test void createsOrderWithLocation() throws Exception {
        when(repo.createOrder(1,2)).thenReturn(Map.of("id",10,"product_id",1,"quantity",2));
        mvc.perform(post("/api/orders").contentType("application/json").content("{\"productId\":1,\"quantity\":2}")).andExpect(status().isCreated()).andExpect(header().string("Location","/api/orders/10")).andExpect(jsonPath("$.quantity").value(2));
    }
}
