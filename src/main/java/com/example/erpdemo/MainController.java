package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class MainController {

    @FXML private StackPane contentRoot;
    @FXML private Label pageTitle;
    @FXML private MenuButton userMenu;   // <-- rolü burada göstereceğiz

    private User loggedInUser;
    private ToggleGroup navGroup;

    @FXML
    public void initialize() {
        contentRoot.sceneProperty().addListener((obs, o, s) -> { if (s != null) setupToggleGroup(); });
        goDashboard();
    }

    public void setUser(User user) {
        this.loggedInUser = user;

        // Rolü sağ üst menüde göster (Yönetici / Kullanıcı vs.)
        if (user != null && user.getRole() != null && !user.getRole().isBlank()) {
            userMenu.setText(user.getRole());
        } else {
            userMenu.setText("Kullanıcı");
        }

        updateApprovalsVisibility();
    }

    @FXML public void openHelp() { loadInlineMessage("Yardım dokümanı yakında eklenecek."); }
    @FXML public void logout()   { LogoutUtil.performLogout(contentRoot); }

    @FXML public void goDashboard() { pageTitle.setText("Gösterge Paneli"); selectNav("Gösterge Paneli"); }
    @FXML public void goCustomers() { loadContent("customer-view.fxml", "Müşteri İşlemleri", "Müşteri İşlemleri"); }
    @FXML public void goRequests()  { loadContent("request-view.fxml",  "Talep/Teklif",      "Talep/Teklif"); }
    @FXML public void goApprovals() {
        if (isAdmin()) loadContent("approval-view.fxml", "Onay İşlemleri", "Onay İşlemleri");
        else { loadInlineMessage("Bu alana erişim yetkiniz yok."); selectNav(null); }
    }
    @FXML public void goReports()   { loadContent("reports-view.fxml",  "Raporlar",          "Raporlar"); }

    /** Önbellek yok: Her çağrıda FXML yeniden yüklenir; controller'da refresh() varsa çağrılır. */
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
                controller.getClass().getMethod("refresh").invoke(controller);
            } catch (NoSuchMethodException ignore) { /* refresh() yoksa sorun değil */ }

            contentRoot.getChildren().setAll(view);
            selectNav(navTextToSelect);

        } catch (IOException e) {
            e.printStackTrace();
            loadInlineMessage(title + " görünümü yüklenemedi (hata).");
            selectNav(navTextToSelect);
        } catch (ReflectiveOperationException e) {
            // refresh() yansıma çağrısında hata olsa da ekranı gösterelim
            contentRoot.getChildren().clear();
        }
    }

    private void loadInlineMessage(String message) {
        Label lbl = new Label(message);
        lbl.getStyleClass().add("hero-title");
        VBox box = new VBox(lbl);
        box.setSpacing(12);
        box.setStyle("-fx-alignment: center;");
        contentRoot.getChildren().setAll(box);
    }

    private boolean isAdmin() {
        return loggedInUser != null &&
                "Yonetici".equalsIgnoreCase(Objects.toString(loggedInUser.getRole(), ""));
    }

    private void updateApprovalsVisibility() {
        ToggleButton approvalsBtn = findNavByText("Onay İşlemleri");
        if (approvalsBtn != null) {
            boolean visible = isAdmin();
            approvalsBtn.setVisible(visible);
            approvalsBtn.setManaged(visible);
        }
    }

    private void setupToggleGroup() {
        VBox sidebar = getSidebar();
        if (sidebar == null) return;
        navGroup = new ToggleGroup();
        for (var node : sidebar.getChildren()) {
            if (node instanceof ToggleButton tb) tb.setToggleGroup(navGroup);
        }
    }

    private void selectNav(String text) {
        if (navGroup == null) return;
        if (text == null) { navGroup.selectToggle(null); return; }
        ToggleButton tb = findNavByText(text);
        if (tb != null) tb.setSelected(true); else navGroup.selectToggle(null);
    }

    private ToggleButton findNavByText(String text) {
        VBox sidebar = getSidebar();
        if (sidebar == null) return null;
        for (var node : sidebar.getChildren()) {
            if (node instanceof ToggleButton tb && text.equalsIgnoreCase(tb.getText())) return tb;
        }
        return null;
    }

    private VBox getSidebar() {
        var parent1 = contentRoot.getParent();
        if (parent1 == null) return null;
        var parent2 = parent1.getParent();
        if (parent2 instanceof BorderPane bp && bp.getLeft() instanceof VBox vbox) return vbox;
        return null;
    }
}
