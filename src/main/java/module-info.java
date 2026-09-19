module org.brasbat.exiforganizer {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.desktop;
    requires java.net.http;
    requires atlantafx.base;
    requires org.xerial.sqlitejdbc;
    requires metadata.extractor;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;

    opens org.brasbat.exiforganizer to javafx.fxml;
    exports org.brasbat.exiforganizer;
}