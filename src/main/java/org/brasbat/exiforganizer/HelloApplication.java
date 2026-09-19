package org.brasbat.exiforganizer;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        try {
            LocationDatabase.initialize();
        } catch (IOException ex) {
            System.err.println("Location database unavailable: " + ex.getMessage());
        }
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(loader.load(), 1400, 900);
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        stage.setTitle("EXIF Organizer");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            event.consume();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }
}
