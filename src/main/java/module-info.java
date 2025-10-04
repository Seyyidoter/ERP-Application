module com.example.erpdemo {
    // --- bağımlılıklar ---
    requires javafx.controls;
    requires javafx.fxml;
    requires com.zaxxer.hikari;
    requires java.sql;
    requires org.apache.poi.ooxml;
    requires org.apache.pdfbox;

    exports com.example.erpdemo.app;
    exports com.example.erpdemo.controller;
    exports com.example.erpdemo.dao;
    exports com.example.erpdemo.model;
    exports com.example.erpdemo.util;

    opens com.example.erpdemo.controller to javafx.fxml;

}
