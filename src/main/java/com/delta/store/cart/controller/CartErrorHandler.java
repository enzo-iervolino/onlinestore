package com.delta.store.cart.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestControllerAdvice(assignableTypes=CartController.class)
public class CartErrorHandler {
    @ExceptionHandler({IllegalArgumentException.class,ArithmeticException.class})
    public ResponseEntity<String> invalid(RuntimeException e) { return ResponseEntity.badRequest().body(e instanceof IllegalArgumentException?e.getMessage():"Quantità non valida"); }
}
