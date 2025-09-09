module com.example.erpdemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql; // JDBC için bu satır gereklidir

    requires org.controlsfx.controls;


    opens com.example.erpdemo to javafx.fxml;
    exports com.example.erpdemo;
}