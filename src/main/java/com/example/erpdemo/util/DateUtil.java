package com.example.erpdemo.util;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DateUtil {
    private DateUtil() {}

    // TR yerelinde gg.AA.yyyy
    public static final Locale  TR = new Locale("tr", "TR");
    public static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy", TR);

    /** LocalDate kolonları için hazır hücre biçimlendirici */
    public static <S> void setDateColumnDMY(TableColumn<S, LocalDate> col) {
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : d.format(DMY));
            }
        });
    }

    /** Label vb. için kısa yardımcı */
    public static String fmt(LocalDate d) { return d == null ? "" : d.format(DMY); }
}
