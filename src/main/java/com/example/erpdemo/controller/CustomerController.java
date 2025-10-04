package com.example.erpdemo.controller;

import com.example.erpdemo.model.Customer;
import com.example.erpdemo.services.CustomerService;
import com.example.erpdemo.services.CustomerServiceImpl;
import com.example.erpdemo.util.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Locale;

/** Müşteri listesi + CRUD + Ödeme alma + Geçmiş + Filtreleme (ASYNC yükleme) */
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

    // 🔧 Alt buton çubuğu (FXML'de fx:id="actionsBar")
    @FXML private HBox actionsBar;

    private final ObservableList<Customer> master = FXCollections.observableArrayList();
    private FilteredList<Customer> filtered;

    // Service
    private final CustomerService customerService = new CustomerServiceImpl();

    @FXML
    public void initialize() {
        // sütun–model bağları
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("companyName"));
        contactColumn.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        iskontoColumn.setCellValueFactory(new PropertyValueFactory<>("iskonto"));
        balanceColumn.setCellValueFactory(new PropertyValueFactory<>("balance"));

        // hizalama ve para biçimlendirme
        iskontoColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        balanceColumn.setCellFactory(MoneyCells.twoDecimalsTR());

        // boş tablo mesajı
        customerTable.setPlaceholder(new Label("Kayıtlı müşteri yok"));

        // filtreleme + sıralama hattı
        filtered = new FilteredList<>(master, x -> true);
        var sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(customerTable.comparatorProperty());
        customerTable.setItems(sorted);

        // arama kutusu
        searchField.textProperty().addListener((obs, old, q) -> applyFilter(q));

        // seçim yokken aksiyon butonlarını pasifleştir
        var noSelection = customerTable.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        deleteButton.disableProperty().bind(noSelection);
        takePaymentButton.disableProperty().bind(noSelection);
        historyButton.disableProperty().bind(noSelection);

        // TABLO İÇİNDE: boş alana tıklanınca seçimi/odağı temizle
        customerTable.setRowFactory(tv -> {
            TableRow<Customer> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) {
                    customerTable.getSelectionModel().clearSelection();
                    if (customerTable.getParent() != null) customerTable.getParent().requestFocus();
                }
            });
            return row;
        });

        // SAHNE GENELİ: tablo DA değilse VE actionsBar DA değilse → seçimi temizle
        javafx.application.Platform.runLater(() -> {
            Scene scene = customerTable.getScene();
            if (scene == null) return;
            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                Node n = e.getPickResult().getIntersectedNode();
                boolean insideTable   = isChildOf(n, customerTable);
                boolean insideActions = isChildOf(n, actionsBar);
                if (!insideTable && !insideActions) {
                    customerTable.getSelectionModel().clearSelection();
                    if (customerTable.getParent() != null) customerTable.getParent().requestFocus();
                }
            });
        });

        // veri yükle (ASYNC)
        loadCustomers();
    }

    /** n düğümü root’un altındaysa true. */
    private static boolean isChildOf(Node n, Node root) {
        if (n == null || root == null) return false;
        while (n != null) {
            if (n == root) return true;
            n = n.getParent();
        }
        return false;
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

    /** Müşterileri arka planda yükler; UI donmaz. */
    private void loadCustomers() {
        setBusy(true);
        Async.run(
                () -> {
                    try { return customerService.listAll(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                },
                list -> master.setAll(list),
                ex -> AppDialogs.unexpectedError("Müşteri verileri yüklenmesi", ex),
                ()  -> setBusy(false)
        );
    }

    private void setBusy(boolean busy) {
        if (customerTable != null) customerTable.setDisable(busy);
        if (searchField != null)   searchField.setDisable(busy);
        if (actionsBar != null)    actionsBar.setDisable(busy);
    }

    @FXML private void handleClearSearch() { searchField.clear(); }

    // ---------- CRUD ----------
    @FXML
    private void handleAddButton() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/new-customer.fxml"));
            Parent parent = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Müşteri Ekle");
            stage.setScene(new Scene(parent));
            IconUtil.setAppIcon(stage);
            stage.showAndWait();

            loadCustomers(); // async
        } catch (IOException e) {
            AppDialogs.unexpectedError("Yeni müşteri penceresi açma", e);
        }
    }

    @FXML
    private void handleEditButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer == null) return; // buton zaten disabled

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/edit-customer.fxml"));
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
            loadCustomers(); // async
        } catch (IOException e) {
            AppDialogs.unexpectedError("Müşteri düzenleme penceresi açma", e);
        }
    }

    @FXML
    private void handleDeleteButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer == null) return; // buton zaten disabled

        // TR butonlu onay
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText(null);
        confirm.setTitle("Onay");
        confirm.setContentText("Müşteriyi silmek istediğinizden emin misiniz?");
        ButtonType evet = new ButtonType("Evet", ButtonBar.ButtonData.YES);
        ButtonType hayir = new ButtonType("Hayır", ButtonBar.ButtonData.NO);
        confirm.getButtonTypes().setAll(evet, hayir);
        IconUtil.decorateAlert(confirm);
        confirm.showAndWait();

        if (confirm.getResult() == evet) {
            setBusy(true);
            Async.runVoid(
                    () -> {
                        try { customerService.delete(selectedCustomer.getId()); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    },
                    () -> { AppDialogs.info("Müşteri başarıyla silindi."); loadCustomers(); },
                    ex  -> AppDialogs.unexpectedError("Müşteri silme", ex),
                    ()  -> setBusy(false)
            );
        }
    }

    // ---------- ÖDEME AL ----------
    @FXML
    private void handleTakePayment() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) return; // buton zaten disabled

        // Tutar girişi – TR butonlar
        TextInputDialog td = new TextInputDialog();
        td.setTitle("Ödeme Al – " + sel.getCompanyName());
        td.setHeaderText(null);
        td.setContentText("Tutar (TL):");
        ButtonType tamam = new ButtonType("Tamam", ButtonBar.ButtonData.OK_DONE);
        ButtonType iptal = new ButtonType("İptal",  ButtonBar.ButtonData.CANCEL_CLOSE);
        td.getDialogPane().getButtonTypes().setAll(tamam, iptal);
        IconUtil.decorateDialog(td);
        var res = td.showAndWait();
        if (res.isEmpty()) return;

        BigDecimal amountBD;
        try {
            amountBD = Money.parseTR(res.get().trim());
            if (amountBD.signum() <= 0) throw new IllegalArgumentException();
        } catch (Exception ex) {
            AppDialogs.warn("Geçerli bir tutar girin (0'dan büyük, örn: 1.234,56).");
            return;
        }

        // Açıklama – TR butonlar
        TextInputDialog note = new TextInputDialog();
        note.setTitle("Ödeme Açıklaması");
        note.setHeaderText(null);
        note.setContentText("Açıklama (opsiyonel):");
        note.getDialogPane().getButtonTypes().setAll(
                new ButtonType("Tamam", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("İptal",  ButtonBar.ButtonData.CANCEL_CLOSE)
        );
        IconUtil.decorateDialog(note);
        String desc = note.showAndWait().orElse("");

        setBusy(true);
        Async.runVoid(
                () -> {
                    try { customerService.addPayment(sel.getId(), amountBD, desc); }
                    catch (Exception e) { throw new RuntimeException(e); }
                },
                () -> { AppDialogs.info("Ödeme kaydedildi."); loadCustomers(); },
                ex  -> AppDialogs.unexpectedError("Ödeme kaydı", ex),
                ()  -> setBusy(false)
        );
    }

    // ---------- GEÇMİŞ ----------
    @FXML
    private void handleCustomerHistory() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) return; // buton zaten disabled
        try {
            var url = getClass().getResource("/fxml/customer-history-view.fxml");
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

    private static SQLException toSql(Throwable t) {
        if (t instanceof SQLException se) return se;
        Throwable c = t.getCause();
        while (c != null && c != t) {
            if (c instanceof SQLException se) return se;
            c = c.getCause();
        }
        return new SQLException(t.getMessage(), t);
    }
}
