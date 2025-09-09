package com.example.erpdemo;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class EmailService {

    public static void sendReport(String toEmail, File reportFile) throws MessagingException, IOException {

        Properties props = new Properties();
        try (InputStream input = EmailService.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IOException("Unable to find application.properties");
            }
            props.load(input);
        }

        String fromEmail = props.getProperty("email.from");
        String password = props.getProperty("email.password");

        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.auth", "true");
        mailProps.put("mail.smtp.starttls.enable", "true");
        mailProps.put("mail.smtp.host", "smtp.gmail.com");
        mailProps.put("mail.smtp.port", "587");

        Session session = Session.getInstance(mailProps, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(fromEmail, password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("ERP Raporu: Onaylanmış Talepler");

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent("Sayın patron, onaylanmış talepler raporu ektedir.", "text/plain; charset=UTF-8");

        MimeBodyPart attachmentBodyPart = new MimeBodyPart();
        attachmentBodyPart.attachFile(reportFile);

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);
        multipart.addBodyPart(attachmentBodyPart);
        message.setContent(multipart);

        Transport.send(message);
    }
}