package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Objects;

public class MainController {

    @FXML private StackPane contentRoot;
    @FXML private Label pageTitle;
    @FXML private MenuButton userMenu;

    // Dashboard (FXML'den gelen)
    @FXML private VBox dashboardRoot;
    @FXML private Label lblTodayRequests;
    @FXML private Label lblTodayProducts;
    @FXML private Label lblTodayRevenue;

    // Ürün bazında talep tablosu
    @FXML private TableView<ProductDemandStat> tblTodayProductDemand;
    @FXML private TableColumn<ProductDemandStat, String>  colDemandProduct;
    @FXML private TableColumn<ProductDemandStat, Integer> colDemandQty;

    private User loggedInUser;
    private Node dashboardViewSnapshot; // dashboardRoot referansı

    @FXML
    public void initialize() {
        // Dashboard referansını sakla
        if (dashboardRoot != null) {
            dashboardViewSnapshot = dashboardRoot;
        }

        // Tablo kolon bağları ve placeholder
        if (tblTodayProductDemand != null) {
            colDemandProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
            colDemandQty.setCellValueFactory(new PropertyValueFactory<>("totalQuantity"));
            tblTodayProductDemand.setPlaceholder(new Label("Bugün ürün talebi yok"));
        }

        // Açılışta dashboard verileri
        loadDashboardMetrics();
        loadTodayDemandTable();

        // Açılış seçimi
        goDashboard();
    }

    public void setUser(User user) {
        this.loggedInUser = user;
        if (user != null && user.getRole() != null && !user.getRole().isBlank()) {
            userMenu.setText(user.getRole());
        } else {
            userMenu.setText("Kullanıcı");
        }
        updateApprovalsVisibility();
    }

    @FXML public void openHelp() { loadInlineMessage("Yardım dokümanı yakında eklenecek."); }
    @FXML public void logout()   { LogoutUtil.performLogout(contentRoot); }

    @FXML
    public void goDashboard() {
        pageTitle.setText("Gösterge Paneli");
        if (dashboardViewSnapshot != null) {
            contentRoot.getChildren().setAll(dashboardViewSnapshot);
        }
        selectNav("Gösterge Paneli");
        loadDashboardMetrics();
        loadTodayDemandTable();
        contentRoot.requestFocus();
    }

    @FXML public void goCustomers() { loadContent("customer-view.fxml", "Müşteri İşlemleri", "Müşteri İşlemleri"); }
    @FXML public void goRequests()  { loadContent("request-view.fxml",  "Talep/Teklif",      "Talep/Teklif"); }
    @FXML public void goApprovals() {
        if (isAdmin()) loadContent("approval-view.fxml", "Onay İşlemleri", "Onay İşlemleri");
        else { loadInlineMessage("Bu alana erişim yetkiniz yok."); selectNav(null); }
    }
    @FXML public void goReports()   { loadContent("reports-view.fxml",  "Raporlar",          "Raporlar"); }

    /** Genel içerik yükleme */
    private void loadContent(String fxmlFile, String title, String navTextToSelect) {
        pageTitle.setText(title);
        try {
            URL url = getClass().getResource(fxmlFile);
            if (url == null) {
                System.err.println("Uyarı: FXML bulunamadı: " + fxmlFile);
                loadInlineMessage(title + " görünümü yüklenemedi (dosya yok).");
                selectNav(navTextToSelect);
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Parent view = loader.load();

            Object controller = loader.getController();
            try {
                try { controller.getClass().getMethod("refresh").invoke(controller); }
                catch (NoSuchMethodException ignore) {}
                if (controller instanceof ApprovalController ac && loggedInUser != null) {
                    ac.setCurrentUserId(loggedInUser.getId());
                }
            } catch (ReflectiveOperationException ignore) { }

            contentRoot.getChildren().setAll(view);
            selectNav(navTextToSelect);
            contentRoot.requestFocus();

        } catch (IOException e) {
            e.printStackTrace();
            loadInlineMessage(title + " görünümü yüklenemedi (hata).");
            selectNav(navTextToSelect);
        }
    }

    private void loadInlineMessage(String message) {
        Label lbl = new Label(message);
        lbl.getStyleClass().add("hero-title");
        VBox box = new VBox(lbl);
        box.setSpacing(12);
        box.setStyle("-fx-alignment: center;");
        contentRoot.getChildren().setAll(box);
        contentRoot.requestFocus();
    }

    private boolean isAdmin() {
        return loggedInUser != null &&
                "Yonetici".equalsIgnoreCase(Objects.toString(loggedInUser.getRole(), ""));
    }

    /* ----------- Nav yardımcıları (Button ile) ----------- */

    private Button findNavButtonByText(String text) {
        VBox sidebar = getSidebar();
        if (sidebar == null) return null;
        for (var node : sidebar.getChildren()) {
            if (node instanceof Button b && text != null && text.equalsIgnoreCase(b.getText())) return b;
        }
        return null;
    }

    private void selectNav(String text) {
        VBox sidebar = getSidebar();
        if (sidebar == null) return;
        for (var node : sidebar.getChildren()) {
            if (node instanceof Button b) {
                b.getStyleClass().remove("selected");
                if (text != null && text.equalsIgnoreCase(b.getText())) {
                    if (!b.getStyleClass().contains("selected")) b.getStyleClass().add("selected");
                }
            }
        }
    }

    private void updateApprovalsVisibility() {
        Button approvalsBtn = findNavButtonByText("Onay İşlemleri");
        if (approvalsBtn != null) {
            boolean visible = isAdmin();
            approvalsBtn.setVisible(visible);
            approvalsBtn.setManaged(visible);
        }
    }

    private VBox getSidebar() {
        var parent1 = contentRoot.getParent();
        if (parent1 == null) return null;
        var parent2 = parent1.getParent();
        if (parent2 instanceof BorderPane bp && bp.getLeft() instanceof VBox vbox) return vbox;
        return null;
    }

    /* ----------- Dashboard veri yükleme ----------- */

    private void loadDashboardMetrics() {
        if (lblTodayRequests == null || lblTodayProducts == null || lblTodayRevenue == null) return;
        try {
            int req = DashboardDAO.getTodayRequestCount();
            int qty = DashboardDAO.getTodayProductQuantity();
            double rev = DashboardDAO.getTodayRevenue();
            lblTodayRequests.setText(String.valueOf(req));
            lblTodayProducts.setText(String.valueOf(qty));
            lblTodayRevenue.setText(String.format("%.2f TL", rev));
        } catch (SQLException e) {
            lblTodayRequests.setText("-");
            lblTodayProducts.setText("-");
            lblTodayRevenue.setText("-");
            e.printStackTrace();
        }
    }

    private void loadTodayDemandTable() {
        if (tblTodayProductDemand == null) return;
        try {
            var list = DashboardDAO.getTodayDemandByProduct();
            tblTodayProductDemand.setItems(FXCollections.observableArrayList(list));
        } catch (SQLException e) {
            tblTodayProductDemand.setItems(FXCollections.observableArrayList());
            e.printStackTrace();
        }
    }
}
