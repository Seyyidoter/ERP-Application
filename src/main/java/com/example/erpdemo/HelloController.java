package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class HelloController {

    @FXML private TextField txtUser;
    @FXML private PasswordField txtPass;
    @FXML private Button btnLogin; // Enter için default button

    @FXML
    public void initialize() {
        txtUser.setOnAction(e -> handleLogin());
        txtPass.setOnAction(e -> handleLogin());
        if (btnLogin != null) btnLogin.setDefaultButton(true);
    }

    @FXML
    private void handleLogin() {
        final String username = txtUser.getText();
        final String password = txtPass.getText();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            showError("Hata", "Kullanıcı adı ve şifre zorunludur.");
            return;
        }

        setBusy(true);

        Async.run(
                // work
                () -> DatabaseManager.validateLogin(username, password),

                // onSuccess (FX Thread)
                ok -> {
                    try {
                        if (Boolean.TRUE.equals(ok)) {
                            User loggedIn = UserDAO.getUserByUsername(username);
                            if (loggedIn == null) {
                                showError("Hata", "Kullanıcı bulunamadı.");
                                return;
                            }

                            HelloApplication.setLoggedInUserId(loggedIn.getId());

                            FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                            Parent root = loader.load();

                            MainController mc = loader.getController();
                            mc.setUser(loggedIn);

                            Stage st = new Stage();
                            st.setTitle("Omnis");
                            IconUtil.setAppIcon(st);
                            st.setScene(new Scene(root));
                            st.setMaximized(true);
                            st.show();

                            // Login penceresini kapat
                            ((Stage) btnLogin.getScene().getWindow()).close();
                        } else {
                            showError("Hata", "Kullanıcı adı veya şifre yanlış!");
                        }
                    } catch (IOException | SQLException ex) {
                        showError("Hata", "Ana ekran açılamadı:\n" + ex.getMessage());
                    }
                },

                // onError
                ex -> showError("Bağlantı Hatası", "Veritabanına bağlanılamadı."),

                // onFinally
                () -> setBusy(false)
        );
    }

    private void setBusy(boolean busy) {
        if (txtUser != null) txtUser.setDisable(busy);
        if (txtPass != null) txtPass.setDisable(busy);
        if (btnLogin != null) btnLogin.setDisable(busy);
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        IconUtil.decorateAlert(a);
        if (btnLogin != null && btnLogin.getScene() != null) {
            a.initOwner(btnLogin.getScene().getWindow());
        }
        a.showAndWait();
    }
}
