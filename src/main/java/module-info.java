module com.example.erpdemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.pdfbox;
    requires com.zaxxer.hikari;
    requires java.sql;
    opens com.example.erpdemo to javafx.fxml;
    exports com.example.erpdemo;
}
