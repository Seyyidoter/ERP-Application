// ApprovalController.java'nın tam hali
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

        loadPendingRequests();
    }

    private void loadPendingRequests() {
        try {
            ObservableList<Request> pendingRequests = RequestDAO.getPendingRequests();
            pendingRequestsTable.setItems(pendingRequests);
        } catch (SQLException e) {
            showAlert("Hata", "Onay bekleyen talepler yüklenirken bir hata oluştu.");
        }
    }

    @FXML
    private void handleApprove() {
        Request selectedRequest = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (selectedRequest != null) {
            try {
                ObservableList<RequestItem> items = RequestDAO.getRequestItemsByRequestId(selectedRequest.getId());

                // Yeterli stok kontrolü
                for (RequestItem item : items) {
                    Product product = ProductDAO.getProductById(item.getProductId());
                    if (product.getStok() < item.getQuantity()) {
                        showAlert("Uyarı", "Yeterli stok bulunmuyor: " + product.getUrunAdi() + " için " + item.getQuantity() + " adet talep edildi, stokta " + product.getStok() + " adet var.");
                        return; // Yetersiz stok varsa işlemi durdur
                    }
                }

                // Stok kontrolü başarılıysa, stoğu güncelle ve onayla
                for (RequestItem item : items) {
                    ProductDAO.updateProductStock(item.getProductId(), -item.getQuantity());
                }

                RequestDAO.updateRequestStatus(selectedRequest.getId(), "Onaylandı", currentUserId);

                showAlert("Başarılı", "Talep başarıyla onaylandı ve stok güncellendi.");
                loadPendingRequests();
            } catch (SQLException e) {
                showAlert("Hata", "Talep onaylanırken bir hata oluştu: " + e.getMessage());
            }
        } else {
            showAlert("Uyarı", "Lütfen bir talep seçin.");
        }
    }

    @FXML
    private void handleReject() {
        Request selectedRequest = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (selectedRequest != null) {
            try {
                RequestDAO.updateRequestStatus(selectedRequest.getId(), "Reddedildi", currentUserId);
                showAlert("Başarılı", "Talep başarıyla reddedildi.");
                loadPendingRequests();
            } catch (SQLException e) {
                showAlert("Hata", "Talep reddedilirken bir hata oluştu: " + e.getMessage());
            }
        } else {
            showAlert("Uyarı", "Lütfen bir talep seçin.");
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