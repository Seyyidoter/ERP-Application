package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;

public class ProfileController {

    @FXML private Label lblUsername;
    @FXML private Label lblRole;
    @FXML private PasswordField txtCurrent;
    @FXML private PasswordField txtNew;
    @FXML private PasswordField txtNew2;

    private User user;
    private Stage dialogStage;

    public void setDialogStage(Stage s) { this.dialogStage = s; }
    public void setUser(User u) {
        this.user = u;
        lblUsername.setText(u != null ? u.getUsername() : "—");
        lblRole.setText(u != null ? u.getRole() : "—");
    }

    @FXML
    private void handleChangePassword() {
        if (user == null) { info("Hata", "Kullanıcı bilgisi bulunamadı."); return; }

        String cur = txtCurrent.getText();
        String n1  = txtNew.getText();
        String n2  = txtNew2.getText();

        if (cur.isBlank() || n1.isBlank() || n2.isBlank()) { info("Uyarı","Tüm alanları doldurun."); return; }
        if (!n1.equals(n2)) { info("Uyarı","Yeni şifreler aynı değil."); return; }
        if (n1.length() < 4) { info("Uyarı","Yeni şifre en az 4 karakter olmalı."); return; }

        try {
            boolean ok = UserDAO.updatePassword(user.getId(), cur, n1);
            if (ok) {
                info("Başarılı", "Şifre değiştirildi.");
                txtCurrent.clear(); txtNew.clear(); txtNew2.clear();
            } else {
                info("Hata", "Mevcut şifre doğru değil.");
            }
        } catch (SQLException e) {
            info("Hata", "Şifre değiştirilemedi: " + e.getMessage());
        }
    }

    @FXML private void handleClose() { if (dialogStage != null) dialogStage.close(); }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null);
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }
}
