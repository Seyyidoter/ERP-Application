package com.example.erpdemo;

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
import java.sql.SQLException;
import java.util.Locale;

/** Müşteri listesi + CRUD + Geçmiş + Filtreleme (ASYNC yükleme) — bakiye/ödeme yok */
public class CustomerController {

    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Integer> idColumn;
    @FXML private TableColumn<Customer, String>  nameColumn;
    @FXML private TableColumn<Customer, String>  contactColumn;
    @FXML private TableColumn<Customer, String>  phoneColumn;
    @FXML private TableColumn<Customer, String>  emailColumn;
    @FXML private TableColumn<Customer, Integer> iskontoColumn;

    @FXML private TextField searchField;

    // Seçime bağlı butonlar
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button historyButton;

    // Alt buton çubuğu (FXML'de fx:id="actionsBar")
    @FXML private HBox actionsBar;

    private final ObservableList<Customer> master = FXCollections.observableArrayList();
    private FilteredList<Customer> filtered;

    @FXML
    public void initialize() {
        // sütun–model bağları
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("companyName"));
        contactColumn.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        iskontoColumn.setCellValueFactory(new PropertyValueFactory<>("iskonto"));
        iskontoColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

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
        historyButton.disableProperty().bind(noSelection);

        // tablo içinde boş alana tıklanınca seçimi/odağı temizle
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

        // tablo DIŞI tıklamada seçimi temizle — actionsBar HARİÇ
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

        filtered.setPredicate(c ->
                contains(c.getCompanyName(), q) ||
                        contains(c.getContactPerson(), q) ||
                        contains(c.getPhone(), q) ||
                        contains(c.getEmail(), q) ||
                        String.valueOf(c.getIskonto()).contains(q)
        );
    }

    private boolean contains(String val, String q) {
        return val != null && val.toLowerCase(Locale.ROOT).contains(q);
    }

    /** Müşterileri arka planda yükler; UI donmaz. */
    private void loadCustomers() {
        setBusy(true);
        Async.run(
                () -> {
                    try { return CustomerDAO.getAllCustomers(); }
                    catch (SQLException e) { throw new RuntimeException(e); }
                },
                list -> master.setAll(list),
                ex -> AppDialogs.dbError("Müşteri verileri yüklenmesi", toSql(ex)),
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-customer.fxml"));
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
        if (selectedCustomer == null) return;

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
            loadCustomers(); // async
        } catch (IOException e) {
            AppDialogs.unexpectedError("Müşteri düzenleme penceresi açma", e);
        }
    }

    @FXML
    private void handleDeleteButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Müşteriyi silmek istediğinizden emin misiniz?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Onay");
        IconUtil.decorateAlert(confirm);
        confirm.showAndWait();

        if (confirm.getResult() == ButtonType.YES) {
            setBusy(true);
            Async.runVoid(
                    () -> {
                        try { CustomerDAO.deleteCustomer(selectedCustomer.getId()); }
                        catch (SQLException e) { throw new RuntimeException(e); }
                    },
                    () -> { AppDialogs.info("Müşteri başarıyla silindi."); loadCustomers(); },
                    ex  -> AppDialogs.dbError("Müşteri silme", toSql(ex)),
                    ()  -> setBusy(false)
            );
        }
    }

    // ---------- GEÇMİŞ ----------
    @FXML
    private void handleCustomerHistory() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
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
