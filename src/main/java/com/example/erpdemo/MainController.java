package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainController {

    @FXML
    private StackPane contentPane;

    @FXML
    public void showCustomers() {
        // Müşteri İşlemleri ekranını yükleyecek metod
        loadContent("customer-view.fxml");
    }

    @FXML
    public void showStock() {
        // Stok İşlemleri ekranını yükleyecek metod
        // loadContent("stock-view.fxml");
    }

    @FXML
    public void showReports() {
        // Raporlar ekranını yükleyecek metod
        // loadContent("reports-view.fxml");
    }

    private void loadContent(String fxmlFile) {
        try {
            Parent content = FXMLLoader.load(getClass().getResource(fxmlFile));
            contentPane.getChildren().setAll(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}