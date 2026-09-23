package org.brasbat.exiforganizer;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
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
        stage.getIcons().add(createAppIcon(64));
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

    private Image createAppIcon(int size) {
        WritableImage icon = new WritableImage(size, size);
        PixelWriter pixels = icon.getPixelWriter();
        double center = size / 2.0;
        double radius = size * 0.46;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - center + 0.5;
                double dy = y - center + 0.5;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance > radius) {
                    pixels.setColor(x, y, Color.TRANSPARENT);
                    continue;
                }
                double blend = Math.max(0, Math.min(1, (y + x) / (double) (size * 1.4)));
                pixels.setColor(x, y, Color.rgb(
                        (int) (22 + 20 * blend),
                        (int) (42 + 55 * blend),
                        (int) (72 + 95 * blend)));
            }
        }

        drawRoundedRectangle(pixels, size, 15, 23, 49, 35, Color.web("#dbeafe"));
        drawRoundedRectangle(pixels, size, 20, 18, 20, 9, Color.web("#7dd3fc"));
        drawCircle(pixels, size, 32, 34, 12, Color.web("#0f172a"));
        drawCircle(pixels, size, 32, 34, 8, Color.web("#38bdf8"));
        drawCircle(pixels, size, 29, 31, 3, Color.web("#e0f2fe"));
        drawStar(pixels, size, 48, 15, 6, Color.web("#fbbf24"));
        return icon;
    }

    private void drawRoundedRectangle(PixelWriter pixels, int size, int left, int top,
                                      int width, int height, Color color) {
        for (int y = top; y < top + height; y++) {
            for (int x = left; x < left + width; x++) {
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    pixels.setColor(x, y, color);
                }
            }
        }
    }

    private void drawCircle(PixelWriter pixels, int size, int centerX, int centerY,
                            int radius, Color color) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (x >= 0 && x < size && y >= 0 && y < size
                        && Math.hypot(x - centerX, y - centerY) <= radius) {
                    pixels.setColor(x, y, color);
                }
            }
        }
    }

    private void drawStar(PixelWriter pixels, int size, int centerX, int centerY,
                          int radius, Color color) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (x >= 0 && x < size && y >= 0 && y < size
                        && (Math.abs(x - centerX) <= 1 || Math.abs(y - centerY) <= 1)
                        && Math.hypot(x - centerX, y - centerY) <= radius) {
                    pixels.setColor(x, y, color);
                }
            }
        }
    }
}
