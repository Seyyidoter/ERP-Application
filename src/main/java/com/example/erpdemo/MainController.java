package com.example.erpdemo;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.event.EventHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

public class MainController {

    @FXML private StackPane contentRoot;
    @FXML private Label pageTitle;
    @FXML private MenuButton userMenu;

    // Dashboard kökü (FXML’de fx:id="dashboardRoot")
    @FXML private VBox dashboardRoot;

    // Dashboard metrikleri
    @FXML private Label lblTodayRequests;
    @FXML private Label lblTodayProducts;
    @FXML private Label lblTodayRevenue;

    // Ürün bazında talep tablosu
    @FXML private TableView<ProductDemandStat> tblTodayProductDemand;
    @FXML private TableColumn<ProductDemandStat, String>  colDemandProduct;
    @FXML private TableColumn<ProductDemandStat, Integer> colDemandQty;

    private User loggedInUser;
    /** Dashboard görünümünün snapshot’ı (FXML’den gelen dashboardRoot’un ta kendisi). */
    private Node dashboardViewSnapshot;

    // Scene’e eklenen “dış tıklama” filtresi için referans (leak/katlanma önler)
    private EventHandler<MouseEvent> outsideClickFilter;

    /* ----------- YENİ: Görünüm/Controller önbelleği ----------- */
    private final Map<String, Parent> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();
    private String currentKey = "__dashboard__"; // başlangıçta dashboard

    private volatile boolean disposed = false;

    private boolean uiDead() {
        if (disposed) return true;
        if (contentRoot == null) return true;
        Scene scene = contentRoot.getScene();
        if (scene == null) return true;
        var win = scene.getWindow();
        return (win == null || !win.isShowing());
    }

