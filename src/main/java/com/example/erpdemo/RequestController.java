package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

public class RequestController {

    @FXML private TableView<Request> tblRequests;
    @FXML private TableColumn<Request, Integer> colId;
    @FXML private TableColumn<Request, Integer> colCustomer;
    @FXML private TableColumn<Request, LocalDate> colDate;
    @FXML private TableColumn<Request, String>  colStatus;

    private final ObservableList<Request> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> c.getValue().idProperty().asObject());
        colCustomer.setCellValueFactory(c -> c.getValue().customerIdProperty().asObject());
        colDate.setCellValueFactory(c -> c.getValue().requestDateProperty());
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());

        tblRequests.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tblRequests.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.DELETE) deleteSingleRequest(); });

        refresh();
    }

    public void refresh() {
        data.setAll(RequestDAO.findAll());
        tblRequests.setItems(data);
        tblRequests.refresh();
    }

    @FXML
    private void createRequest() {
        try {
            var url = RequestController.class.getResource("/com/example/erpdemo/new-request.fxml");
            if (url == null) throw new IllegalStateException("new-request.fxml bulunamadı (classpath).");

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();

            NewRequestController controller = loader.getController();

            Stage dlg = new Stage();
            dlg.setTitle("Yeni Talep");
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.initOwner(tblRequests.getScene().getWindow());
            dlg.setScene(new Scene(root));
            controller.setDialogStage(dlg);

            dlg.showAndWait();
            refresh();

        } catch (IOException | RuntimeException ex) {
            showError("Yeni talep penceresi açılamadı:\n" + ex.getMessage());
        }
    }

    @FXML
    private void viewRequest() {
        Request selected = tblRequests.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Lütfen bir satır seçin."); return; }

        try {
            // DETAY PENCERESİ -> view-request.fxml
            var url = RequestController.class.getResource("/com/example/erpdemo/view-request.fxml");
            if (url == null) throw new IllegalStateException("view-request.fxml bulunamadı (classpath).");

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();

            ViewRequestController controller = loader.getController();
            controller.setRequest(selected);

            Stage dlg = new Stage();
            dlg.setTitle("Talep Detayı");
            dlg.initOwner(tblRequests.getScene().getWindow());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.setScene(new Scene(root));
            controller.setDialogStage(dlg);

            dlg.showAndWait();

        } catch (IOException | RuntimeException ex) {
            showError("Talep detayı açılamadı:\n" + ex.getMessage());
        }
    }

    @FXML
    private void deleteSingleRequest() {
        Request selected = tblRequests.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Lütfen bir satır seçin."); return; }

        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Seçili talep silinecek. Emin misiniz?", ButtonType.CANCEL, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    RequestDAO.deleteRequestById(selected.getId());
                    refresh();
                    showInfo("Talep silindi.");
                } catch (SQLException e) {
                    showError("Silme sırasında hata: " + e.getMessage());
                }
            }
        });
    }

    private void showInfo(String msg) {
        Alert x = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        x.setHeaderText(null);
        x.show();
    }
    private void showError(String msg) {
        Alert x = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        x.setHeaderText(null);
        x.show();
    }
}
