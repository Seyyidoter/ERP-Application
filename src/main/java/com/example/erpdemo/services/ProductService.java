package com.example.erpdemo.services;

import com.example.erpdemo.model.Product;

import java.util.List;

public interface ProductService {
    List<Product> listAll() throws Exception;

    void delete(int productId) throws Exception;
}
