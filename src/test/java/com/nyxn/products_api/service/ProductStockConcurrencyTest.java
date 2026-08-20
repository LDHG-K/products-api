package com.nyxn.products_api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nyxn.products_api.dto.ProductRequest;
import com.nyxn.products_api.dto.ProductResponse;
import com.nyxn.products_api.exception.InsufficientStockException;
import com.nyxn.products_api.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProductStockConcurrencyTest {

    private static final int STARTING_STOCK = 1;
    private static final int CONCURRENT_BUYERS = 8;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void purchaseStock_underConcurrentDemand_neverOversells() throws InterruptedException {
        // Arrange
        Long productId = createProductWithStock(STARTING_STOCK).id();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_BUYERS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_BUYERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_BUYERS);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficientStockFailures = new AtomicInteger();

        // Act: release every buyer at the same instant so they collide on the same row
        for (int i = 0; i < CONCURRENT_BUYERS; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    productService.purchaseStock(productId, 1);
                    successes.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    insufficientStockFailures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        readyLatch.await();
        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertThat(finishedInTime).isTrue();
        assertThat(successes.get()).isEqualTo(STARTING_STOCK);
        assertThat(insufficientStockFailures.get()).isEqualTo(CONCURRENT_BUYERS - STARTING_STOCK);
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(0);
    }

    private ProductResponse createProductWithStock(int stock) {
        return productService.create(new ProductRequest(
                "Cyber-Day Last Unit", "Stress-test item for concurrency",
                new BigDecimal("199.99"), stock, "Electronics"));
    }
}
