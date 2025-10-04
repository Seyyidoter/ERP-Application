package com.example.erpdemo.services;

import com.example.erpdemo.dao.ProductDAO;
import com.example.erpdemo.model.Product;

import java.util.List;

public class ProductServiceImpl implements ProductService {

    @Override
    public List<Product> listAll() throws Exception {
        return ProductDAO.getAllProducts();
    }

    @Override
    public void delete(int productId) throws Exception {
        ProductDAO.deleteProduct(productId);
    }
}
