package com.nyxn.products_api.service;

import com.nyxn.products_api.dto.ProductRequest;
import com.nyxn.products_api.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    Page<ProductResponse> findAll(Pageable pageable);

    ProductResponse findById(Long id);

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);

    void purchaseStock(Long id, int quantity);
}
