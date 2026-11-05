package com.example.perflab.api;
import com.example.perflab.repository.LabRepository;
import com.example.perflab.service.LabWork;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @Validated
public class LabController {
    private final LabRepository repo;
    private final LabWork work;
    public LabController(LabRepository repo,LabWork work) {
        this.repo=repo;
        this.work=work;
    }
    @GetMapping("/api/products/{id}") public Map<String,Object> product(@PathVariable @Min(1) long id) {
        return repo.product(id);
    }
    @GetMapping("/api/products") public Map<String,Object> products(@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return Map.of("page",page,"size",size,"items",repo.products(page,size));
    }
    @GetMapping("/api/products/search") public List<Map<String,Object>> search(@RequestParam @NotBlank @Size(max=80) String keyword) {
        return repo.search(keyword);
    }
    public record OrderRequest(@Min(1) long productId,@Min(1) @Max(100) int quantity) {
    }
    @PostMapping("/api/orders") public ResponseEntity<Map<String,Object>> create(@RequestBody @Valid OrderRequest order) {
        var result=repo.createOrder(order.productId(),order.quantity());
        return ResponseEntity.created(java.net.URI.create("/api/orders/"+result.get("id"))).body(result);
    }
    @GetMapping("/api/orders/{id}") public Map<String,Object> order(@PathVariable @Min(1) long id) {
        return repo.order(id);
    }
    @GetMapping("/api/lab/runtime") public Object runtime() {
        return Map.of("productCount",repo.productCount(),"javaVersion",System.getProperty("java.version"),"jvmArguments",java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments(),"collectors",java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().map(java.lang.management.GarbageCollectorMXBean::getName).toList(),"maxHeapBytes",Runtime.getRuntime().maxMemory());
    }
    @GetMapping("/api/lab/cpu") public Map<String,Object> cpu(@RequestParam(defaultValue="10000") @Min(1) @Max(5000000) int iterations) {
        return work.cpu(iterations);
    }
    @GetMapping("/api/lab/alloc") public Map<String,Object> alloc(@RequestParam(defaultValue="64") @Min(1) @Max(4096) int kb) {
        return work.alloc(kb);
    }
    @GetMapping("/api/lab/slow-query") public Object slow(@RequestParam(defaultValue="indexed") String mode) {
        return repo.slow(mode);
    }
}