    @FXML
    public void initialize() {
        // Snapshot’ı FXML’den gelen node ile HEMEN ata
        if (dashboardRoot != null) {
            dashboardViewSnapshot = dashboardRoot;
        }

        // Dashboard tablosu (varsa)
        if (tblTodayProductDemand != null) {
            colDemandProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
            colDemandQty.setCellValueFactory(new PropertyValueFactory<>("totalQuantity"));
            tblTodayProductDemand.setPlaceholder(new Label("Bugün ürün talebi yok"));

            // Tablo içinde boş alana tıklanınca seçim/odak temizle
            tblTodayProductDemand.setRowFactory(tv -> {
                TableRow<ProductDemandStat> row = new TableRow<>();
                row.setOnMouseClicked(e -> {
                    if (row.isEmpty()) {
                        tblTodayProductDemand.getSelectionModel().clearSelection();
                        if (tblTodayProductDemand.getParent() != null)
                            tblTodayProductDemand.getParent().requestFocus();
                    }
                });
                return row;
            });

            // “Tablo DIŞINA tıklama” filtresi — scene yaşam döngüsüne bağla (ekle/çıkar)
            tblTodayProductDemand.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (oldScene != null && outsideClickFilter != null) {
                    oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
                }
                if (newScene != null) {
                    outsideClickFilter = e -> {
                        Node n = e.getPickResult().getIntersectedNode();
                        boolean inside = false;
                        while (n != null) {
                            if (n == tblTodayProductDemand) { inside = true; break; }
                            n = n.getParent();
                        }
                        if (!inside) {
                            tblTodayProductDemand.getSelectionModel().clearSelection();
                            if (tblTodayProductDemand.getParent() != null)
                                tblTodayProductDemand.getParent().requestFocus();
                        }
                    };
                    newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);

                    final EventHandler<WindowEvent> cleanup = we -> {
                        if (outsideClickFilter != null) {
                            newScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
                            outsideClickFilter = null;
                        }
                    };

                    if (newScene.getWindow() != null) {
                        newScene.getWindow().addEventHandler(WindowEvent.WINDOW_HIDING, cleanup);
                        newScene.getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, cleanup);
                    } else {
                        newScene.windowProperty().addListener((o, ow, nw) -> {
                            if (nw != null) {
                                nw.addEventHandler(WindowEvent.WINDOW_HIDING, cleanup);
                                nw.addEventHandler(WindowEvent.WINDOW_HIDDEN, cleanup);
                            }
                        });
                    }
                }
            });
        }

        Platform.runLater(() -> {
            if (contentRoot == null) return;
            Scene sc = contentRoot.getScene();
            if (sc == null) return;

            if (sc.getWindow() != null) {
                sc.getWindow().addEventHandler(WindowEvent.WINDOW_HIDING, ev -> disposed = true);
                sc.getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, ev -> disposed = true);
            } else {
                sc.windowProperty().addListener((o, ow, nw) -> {
                    if (nw != null) {
                        nw.addEventHandler(WindowEvent.WINDOW_HIDING, ev -> disposed = true);
                        nw.addEventHandler(WindowEvent.WINDOW_HIDDEN, ev -> disposed = true);
                    }
                });
            }
        });

        // Başlangıçta dashboard’u göster
        showDashboardOnly();

        Platform.runLater(() -> { if (contentRoot != null) contentRoot.requestFocus(); });
    }

    public void setUser(User user) {
        this.loggedInUser = user;
        if (userMenu != null) {
            String label = (user != null && user.getRole() != null && !user.getRole().isBlank())
                    ? user.getRole() : "Kullanıcı";
            userMenu.setText(label);
        }
        updateApprovalsVisibility();
    }

    @FXML public void logout() {
        HelloApplication.setLoggedInUserId(0);
        LogoutUtil.performLogout(contentRoot);
    }

    // ===================== NAV =====================

    @FXML
    public void goDashboard() {
        // Zaten dashboard’taysak hiçbir şey yapma (göz kırpma yok)
        if ("__dashboard__".equals(currentKey)) {
            selectNav("Gösterge Paneli");
            return;
        }
        showDashboardOnly();
    }

    private void showDashboardOnly() {
        pageTitle.setText("Gösterge Paneli");
        if (dashboardViewSnapshot != null) {
            contentRoot.getChildren().setAll(dashboardViewSnapshot);
            currentKey = "__dashboard__";
            // İlk geçişte metrikleri yenileyelim
            loadDashboardMetrics();
            loadTodayDemandTable();
            selectNav("Gösterge Paneli");
            contentRoot.requestFocus();
        } else {
            loadInlineMessage("Gösterge Paneli yüklenemedi.");
            selectNav("Gösterge Paneli");
        }
    }

    @FXML public void goCustomers() { loadContentCached("customer-view.fxml", "Müşteri İşlemleri", "Müşteri İşlemleri", true); }
    @FXML public void goRequests()  { loadContentCached("request-view.fxml",  "Talep/Teklif",      "Talep/Teklif", true); }
    @FXML public void goProducts()  { loadContentCached("stock-view.fxml",    "Ürün İşlemleri",    "Ürün İşlemleri", true); }

    @FXML
    public void goApprovals() {
        if (isAdmin()) loadContentCached("approval-view.fxml", "Onay İşlemleri", "Onay İşlemleri", true);
        else { loadInlineMessage("Bu alana erişim yetkiniz yok."); selectNav(null); }
    }

    @FXML public void goReports() { loadContentCached("reports-view.fxml", "Raporlar", "Raporlar", false); }

    /**
     * YENİ: FXML’i ilkinde yüklüyor, sonraki tıklamalarda cache’ten getiriyor.
     * @param callRefreshOnce true ise, controller’da varsa refresh() sadece ilk yüklemede çağrılır.
     */
    private void loadContentCached(String fxmlFile, String title, String navTextToSelect, boolean callRefreshOnce) {
        pageTitle.setText(title);

        if (Objects.equals(currentKey, fxmlFile)) {
            selectNav(navTextToSelect);
            // Aynı sayfaya tekrar gelindiyse sadece onResume tetikle
            Object sameController = controllerCache.get(fxmlFile);
            invokeIfExists(sameController, "onResume");
            return;
        }

        try {
            Parent view = viewCache.get(fxmlFile);
            Object controller = controllerCache.get(fxmlFile);

            if (view == null) {
                URL url = getClass().getResource(fxmlFile);
                if (url == null) {
                    System.err.println("Uyarı: FXML bulunamadı: " + fxmlFile);
                    loadInlineMessage(title + " görünümü yüklenemedi (dosya yok).");
                    selectNav(navTextToSelect);
                    return;
                }
                FXMLLoader loader = new FXMLLoader(url);
                view = loader.load();
                controller = loader.getController();

                // Controller özel entegrasyonları (ilk yükleme)
                if (controller instanceof ApprovalController ac && loggedInUser != null) {
                    ac.setCurrentUserId(loggedInUser.getId());
                }

                // SADECE İLK YÜKLEMEDE refresh()
                if (callRefreshOnce) {
                    invokeIfExists(controller, "refresh");
                }

                viewCache.put(fxmlFile, view);
                controllerCache.put(fxmlFile, controller);
            }

            // Ekrana getir
            contentRoot.getChildren().setAll(view);
            currentKey = fxmlFile;
            selectNav(navTextToSelect);
            contentRoot.requestFocus();

            // Her gösterimde onResume()
            invokeIfExists(controller, "onResume");

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
        currentKey = "__message__";
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

    // ================= Dashboard veri yükleme =================
    private void loadDashboardMetrics() {
        if (lblTodayRequests == null || lblTodayProducts == null || lblTodayRevenue == null) return;

        // İsteğe bağlı: “yükleniyor” göstergesi
        lblTodayRequests.setText("…");
        lblTodayProducts.setText("…");
        lblTodayRevenue.setText("…");

        Async.run(
                // BACKGROUND
                () -> {
                    int req = DashboardDAO.getTodayRequestCount();
                    int qty = DashboardDAO.getTodayProductQuantity();
                    BigDecimal rev = DashboardDAO.getTodayRevenueBD();
                    return new Object[]{req, qty, rev};
                },
                // SUCCESS (FX thread)
                data -> {
                    if (uiDead() || !"__dashboard__".equals(currentKey)) return;
                    int req = (int) data[0];
                    int qty = (int) data[1];
                    BigDecimal rev = (BigDecimal) data[2];

                    lblTodayRequests.setText(String.valueOf(req));
                    lblTodayProducts.setText(String.valueOf(qty));
                    lblTodayRevenue.setText(Money.fmtTRWithSymbol(rev));
                },
                // ERROR
                ex -> {
                    if (uiDead()) return;
                    lblTodayRequests.setText("-");
                    lblTodayProducts.setText("-");
                    lblTodayRevenue.setText("-");
                },
                // FINALLY
                () -> { /* no-op */ }
        );
    }

    private void loadTodayDemandTable() {
        if (tblTodayProductDemand == null) return;

        tblTodayProductDemand.setItems(FXCollections.observableArrayList()); // temizle/placeholder

        Async.run(
                // BACKGROUND
                () -> DashboardDAO.getTodayDemandByProduct(),
                // SUCCESS
                list -> {
                    if (uiDead() || !"__dashboard__".equals(currentKey)) return;
                    tblTodayProductDemand.setItems(FXCollections.observableArrayList(list));
                },
                // ERROR
                ex -> {
                    if (uiDead()) return;
                    tblTodayProductDemand.setItems(FXCollections.observableArrayList());
                },
                // FINALLY
                () -> { /* no-op */ }
        );
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

        if (contentRoot != null && contentRoot.getScene() != null) {
            dlg.initOwner(contentRoot.getScene().getWindow()); // <-- EKLE (ÖNEMLİ)
        }

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
        ButtonType btnIptal = new ButtonType("İptal", ButtonBar.ButtonData.CANCEL_CLOSE);
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
        if (contentRoot != null && contentRoot.getScene() != null) {
            a.initOwner(contentRoot.getScene().getWindow());
        }
        a.showAndWait();
    }

    private static void invokeIfExists(Object controller, String methodName) {
        if (controller == null) return;
        try {
            var m = controller.getClass().getMethod(methodName);
            m.setAccessible(true);
            m.invoke(controller);
        } catch (NoSuchMethodException ignore) {
            // controller bu metodu tanımlamadıysa sessizce geç
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
