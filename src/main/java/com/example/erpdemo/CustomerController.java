package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class CustomerController {

    @FXML private TableView<?> customerTable;
    @FXML private TableColumn<?, ?> idColumn;
    @FXML private TableColumn<?, ?> nameColumn;
    @FXML private TableColumn<?, ?> contactColumn;
    @FXML private TableColumn<?, ?> phoneColumn;
    @FXML private TableColumn<?, ?> emailColumn;

    @FXML
    public void initialize() {
        // Bu metod, FXML dosyası yüklendiğinde otomatik olarak çalışır.
        // Veritabanından müşteri verilerini buraya yükleyeceğiz.
        System.out.println("Müşteri ekranı başarıyla yüklendi.");
    }
}