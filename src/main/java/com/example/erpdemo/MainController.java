package com.example.erpdemo;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Objects;

public class MainController {

    @FXML private StackPane contentRoot;
    @FXML private Label pageTitle;
    @FXML private MenuButton userMenu;

    // Dashboard
    @FXML private VBox dashboardRoot;
    @FXML private Label lblTodayRequests;
    @FXML private Label lblTodayProducts;
    @FXML private Label lblTodayRevenue;

    // Ürün bazında talep tablosu
    @FXML private TableView<ProductDemandStat> tblTodayProductDemand;
    @FXML private TableColumn<ProductDemandStat, String>  colDemandProduct;
    @FXML private TableColumn<ProductDemandStat, Integer> colDemandQty;

    private User loggedInUser;
    private Node dashboardViewSnapshot;

    @FXML
    public void initialize() {
        if (dashboardRoot != null) dashboardViewSnapshot = dashboardRoot;

        if (tblTodayProductDemand != null) {
            colDemandProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
            colDemandQty.setCellValueFactory(new PropertyValueFactory<>("totalQuantity"));
            tblTodayProductDemand.setPlaceholder(new Label("Bugün ürün talebi yok"));

            // Açılışta mavi odak çerçevesini engelle
            tblTodayProductDemand.setFocusTraversable(false);
            tblTodayProductDemand.getSelectionModel().clearSelection();
        }

        loadDashboardMetrics();
        loadTodayDemandTable();
        goDashboard();

        Platform.runLater(() -> {
            if (contentRoot != null) contentRoot.requestFocus();
        });
    }

    public void setUser(User user) {
        this.loggedInUser = user;
        userMenu.setText(user != null && user.getRole() != null && !user.getRole().isBlank()
                ? user.getRole() : "Kullanıcı");
        updateApprovalsVisibility();
    }

    @FXML public void logout() { LogoutUtil.performLogout(contentRoot); }

    @FXML
    public void goDashboard() {
        pageTitle.setText("Gösterge Paneli");
        if (dashboardViewSnapshot != null) contentRoot.getChildren().setAll(dashboardViewSnapshot);
        selectNav("Gösterge Paneli");
        loadDashboardMetrics();
        loadTodayDemandTable();
        contentRoot.requestFocus();
    }

    @FXML public void goCustomers() { loadContent("customer-view.fxml", "Müşteri İşlemleri", "Müşteri İşlemleri"); }
    @FXML public void goRequests()  { loadContent("request-view.fxml",  "Talep/Teklif",      "Talep/Teklif"); }
    @FXML public void goProducts()  { loadContent("stock-view.fxml",    "Ürün İşlemleri",    "Ürün İşlemleri"); }

    @FXML
    public void goApprovals() {
        if (isAdmin()) loadContent("approval-view.fxml", "Onay İşlemleri", "Onay İşlemleri");
        else { loadInlineMessage("Bu alana erişim yetkiniz yok."); selectNav(null); }
    }

    @FXML public void goReports() { loadContent("reports-view.fxml", "Raporlar", "Raporlar"); }

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
                "Yonetici".equalsIgnoreCase(String.valueOf(loggedInUser.getRole()));
    }

    // ---- Nav yardımcıları ----
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

    // ---- Dashboard veri yükleme ----
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

    // ================= Şifre Değiştirme =================
    @FXML
    private void changePassword() {
        if (loggedInUser == null) {
            loadInlineMessage("Oturum bilgisi alınamadı.");
            return;
        }

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Şifre Değiştir");
        dlg.setHeaderText(null);

        // Logo
        Stage stage = (Stage) dlg.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("assets/logo-32.png"))));

        PasswordField currentPwd = new PasswordField();
        PasswordField newPwd     = new PasswordField();
        PasswordField newPwd2    = new PasswordField();

        currentPwd.setPromptText("Mevcut şifre");
        newPwd.setPromptText("Yeni şifre");
        newPwd2.setPromptText("Yeni şifre (tekrar)");

        var gp = new javafx.scene.layout.GridPane();
        gp.setHgap(10); gp.setVgap(10);
        gp.addRow(0, new Label("Mevcut Şifre:"), currentPwd);
        gp.addRow(1, new Label("Yeni Şifre:"),   newPwd);
        gp.addRow(2, new Label("Yeni Şifre (Tekrar):"), newPwd2);

        dlg.getDialogPane().setContent(gp);

        ButtonType btnTamam = new ButtonType("Tamam", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnIptal  = new ButtonType("İptal", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().addAll(btnTamam, btnIptal);

        dlg.setResultConverter(bt -> bt);
        var res = dlg.showAndWait();
        if (res.isEmpty() || res.get() != btnTamam) return;

        String cur = currentPwd.getText();
        String np1 = newPwd.getText();
        String np2 = newPwd2.getText();

        if (np1 == null || np1.isBlank() || !np1.equals(np2)) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Yeni şifreler boş olamaz ve birbiriyle aynı olmalıdır.");
            return;
        }

        try {
            boolean updated = UserDAO.updatePassword(loggedInUser.getId(), cur, np1);
            if (!updated) { showAlert(Alert.AlertType.ERROR, "Hata", "Mevcut şifre yanlış."); return; }
            showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Şifreniz güncellendi.");
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Hata", "Şifre güncellenemedi: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null);
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }
}
