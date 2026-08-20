package com.nyxn.products_api.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requestedQuantity) {
        super("Not enough stock for product %d to fulfill a purchase of %d unit(s)"
                .formatted(productId, requestedQuantity));
    }
}
