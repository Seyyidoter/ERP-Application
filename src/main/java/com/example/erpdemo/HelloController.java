package com.example.erpdemo;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class HelloController {

    @FXML private TextField txtUser;
    @FXML private PasswordField txtPass;
    @FXML private Button btnLogin; // Enter için default button

    @FXML
    public void initialize() {
        // Enter ile giriş
        txtUser.setOnAction(e -> handleLogin());
        txtPass.setOnAction(e -> handleLogin());
        if (btnLogin != null) btnLogin.setDefaultButton(true);
    }

    @FXML
    private void handleLogin() {
        final String username = txtUser.getText();
        final String password = txtPass.getText();

        var task = new Task<Boolean>() {
            @Override protected Boolean call() throws SQLException {
                return DatabaseManager.validateLogin(username, password);
            }
        };

        task.setOnSucceeded(ev -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                    Parent root = loader.load();

                    MainController mc = loader.getController();
                    mc.setUser(UserDAO.getUserByUsername(username));

                    Stage st = new Stage();
                    st.setTitle("Omnis");
                    st.getIcons().addAll(
                            new Image(getClass().getResourceAsStream("/com/example/erpdemo/assets/logo-16.png")),
                            new Image(getClass().getResourceAsStream("/com/example/erpdemo/assets/logo-32.png")),
                            new Image(getClass().getResourceAsStream("/com/example/erpdemo/assets/logo-64.png"))
                    );
                    st.setScene(new Scene(root));
                    st.setMaximized(true);
                    st.show();

                    // login penceresini kapat
                    ((Stage) btnLogin.getScene().getWindow()).close();

                } catch (IOException | SQLException ex) {
                    showError("Hata", "Ana ekran açılamadı:\n" + ex.getMessage());
                }
            } else {
                showError("Hata", "Kullanıcı adı veya şifre yanlış!");
            }
        });

        task.setOnFailed(ev ->
                showError("Bağlantı Hatası", "Veritabanına bağlanılamadı.")
        );

        new Thread(task, "login-task").start();
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null); a.showAndWait();
    }
}
