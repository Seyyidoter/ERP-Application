package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

public class RequestController {

    @FXML private TableView<Request> requestTable;
    @FXML private TableColumn<Request, Integer> idColumn;
    @FXML private TableColumn<Request, Integer> customerIdColumn;
    @FXML private TableColumn<Request, LocalDate> dateColumn;
    @FXML private TableColumn<Request, String> statusColumn;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        customerIdColumn.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        loadRequests();
    }

    private void loadRequests() {
        try {
            ObservableList<Request> requestList = RequestDAO.getAllRequests();
            requestTable.setItems(requestList);
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Hata", "Talep verileri yüklenirken bir hata oluştu.");
        }
    }

    @FXML
    private void handleAddRequest() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-request.fxml"));
            Parent parent = loader.load();

            NewRequestController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Talep Oluştur");
            stage.setScene(new Scene(parent));

            controller.setDialogStage(stage);

            stage.showAndWait();

            loadRequests();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Hata", "Yeni talep penceresi açılamadı.");
        }
    }

    @FXML
    private void handleViewRequest() {
        Request selectedRequest = requestTable.getSelectionModel().getSelectedItem();
        if (selectedRequest != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("view-request.fxml"));
                Parent parent = loader.load();

                ViewRequestController controller = loader.getController();

                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.setTitle("Talep Detayları");
                stage.setScene(new Scene(parent));

                controller.setDialogStage(stage);
                controller.setRequest(selectedRequest);

                stage.showAndWait();

            } catch (IOException e) {
                e.printStackTrace();
                showAlert("Hata", "Talep detay penceresi açılamadı.");
            }
        } else {
            showAlert("Uyarı", "Lütfen görüntülemek için bir talep seçin.");
        }
    }

    @FXML
    private void handleDeleteRequest() {
        Request selectedRequest = requestTable.getSelectionModel().getSelectedItem();
        if (selectedRequest != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Talebi silmek istediğinizden emin misiniz?", ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.showAndWait();

            if (confirm.getResult() == ButtonType.YES) {
                try {
                    RequestDAO.deleteRequest(selectedRequest.getId());
                    showAlert("Başarılı", "Talep başarıyla silindi.");
                    loadRequests();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showAlert("Hata", "Talep silinirken bir hata oluştu: " + e.getMessage());
                }
            }
        } else {
            showAlert("Uyarı", "Lütfen silmek için bir talep seçin.");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}