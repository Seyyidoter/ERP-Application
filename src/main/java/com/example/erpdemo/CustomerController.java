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
import java.text.NumberFormat;
import java.util.Locale;

/** Müşteri listesi + CRUD + Ödeme alma + Geçmiş + Filtreleme */
public class CustomerController {

    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Integer>    idColumn;
    @FXML private TableColumn<Customer, String>     nameColumn;
    @FXML private TableColumn<Customer, String>     contactColumn;
    @FXML private TableColumn<Customer, String>     phoneColumn;
    @FXML private TableColumn<Customer, String>     emailColumn;
    @FXML private TableColumn<Customer, Integer>    iskontoColumn;
    @FXML private TableColumn<Customer, BigDecimal> balanceColumn;

    @FXML private TextField searchField;

    // Seçime bağlı butonlar
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button takePaymentButton; // Ödeme Al
    @FXML private Button historyButton;     // Geçmiş

    private final ObservableList<Customer> master = FXCollections.observableArrayList();
    private FilteredList<Customer> filtered;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("companyName"));
        contactColumn.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        iskontoColumn.setCellValueFactory(new PropertyValueFactory<>("iskonto"));
        balanceColumn.setCellValueFactory(new PropertyValueFactory<>("balance"));

        // hizalama ve para formatı
        iskontoColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        balanceColumn.setCellFactory(col -> new TableCell<>() {
            final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("tr","TR"));
            { nf.setMinimumFractionDigits(2); nf.setMaximumFractionDigits(2); }
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); }
                else { setText(nf.format(v)); setStyle("-fx-alignment: CENTER-RIGHT;"); }
            }
        });

        filtered = new FilteredList<>(master, x -> true);
        SortedList<Customer> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(customerTable.comparatorProperty());
        customerTable.setItems(sorted);

        searchField.textProperty().addListener((obs, old, q) -> applyFilter(q));

        // Seçim yokken aksiyon butonlarını pasifleştir
        var noSelection = customerTable.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        deleteButton.disableProperty().bind(noSelection);
        takePaymentButton.disableProperty().bind(noSelection);
        historyButton.disableProperty().bind(noSelection);

        loadCustomers();
    }

    private void applyFilter(String query) {
        final String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) { filtered.setPredicate(x -> true); return; }

        filtered.setPredicate(c -> {
            if (contains(c.getCompanyName(), q)) return true;
            if (contains(c.getContactPerson(), q)) return true;
            if (contains(c.getPhone(), q)) return true;
            if (contains(c.getEmail(), q)) return true;
            if (String.valueOf(c.getIskonto()).contains(q)) return true;

            BigDecimal bal = c.getBalance() == null ? BigDecimal.ZERO : c.getBalance();
            if (bal.toPlainString().toLowerCase(Locale.ROOT).contains(q)) return true;
            String bal2 = String.format(Locale.ROOT, "%.2f", bal);
            return bal2.contains(q);
        });
    }

    private boolean contains(String val, String q) {
        return val != null && val.toLowerCase(Locale.ROOT).contains(q);
    }

    private void loadCustomers() {
        try {
            master.setAll(CustomerDAO.getAllCustomers());
        } catch (SQLException e) {
            AppDialogs.dbError("Müşteri verileri yüklenmesi", e);
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
            AppDialogs.unexpectedError("Yeni müşteri penceresi açma", e);
        }
    }

    @FXML
    private void handleEditButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer == null) return; // buton zaten disabled

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
            AppDialogs.unexpectedError("Müşteri düzenleme penceresi açma", e);
        }
    }

    @FXML
    private void handleDeleteButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer == null) return; // buton zaten disabled

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Müşteriyi silmek istediğinizden emin misiniz?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Onay");
        IconUtil.decorateAlert(confirm);
        confirm.showAndWait();

        if (confirm.getResult() == ButtonType.YES) {
            try {
                CustomerDAO.deleteCustomer(selectedCustomer.getId());
                AppDialogs.info("Müşteri başarıyla silindi.");
                loadCustomers();
            } catch (SQLException e) {
                String generic = "Bu müşteri ilişkili kayıtlar nedeniyle silinemedi.";
                AppDialogs.dbError(generic, e);
            }
        }
    }

    // ---------- ÖDEME AL ----------
    @FXML
    private void handleTakePayment() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) return; // buton zaten disabled

        TextInputDialog td = new TextInputDialog();
        td.setTitle("Ödeme Al – " + sel.getCompanyName());
        td.setHeaderText(null);
        td.setContentText("Tutar (TL):");
        IconUtil.decorateDialog(td);
        var res = td.showAndWait();
        if (res.isEmpty()) return;

        java.math.BigDecimal amountBD;
        try {
            String txt = res.get().replace(",", ".").trim();
            amountBD = new java.math.BigDecimal(txt);
            if (amountBD.signum() <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            AppDialogs.warn("Geçerli bir tutar girin (0'dan büyük).");
            return;
        }

        TextInputDialog note = new TextInputDialog();
        note.setTitle("Ödeme Açıklaması");
        note.setHeaderText(null);
        note.setContentText("Açıklama (opsiyonel):");
        IconUtil.decorateDialog(note);
        String desc = note.showAndWait().orElse("");

        try {
            PaymentDAO.addPayment(sel.getId(), amountBD, desc);
            AppDialogs.info("Ödeme kaydedildi.");
            loadCustomers();
        } catch (SQLException e) {
            AppDialogs.dbError("Ödeme kaydı", e);
        }
    }

    // ---------- GEÇMİŞ ----------
    @FXML
    private void handleCustomerHistory() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) return; // buton zaten disabled
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
            AppDialogs.unexpectedError("Geçmiş penceresi açma", ex);
        }
    }
}
