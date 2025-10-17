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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import javafx.event.EventHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;

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

    @FXML private Button btnDashboard;
    @FXML private Button btnCustomers;
    @FXML private Button btnRequests;
    @FXML private Button btnProducts;
    @FXML private Button btnApprovals;
    @FXML private Button btnReports;

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
        if ("__dashboard__".equals(currentKey)) {
            selectNav(btnDashboard);
            return;
        }
        showDashboardOnly();
    }

    private void showDashboardOnly() {
        pageTitle.setText("Gösterge Paneli");
        if (dashboardViewSnapshot != null) {
            contentRoot.getChildren().setAll(dashboardViewSnapshot);
            currentKey = "__dashboard__";
            loadDashboardMetrics();
            loadTodayDemandTable();
            selectNav(btnDashboard);     // <-- burada
            contentRoot.requestFocus();
        } else {
            loadInlineMessage("Gösterge Paneli yüklenemedi.");
            selectNav(btnDashboard);     // <-- burada
        }
    }

    public void goCustomers() { loadContentCached("customer-view.fxml", "Müşteri İşlemleri", btnCustomers, true); }
    public void goRequests()  { loadContentCached("request-view.fxml",  "Talep/Teklif",      btnRequests,  true); }
    public void goProducts()  { loadContentCached("stock-view.fxml",    "Ürün İşlemleri",    btnProducts,  true); }

    @FXML
    public void goApprovals() {
        if (isAdmin()) {
            loadContentCached("approval-view.fxml", "Onay İşlemleri", btnApprovals, true);
        } else {
            loadInlineMessage("Bu alana erişim yetkiniz yok.");
            selectNav(null);
        }
    }

    public void goReports()   { loadContentCached("reports-view.fxml",  "Raporlar", btnReports,   false); }

    /**
     * YENİ: FXML’i ilkinde yüklüyor, sonraki tıklamalarda cache’ten getiriyor.
     * @param callRefreshOnce true ise, controller’da varsa refresh() sadece ilk yüklemede çağrılır.
     */
    private void loadContentCached(String fxmlFile, String title, Button navToSelect, boolean callRefreshOnce) {
        pageTitle.setText(title);

        if (Objects.equals(currentKey, fxmlFile)) {
            selectNav(navToSelect);
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
                    selectNav(navToSelect);
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
            selectNav(navToSelect);
            contentRoot.requestFocus();

            // Her gösterimde onResume()
            invokeIfExists(controller, "onResume");

        } catch (IOException e) {
            e.printStackTrace();
            loadInlineMessage(title + " görünümü yüklenemedi (hata).");
            selectNav(navToSelect);
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
    private List<Button> navButtons() {
        return Arrays.asList(btnDashboard, btnCustomers, btnRequests, btnProducts, btnApprovals, btnReports);
    }

    private void selectNav(Button selected) {
        for (Button b : navButtons()) {
            if (b == null) continue;
            b.getStyleClass().remove("selected");
        }
        if (selected != null && !selected.getStyleClass().contains("selected")) {
            //selected.getStyleClass().add("selected");
        }
    }

    private void updateApprovalsVisibility() {
        if (btnApprovals == null) return;
        boolean visible = isAdmin();
        btnApprovals.setVisible(visible);
        btnApprovals.setManaged(visible);
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
            AppDialogs.warn("Oturum bilgisi alınamadı.");
            return;
        }

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Şifre Değiştir");
        dlg.setHeaderText(null);

        if (contentRoot != null && contentRoot.getScene() != null) {
            dlg.initOwner(contentRoot.getScene().getWindow());
        }

        IconUtil.decorateDialog(dlg);

        PasswordField currentPwd = new PasswordField();
        PasswordField newPwd     = new PasswordField();
        PasswordField newPwd2    = new PasswordField();

        currentPwd.setPromptText("Mevcut şifre");
        newPwd.setPromptText("Yeni şifre");
        newPwd2.setPromptText("Yeni şifre (tekrar)");

        var gp = new javafx.scene.layout.GridPane();
        gp.setHgap(10); gp.setVgap(10);
        gp.addRow(0, new Label("Mevcut Şifre:"),       currentPwd);
        gp.addRow(1, new Label("Yeni Şifre:"),         newPwd);
        gp.addRow(2, new Label("Yeni Şifre (Tekrar):"), newPwd2);

        dlg.getDialogPane().setContent(gp);

        ButtonType btnTamam = new ButtonType("Tamam", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnIptal = new ButtonType("İptal",  ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().addAll(btnTamam, btnIptal);

        // Canlı doğrulama: "Tamam" düğmesini şartlar sağlanana kadar kilitle
        final Node okBtn = dlg.getDialogPane().lookupButton(btnTamam);
        okBtn.disableProperty().bind(
                currentPwd.textProperty().isEmpty()
                        .or(newPwd.textProperty().isEmpty())
                        .or(newPwd2.textProperty().isEmpty())
                        .or(newPwd.textProperty().length().lessThan(4)) // dilerseniz min uzunluk
                        .or(newPwd.textProperty().isNotEqualTo(newPwd2.textProperty()))
        );

        dlg.setResultConverter(bt -> bt);
        var res = dlg.showAndWait();
        if (res.isEmpty() || res.get() != btnTamam) return;

        String cur = currentPwd.getText() == null ? "" : currentPwd.getText().trim();
        String np1 = newPwd.getText()     == null ? "" : newPwd.getText().trim();
        String np2 = newPwd2.getText()    == null ? "" : newPwd2.getText().trim();

        // Erken kontroller (DB’ye gitmeden)
        if (cur.isBlank()) {
            AppDialogs.warn("Mevcut şifreyi girin.");
            return;
        }
        if (np1.isBlank() || np2.isBlank()) {
            AppDialogs.warn("Yeni şifre alanları boş olamaz.");
            return;
        }
        if (!np1.equals(np2)) {
            AppDialogs.warn("Yeni şifreler birbiriyle aynı olmalıdır.");
            return;
        }
        if (np1.length() < 4) { // isteğe bağlı politika
            AppDialogs.warn("Yeni şifre en az 4 karakter olmalı.");
            return;
        }
        if (np1.equals(cur)) { // isteğe bağlı politika
            AppDialogs.warn("Yeni şifre mevcut şifreyle aynı olamaz.");
            return;
        }

        try {
            boolean updated = UserDAO.updatePassword(loggedInUser.getId(), cur, np1);
            if (!updated) {
                AppDialogs.error("Mevcut şifre yanlış.");
                return;
            }
            AppDialogs.info("Şifreniz güncellendi.");
        } catch (SQLException e) {
            AppDialogs.dbError("Şifre güncelleme", e);
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
            var cls = controller.getClass();
            var m = cls.getDeclaredMethod(methodName);
            m.setAccessible(true);
            m.invoke(controller);
        } catch (NoSuchMethodException ignore) {

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
