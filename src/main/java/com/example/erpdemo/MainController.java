package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class MainController {

    @FXML private StackPane contentPane;
    @FXML private VBox menuPane;

    private User loggedInUser;

    public void setUser(User user) {
        this.loggedInUser = user;
        updateMenuVisibility();
    }

    private void updateMenuVisibility() {
        if (!"Yonetici".equals(loggedInUser.getRole())) {
            menuPane.getChildren().stream()
                    .filter(node -> node instanceof javafx.scene.control.Button && "Onay İşlemleri".equals(((javafx.scene.control.Button) node).getText()))
                    .findFirst()
                    .ifPresent(node -> node.setVisible(false));
        }
    }

    @FXML
    public void showCustomers() {
        loadContent("customer-view.fxml");
    }

    @FXML
    public void showStock() {
        loadContent("stock-view.fxml");
    }

    @FXML
    public void showRequests() {
        loadContent("request-view.fxml");
    }

    @FXML
    public void showApprovals() {
        if ("Yonetici".equals(loggedInUser.getRole())) {
            loadContent("approval-view.fxml");
        } else {
            // Yetkisiz erişim uyarısı
        }
    }

    @FXML
    public void showReports() {
        loadContent("reports-view.fxml");
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