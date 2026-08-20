package com.nyxn.products_api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxn.products_api.dto.ProductRequest;
import com.nyxn.products_api.dto.ProductResponse;
import com.nyxn.products_api.exception.ResourceNotFoundException;
import com.nyxn.products_api.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProductService productService;

    private ProductResponse sampleResponse(Long id) {
        return new ProductResponse(id, "Wireless Mouse", "Ergonomic mouse",
                new BigDecimal("19.99"), 150, "Electronics", Instant.parse("2026-08-19T00:00:00Z"));
    }

    @Test
    void getAll_shouldReturnPagedProducts() throws Exception {
        // Arrange
        ProductResponse product = sampleResponse(1L);
        Pageable pageable = PageRequest.of(0, 10);
        given(productService.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(product), pageable, 1));

        // Act & Assert
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].name", is("Wireless Mouse")))
                .andExpect(jsonPath("$.totalElements", is(1)));

        then(productService).should().findAll(any(Pageable.class));
    }

    @Test
    void getById_whenProductExists_shouldReturnProduct() throws Exception {
        // Arrange
        given(productService.findById(1L)).willReturn(sampleResponse(1L));

        // Act & Assert
        mockMvc.perform(get("/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.category", is("Electronics")));

        then(productService).should().findById(1L);
    }

    @Test
    void getById_whenProductDoesNotExist_shouldReturn404() throws Exception {
        // Arrange
        given(productService.findById(99L))
                .willThrow(new ResourceNotFoundException("Product", 99L));

        // Act & Assert
        mockMvc.perform(get("/products/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Product not found with id: 99")));
    }

    @Test
    void create_withValidRequest_shouldReturn201WithLocationHeader() throws Exception {
        // Arrange
        ProductRequest request = new ProductRequest("Widget", "A widget",
                new BigDecimal("9.99"), 100, "Tools");
        ProductResponse created = new ProductResponse(9L, "Widget", "A widget",
                new BigDecimal("9.99"), 100, "Tools", Instant.parse("2026-08-19T00:00:00Z"));
        given(productService.create(any(ProductRequest.class))).willReturn(created);

        // Act & Assert
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/products/9")))
                .andExpect(jsonPath("$.id", is(9)))
                .andExpect(jsonPath("$.name", is("Widget")));

        then(productService).should().create(any(ProductRequest.class));
    }

    @Test
    void create_withInvalidRequest_shouldReturn400AndNotCallService() throws Exception {
        // Arrange
        ProductRequest invalidRequest = new ProductRequest("", null,
                new BigDecimal("-5"), -1, "");

        // Act & Assert
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.price").exists())
                .andExpect(jsonPath("$.validationErrors.stock").exists())
                .andExpect(jsonPath("$.validationErrors.category").exists());

        then(productService).should(never()).create(any(ProductRequest.class));
    }

    @Test
    void update_whenProductExists_shouldReturn200() throws Exception {
        // Arrange
        ProductRequest request = new ProductRequest("Wireless Mouse v2", "Updated",
                new BigDecimal("24.99"), 140, "Electronics");
        ProductResponse updated = new ProductResponse(1L, "Wireless Mouse v2", "Updated",
                new BigDecimal("24.99"), 140, "Electronics", Instant.parse("2026-08-19T00:00:00Z"));
        given(productService.update(eq(1L), any(ProductRequest.class))).willReturn(updated);

        // Act & Assert
        mockMvc.perform(put("/products/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Wireless Mouse v2")))
                .andExpect(jsonPath("$.stock", is(140)));
    }

    @Test
    void update_whenProductDoesNotExist_shouldReturn404() throws Exception {
        // Arrange
        ProductRequest request = new ProductRequest("X", "desc", new BigDecimal("1.00"), 1, "Y");
        given(productService.update(eq(99L), any(ProductRequest.class)))
                .willThrow(new ResourceNotFoundException("Product", 99L));

        // Act & Assert
        mockMvc.perform(put("/products/{id}", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Product not found with id: 99")));
    }

    @Test
    void delete_whenProductExists_shouldReturn204() throws Exception {
        // Arrange (void method, no stubbing needed for the happy path)

        // Act & Assert
        mockMvc.perform(delete("/products/{id}", 1L))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(productService).should().delete(1L);
    }

    @Test
    void delete_whenProductDoesNotExist_shouldReturn404() throws Exception {
        // Arrange
        org.mockito.BDDMockito.willThrow(new ResourceNotFoundException("Product", 99L))
                .given(productService).delete(99L);

        // Act & Assert
        mockMvc.perform(delete("/products/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Product not found with id: 99")));
    }

    @Test
    void getById_withNonNumericId_shouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/products/{id}", "abc"))
                .andExpect(status().isBadRequest());

        then(productService).should(never()).findById(anyLong());
    }
}
