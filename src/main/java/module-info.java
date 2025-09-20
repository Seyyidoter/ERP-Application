module com.example.erpdemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.apache.pdfbox;
    requires java.desktop;
    requires jdk.internal.le;
    opens com.example.erpdemo to javafx.fxml;
    exports com.example.erpdemo;
}
