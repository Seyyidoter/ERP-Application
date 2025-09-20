package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Locale;

/** Müşteri listesi + CRUD + Ödeme alma + Geçmiş + Filtreleme */
public class CustomerController {

    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Integer> idColumn;
    @FXML private TableColumn<Customer, String>  nameColumn;
    @FXML private TableColumn<Customer, String>  contactColumn;
    @FXML private TableColumn<Customer, String>  phoneColumn;
    @FXML private TableColumn<Customer, String>  emailColumn;
    @FXML private TableColumn<Customer, Integer> iskontoColumn;

    // BAKIYE artık BigDecimal:
    @FXML private TableColumn<Customer, BigDecimal>  balanceColumn;

    @FXML private TextField searchField;

    private final ObservableList<Customer> master = FXCollections.observableArrayList();
    private FilteredList<Customer> filtered;

    @FXML
    public void initialize() {
        // Kolon bağları
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("companyName"));
        contactColumn.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        iskontoColumn.setCellValueFactory(new PropertyValueFactory<>("iskonto"));

        // BigDecimal balance:
        balanceColumn.setCellValueFactory(new PropertyValueFactory<>("balance"));

        // UI: sayısal hizalama/format
        iskontoColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        balanceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format(Locale.forLanguageTag("tr-TR"), "%.2f", v));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });

        // Filtre yapısı
        filtered = new FilteredList<>(master, x -> true);
        SortedList<Customer> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(customerTable.comparatorProperty());
        customerTable.setItems(sorted);

        // Canlı arama
        if (searchField != null) {
            searchField.textProperty().addListener((obs, old, q) -> applyFilter(q));
        }

        loadCustomers();
    }

    private void applyFilter(String query) {
        final String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) { filtered.setPredicate(x -> true); return; }

        filtered.setPredicate(c -> {
            // Metin alanlarında arama
            if (contains(c.getCompanyName(), q)) return true;
            if (contains(c.getContactPerson(), q)) return true;
            if (contains(c.getPhone(), q)) return true;
            if (contains(c.getEmail(), q)) return true;

            // Sayısalları da basitçe string karşılaştır
            if (String.valueOf(c.getIskonto()).contains(q)) return true;

            // BigDecimal'ı stringe çevir – her iki formu da dene
            BigDecimal bal = c.getBalance() == null ? BigDecimal.ZERO : c.getBalance();
            if (bal.toPlainString().toLowerCase(Locale.ROOT).contains(q)) return true;
            String bal2 = String.format(Locale.ROOT, "%.2f", bal);
            if (bal2.contains(q)) return true;

            return false;
        });
    }

    private boolean contains(String val, String q) {
        return val != null && val.toLowerCase(Locale.ROOT).contains(q);
    }

    private void loadCustomers() {
        try {
            master.setAll(CustomerDAO.getAllCustomers());
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Hata", "Müşteri verileri yüklenirken bir hata oluştu.");
        }
    }

    @FXML private void handleClearSearch() { searchField.clear(); }

    // ---------- CRUD ----------
    @FXML
    private void handleAddButton() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-customer.fxml"));
            Parent parent = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Müşteri Ekle");
            stage.setScene(new Scene(parent));
            IconUtil.setAppIcon(stage);
            stage.showAndWait();

            loadCustomers();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Hata", "Yeni müşteri penceresi açılamıyor.");
        }
    }

    @FXML
    private void handleEditButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("edit-customer.fxml"));
                Parent parent = loader.load();

                EditCustomerController controller = loader.getController();

                Stage dialogStage = new Stage();
                dialogStage.setTitle("Müşteri Düzenle");
                dialogStage.initModality(Modality.APPLICATION_MODAL);
                dialogStage.setScene(new Scene(parent));
                IconUtil.setAppIcon(dialogStage);

                controller.setDialogStage(dialogStage);
                controller.setCustomer(selectedCustomer);

                dialogStage.showAndWait();
                loadCustomers();
            } catch (IOException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Hata", "Müşteri düzenleme penceresi açılamadı.");
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Lütfen düzenlemek için bir müşteri seçin.");
        }
    }

    @FXML
    private void handleDeleteButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer == null) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Lütfen silmek için bir müşteri seçin.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Müşteriyi silmek istediğinizden emin misiniz?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Onay");
        IconUtil.decorateAlert(confirm);
        confirm.showAndWait();

        if (confirm.getResult() == ButtonType.YES) {
            try {
                CustomerDAO.deleteCustomer(selectedCustomer.getId());
                showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Müşteri başarıyla silindi.");
                loadCustomers();
            } catch (SQLException e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Hata", "Müşteri silinirken bir hata oluştu: " + e.getMessage());
            }
        }
    }

    // ---------- ÖDEME AL ----------
    @FXML
    private void handleTakePayment() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Lütfen ödeme almak için bir müşteri seçin.");
            return;
        }

        TextInputDialog td = new TextInputDialog();
        td.setTitle("Ödeme Al – " + sel.getCompanyName());
        td.setHeaderText(null);
        td.setContentText("Tutar (TL):");
        IconUtil.decorateDialog(td);
        var res = td.showAndWait();
        if (res.isEmpty()) return;

        // Kullanıcıdan gelen değeri BigDecimal'a çevir
        java.math.BigDecimal amountBD;
        try {
            String txt = res.get().replace(",", ".").trim();
            amountBD = new java.math.BigDecimal(txt);
            if (amountBD.signum() <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Geçerli bir tutar girin (0'dan büyük).");
            return;
        }

        TextInputDialog note = new TextInputDialog();
        note.setTitle("Ödeme Açıklaması");
        note.setHeaderText(null);
        note.setContentText("Açıklama (opsiyonel):");
        IconUtil.decorateDialog(note);
        String desc = note.showAndWait().orElse("");

        try {
            // PaymentDAO zaten BigDecimal ile çalışıyor
            PaymentDAO.addPayment(sel.getId(), amountBD, desc);
            showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Ödeme kaydedildi.");
            loadCustomers();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Hata", "Ödeme kaydedilemedi: " + e.getMessage());
        }
    }

    // ---------- GEÇMİŞ ----------
    @FXML
    private void handleCustomerHistory() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Lütfen geçmişini görmek istediğiniz müşteriyi seçin.");
            return;
        }
        try {
            var url = getClass().getResource("customer-history-view.fxml");
            FXMLLoader loader = new FXMLLoader(url);
            Parent view = loader.load();

            CustomerHistoryController controller = loader.getController();

            Stage dlg = new Stage();
            dlg.setTitle("Müşteri Geçmişi – " + sel.getCompanyName());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.initOwner(customerTable.getScene().getWindow());
            dlg.setScene(new Scene(view));
            IconUtil.setAppIcon(dlg);

            controller.setDialogStage(dlg);
            controller.setCustomer(sel);

            dlg.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Hata", "Geçmiş penceresi açılamadı:\n" + ex.getMessage());
        }
    }

    // ---------- yardımcı ----------
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        IconUtil.decorateAlert(alert);
        alert.showAndWait();
    }
}
