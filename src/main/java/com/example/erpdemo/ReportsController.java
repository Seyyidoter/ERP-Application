package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import javafx.collections.ObservableList;

public class ReportsController {

    @FXML
    private void generateApprovedRequestsReport() {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // Fontu yüklüyoruz.
                InputStream fontStream = ReportsController.class.getResourceAsStream("times.ttf");
                PDType0Font font = PDType0Font.load(document, fontStream);

                contentStream.beginText();
                contentStream.setFont(font, 12);
                contentStream.setLeading(14.5f);
                contentStream.newLineAtOffset(25, 750);

                contentStream.showText("Onaylanmış Talepler Raporu");
                contentStream.newLine();

                contentStream.setFont(font, 10);

                ObservableList<Request> approvedRequests = RequestDAO.getApprovedRequests();

                if (approvedRequests.isEmpty()) {
                    contentStream.showText("Onaylanmış talep bulunamadı.");
                } else {
                    for (Request request : approvedRequests) {
                        Customer customer = CustomerDAO.getCustomerById(request.getCustomerId());
                        String customerName = customer != null ? customer.getCompanyName() : "Bilinmiyor";

                        contentStream.showText("--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
                        contentStream.newLine();
                        contentStream.showText("Talep ID: " + request.getId());
                        contentStream.newLine();
                        contentStream.showText("Müşteri Adı: " + customerName);
                        contentStream.newLine();
                        contentStream.showText("Talep Tarihi: " + request.getRequestDate());
                        contentStream.newLine();
                        contentStream.showText("Durum: " + request.getStatus());
                        contentStream.newLine();
                        contentStream.showText("--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
                        contentStream.newLine();
                    }
                }
                contentStream.endText();
            }

            String fileName = "ApprovedRequestsReport.pdf";
            document.save(fileName);
            showAlert("Başarılı", "Rapor başarıyla oluşturuldu: " + fileName);

        } catch (IOException | SQLException e) {
            showAlert("Hata", "PDF raporu oluşturulurken bir hata oluştu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}