package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.sql.SQLException;
import java.time.LocalDate;

public class ApprovalController {

    @FXML private TableView<Request> pendingRequestsTable;
    @FXML private TableColumn<Request, Integer> idColumn;
    @FXML private TableColumn<Request, Integer> customerIdColumn;
    @FXML private TableColumn<Request, LocalDate> dateColumn;
    @FXML private TableColumn<Request, String> statusColumn;

    @FXML private Button approveBtn;
    @FXML private Button rejectBtn;

    private int currentUserId = 0;

    /** MainController’dan aktarılacak */
    public void setCurrentUserId(int id) {
        this.currentUserId = id;
    }

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        customerIdColumn.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Seçim yoksa butonlar kapalı
        approveBtn.setDisable(true);
        rejectBtn.setDisable(true);
        pendingRequestsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            boolean hasSel = n != null;
            approveBtn.setDisable(!hasSel);
            rejectBtn.setDisable(!hasSel);
        });

        // İstersen tarih formatlayıcı
        dateColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : d.toString()); // dd.MM.yyyy şeklinde istersen formatla
            }
        });

        refresh();
    }

    /** MainController.loadContent çağırdığında otomatik çalışır. */
    public void refresh() {
        try {
            ObservableList<Request> pending = RequestDAO.getPendingRequests();
            pendingRequestsTable.setItems(pending);
            pendingRequestsTable.refresh();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Hata", "Onay bekleyen talepler yüklenirken bir hata oluştu.");
        }
    }

    @FXML
    private void handleApprove() {
        Request r = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (r == null) { showAlert(Alert.AlertType.WARNING,"Uyarı","Lütfen bir talep seçin."); return; }
        if (currentUserId <= 0) { showAlert(Alert.AlertType.ERROR,"Hata","Kullanıcı bilgisi alınamadı."); return; }

        try {
            RequestDAO.approveRequest(r.getId(), currentUserId);
            showAlert(Alert.AlertType.INFORMATION,"Başarılı","Talep onaylandı.");
            refresh();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR,"Hata","Talep onaylanırken bir hata oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleReject() {
        Request r = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (r == null) { showAlert(Alert.AlertType.WARNING,"Uyarı","Lütfen bir talep seçin."); return; }
        if (currentUserId <= 0) { showAlert(Alert.AlertType.ERROR,"Hata","Kullanıcı bilgisi alınamadı."); return; }

        try {
            RequestDAO.rejectRequest(r.getId(), currentUserId);
            showAlert(Alert.AlertType.INFORMATION,"Başarılı","Talep reddedildi.");
            refresh();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR,"Hata","Talep reddedilirken hata: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String t, String m) {
        Alert a = new Alert(type, m, ButtonType.OK);
        a.setTitle(t); a.setHeaderText(null); a.showAndWait();
    }
}
