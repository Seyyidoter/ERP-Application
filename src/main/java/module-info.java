module com.example.erpdemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.apache.pdfbox;
    opens com.example.erpdemo to javafx.fxml;
    exports com.example.erpdemo;
}
