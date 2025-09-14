package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class MainController {

    @FXML private StackPane contentRoot;
    @FXML private Label pageTitle;
    @FXML private MenuButton userMenu;

    private User loggedInUser;

    @FXML
    public void initialize() {
        goDashboard();
    }

    public void setUser(User user) {
        this.loggedInUser = user;
        if (user != null && user.getRole() != null && !user.getRole().isBlank()) {
            userMenu.setText(user.getRole());
        } else {
            userMenu.setText("Kullanıcı");
        }
        updateApprovalsVisibility(); // Button versiyonu
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

    /** FXML yükle + varsa refresh() çağır + ApprovalController'a userId aktar + fokus'u content'e ver */
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
                // refresh() varsa
                try { controller.getClass().getMethod("refresh").invoke(controller); }
                catch (NoSuchMethodException ignore) { /* yoksa sorun değil */ }

                // ApprovalController ise currentUserId aktar
                if (controller instanceof ApprovalController ac && loggedInUser != null) {
                    ac.setCurrentUserId(loggedInUser.getId());
                }
            } catch (ReflectiveOperationException ignore) { }

            contentRoot.getChildren().setAll(view);
            selectNav(navTextToSelect);

            // NAV butonundaki fokus'u kaldır, mavi çerçeve kalmasın
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
    }

    private boolean isAdmin() {
        return loggedInUser != null &&
                "Yonetici".equalsIgnoreCase(Objects.toString(loggedInUser.getRole(), ""));
    }

    /* ----------- Button ile çalışan nav yardımcıları ----------- */

    /** Başlığa göre sol menüdeki Button'u bul */
    private Button findNavButtonByText(String text) {
        VBox sidebar = getSidebar();
        if (sidebar == null) return null;
        for (var node : sidebar.getChildren()) {
            if (node instanceof Button b && text != null && text.equalsIgnoreCase(b.getText())) return b;
        }
        return null;
    }

    /** Seçimi styleClass "selected" ile yönet */
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

    /** "Onay İşlemleri" butonunu role göre göster/gizle */
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
}
