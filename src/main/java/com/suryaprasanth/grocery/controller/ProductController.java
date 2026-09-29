package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<Product> list(@RequestParam(required = false) Long categoryId,
                               @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return productRepository.findByNameContainingIgnoreCaseOrderByIdAsc(search.trim());
        }
        if (categoryId != null) {
            return productRepository.findByCategoryIdOrderByIdAsc(categoryId);
        }
        return productRepository.findAll(Sort.by("id"));
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
