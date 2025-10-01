module com.example.erpdemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.zaxxer.hikari;
    requires java.sql;
    requires org.apache.poi.ooxml;
    requires org.apache.pdfbox;
    opens com.example.erpdemo to javafx.fxml;
    exports com.example.erpdemo;
}
