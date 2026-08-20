package com.nyxn.products_api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.nyxn.products_api.dto.ProductRequest;
import com.nyxn.products_api.dto.ProductResponse;
import com.nyxn.products_api.exception.ResourceNotFoundException;
import com.nyxn.products_api.mapper.ProductMapper;
import com.nyxn.products_api.model.Product;
import com.nyxn.products_api.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product sampleProduct(Long id) {
        return Product.builder()
                .id(id)
                .name("Wireless Mouse")
                .description("Ergonomic mouse")
                .price(new BigDecimal("19.99"))
                .stock(150)
                .category("Electronics")
                .createdAt(Instant.parse("2026-08-19T00:00:00Z"))
                .build();
    }

    private ProductResponse sampleResponse(Long id) {
        return new ProductResponse(id, "Wireless Mouse", "Ergonomic mouse",
                new BigDecimal("19.99"), 150, "Electronics", Instant.parse("2026-08-19T00:00:00Z"));
    }

    @Test
    void findAll_shouldReturnMappedPageOfProducts() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Product product = sampleProduct(1L);
        ProductResponse response = sampleResponse(1L);
        given(productRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(product), pageable, 1));
        given(productMapper.toResponse(product)).willReturn(response);

        // Act
        var result = productService.findAll(pageable);

        // Assert
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(response);
        then(productRepository).should().findAll(pageable);
    }

    @Test
    void findById_whenProductExists_shouldReturnMappedProduct() {
        // Arrange
        Product product = sampleProduct(1L);
        ProductResponse response = sampleResponse(1L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(productMapper.toResponse(product)).willReturn(response);

        // Act
        ProductResponse result = productService.findById(1L);

        // Assert
        assertThat(result).isEqualTo(response);
    }

    @Test
    void findById_whenProductDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id: 99");
        then(productMapper).should(never()).toResponse(any());
    }

    @Test
    void create_shouldMapSaveAndReturnResponse() {
        // Arrange
        ProductRequest request = new ProductRequest("Widget", "A widget",
                new BigDecimal("9.99"), 100, "Tools");
        Product entityToSave = Product.builder().name("Widget").build();
        Product savedEntity = sampleProduct(9L);
        ProductResponse response = sampleResponse(9L);

        given(productMapper.toEntity(request)).willReturn(entityToSave);
        given(productRepository.save(entityToSave)).willReturn(savedEntity);
        given(productMapper.toResponse(savedEntity)).willReturn(response);

        // Act
        ProductResponse result = productService.create(request);

        // Assert
        assertThat(result).isEqualTo(response);
        then(productRepository).should().save(entityToSave);
    }

    @Test
    void update_whenProductExists_shouldUpdateAndReturnResponse() {
        // Arrange
        ProductRequest request = new ProductRequest("Wireless Mouse v2", "Updated",
                new BigDecimal("24.99"), 140, "Electronics");
        Product existingProduct = sampleProduct(1L);
        ProductResponse response = sampleResponse(1L);

        given(productRepository.findById(1L)).willReturn(Optional.of(existingProduct));
        given(productRepository.save(existingProduct)).willReturn(existingProduct);
        given(productMapper.toResponse(existingProduct)).willReturn(response);

        // Act
        ProductResponse result = productService.update(1L, request);

        // Assert
        assertThat(result).isEqualTo(response);
        then(productMapper).should().updateEntity(existingProduct, request);
        then(productRepository).should().save(existingProduct);
    }

    @Test
    void update_whenProductDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        ProductRequest request = new ProductRequest("X", "desc", new BigDecimal("1.00"), 1, "Y");
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id: 99");
        then(productRepository).should(never()).save(any());
    }

    @Test
    void delete_whenProductExists_shouldDeleteProduct() {
        // Arrange
        Product product = sampleProduct(1L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // Act
        productService.delete(1L);

        // Assert
        then(productRepository).should().delete(product);
    }

    @Test
    void delete_whenProductDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id: 99");
        then(productRepository).should(never()).delete(any());
    }
}
