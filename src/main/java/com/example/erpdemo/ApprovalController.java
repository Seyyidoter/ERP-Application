package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.SQLException;
import java.time.LocalDate;

public class ApprovalController {

    @FXML private TableView<Request> pendingRequestsTable;
    @FXML private TableColumn<Request, Integer> idColumn;
    @FXML private TableColumn<Request, Integer> customerIdColumn;
    @FXML private TableColumn<Request, LocalDate> dateColumn;
    @FXML private TableColumn<Request, String> statusColumn;

    private int currentUserId = 1;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        customerIdColumn.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        refresh();
    }

    /** MainController.loadContent çağırdığında otomatik çalışır. */
    public void refresh() {
        try {
            ObservableList<Request> pending = RequestDAO.getPendingRequests();
            pendingRequestsTable.setItems(pending);
            pendingRequestsTable.refresh();
        } catch (SQLException e) {
            showAlert("Hata", "Onay bekleyen talepler yüklenirken bir hata oluştu.");
        }
    }

    @FXML
    private void handleApprove() {
        Request r = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (r == null) { showAlert("Uyarı", "Lütfen bir talep seçin."); return; }

        try {
            RequestDAO.updateRequestStatus(r.getId(), "Onaylandı", currentUserId);
            showAlert("Başarılı", "Talep onaylandı.");
            refresh();
        } catch (SQLException e) {
            showAlert("Hata", "Talep onaylanırken bir hata oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleReject() {
        Request r = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (r == null) { showAlert("Uyarı", "Lütfen bir talep seçin."); return; }

        try {
            RequestDAO.updateRequestStatus(r.getId(), "Reddedildi", currentUserId);
            showAlert("Başarılı", "Talep reddedildi.");
            refresh();
        } catch (SQLException e) {
            showAlert("Hata", "Talep reddedilirken hata: " + e.getMessage());
        }
    }

    private void showAlert(String t, String m) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, m);
        a.setTitle(t); a.setHeaderText(null); a.showAndWait();
    }
}
