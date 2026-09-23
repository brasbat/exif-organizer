package org.brasbat.exiforganizer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HelloController {
    @FXML private ListView<String> sourceFoldersView;
    @FXML private TextField destinationField;
    @FXML private VBox folderStructureEditor;
    @FXML private CheckBox includeSubfoldersCheckBox;
    @FXML private CheckBox moveCheckBox;
    @FXML private VBox imageFormatSelection;
    @FXML private Button scanButton;
    @FXML private Button organizeButton;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TreeView<String> previewTree;
    @FXML private ListView<String> statisticsView;

    private final ObservableList<PhotoFile> photos = FXCollections.observableArrayList();
    private final ObservableList<String> sourceFolders = FXCollections.observableArrayList();
    private final ObservableList<String> structureTokens = FXCollections.observableArrayList();
    private final ObservableList<String> statistics = FXCollections.observableArrayList();
    private final ObservableList<String> availableProperties = FXCollections.observableArrayList(
            "{Year}", "{Month}", "{Day}", "{Hour}", "{Minute}", "{Second}",
            "{Make}", "{Model}", "{Location}", "{DateTimeOriginal:yyyy-MM-dd}");
    private boolean scanCompleted;
    private boolean busy;
    private boolean destinationIsDefault;
    private boolean settingDefaultDestination;
    private Map<String, CheckBox> imageFormatCheckBoxes = new LinkedHashMap<>();

    @FXML
    private void initialize() {
        structureTokens.addAll("{Year}", "{Month}", "{Day}");
        rebuildStructureEditor();
        progressBar.setVisible(false);
        sourceFoldersView.setItems(sourceFolders);
        previewTree.setShowRoot(true);
        previewTree.setRoot(new TreeItem<>("Choose a source folder"));
        statisticsView.setItems(statistics);
        statisticsView.setPlaceholder(new Label("Scan photos to calculate EXIF statistics."));
        destinationField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!settingDefaultDestination) {
                destinationIsDefault = false;
            }
            updateOrganizeButtonState();
        });
        updateOrganizeButtonState();
    }

    @FXML
    private void chooseSource() {
        final int[] result = new int[1];
        final java.io.File[][] selectedFiles = new java.io.File[1][];
        final Throwable[] chooserError = new Throwable[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("Choose source folders");
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setMultiSelectionEnabled(true);
                    if (!sourceFolders.isEmpty()) {
                        chooser.setCurrentDirectory(Paths.get(sourceFolders.get(0)).toFile());
                    }
                    result[0] = chooser.showOpenDialog(null);
                    selectedFiles[0] = chooser.getSelectedFiles();
                } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
                    chooserError[0] = ex;
                }
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        } catch (java.lang.reflect.InvocationTargetException ex) {
            chooserError[0] = ex.getCause();
        }
        if (chooserError[0] != null) {
            new Alert(Alert.AlertType.ERROR, "Could not open the native folder chooser: "
                    + chooserError[0].getMessage()).showAndWait();
            return;
        }

        if (result[0] == JFileChooser.APPROVE_OPTION && selectedFiles[0] != null) {
            boolean changed = false;
            for (java.io.File selected : selectedFiles[0]) {
                String path = selected.toPath().toAbsolutePath().normalize().toString();
                if (!sourceFolders.contains(path)) {
                    sourceFolders.add(path);
                    changed = true;
                }
            }
            if (changed) {
                sourceFoldersView.getSelectionModel().selectLast();
                setDefaultDestination();
                scan();
            }
        }
    }

    @FXML
    private void removeSource() {
        int selectedIndex = sourceFoldersView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            sourceFolders.remove(selectedIndex);
            setDefaultDestination();
            scan();
        }
    }

    @FXML
    private void chooseDestination() {
        chooseDirectory(destinationField, "Choose destination folder");
    }

    private void chooseDirectory(TextField field, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (!field.getText().trim().isEmpty()) {
            Path current = Paths.get(field.getText().trim());
            if (Files.isDirectory(current)) chooser.setInitialDirectory(current.toFile());
        }
        Window window = field.getScene().getWindow();
        java.io.File selected = chooser.showDialog(window);
        if (selected != null) {
            String selectedPath = selected.toPath().toAbsolutePath().normalize().toString();
            Platform.runLater(() -> {
                field.setText(selectedPath);
                field.positionCaret(field.getText().length());
                field.requestLayout();
                if (field == destinationField) {
                    updateTargets();
                }
            });
        }
    }

    @FXML
    private void sourceOptionsChanged() {
        if (!sourceFolders.isEmpty()) {
            scan();
        }
    }

    @FXML
    private void addStructureLevel(int index) {
        structureTokens.add(index + 1, "{Year}");
        rebuildStructureEditor();
        updateTargets();
    }

    private void removeStructureLevel(int index) {
        if (structureTokens.size() > 1) {
            structureTokens.remove(index);
            rebuildStructureEditor();
            updateTargets();
        }
    }

    private void rebuildStructureEditor() {
        folderStructureEditor.getChildren().clear();
        for (int index = 0; index < structureTokens.size(); index++) {
            final int level = index;
            ObservableList<String> filteredProperties = FXCollections.observableArrayList(availableProperties);
            ComboBox<String> property = new ComboBox<>(filteredProperties);
            property.setEditable(true);
            property.setVisibleRowCount(5);
            property.setMinHeight(34);
            property.setPrefWidth(260);
            property.setPromptText("Search properties");
            property.setMaxWidth(Double.MAX_VALUE);
            property.setValue(structureTokens.get(index));
            property.getEditor().textProperty().addListener((observable, oldValue, query) -> {
                filteredProperties.setAll(filteredPropertiesFor(query));
                if (property.isFocused() && query != null && !query.trim().isEmpty()
                        && !filteredProperties.isEmpty()) {
                    javafx.application.Platform.runLater(() -> {
                        if (!property.isShowing()) {
                            property.show();
                        }
                    });
                }
            });
            property.setOnAction(event -> {
                String value = property.getEditor().getText().trim();
                String selected = property.getSelectionModel().getSelectedItem();
                if (selected != null && filteredProperties.contains(selected)) {
                    value = selected;
                }
                if (availableProperties.contains(value)) {
                    structureTokens.set(level, value);
                    property.setValue(value);
                }
                updateTargets();
            });
            property.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused) {
                    property.hide();
                }
            });
            property.hide();

            Label levelLabel = new Label("Level " + (index + 1));
            levelLabel.setMinWidth(55);
            Button addButton = new Button("+");
            addButton.setMinWidth(34);
            addButton.setPrefHeight(34);
            addButton.getStyleClass().add("secondary-button");
            addButton.setOnAction(event -> addStructureLevel(level));
            Button removeButton = new Button("-");
            removeButton.setMinWidth(34);
            removeButton.setPrefHeight(34);
            removeButton.getStyleClass().add("secondary-button");
            removeButton.setDisable(structureTokens.size() == 1);
            removeButton.setOnAction(event -> removeStructureLevel(level));
            HBox buttons = new HBox(4, addButton, removeButton);
            buttons.setMinWidth(72);
            buttons.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            HBox row = new HBox(8, levelLabel, property, buttons);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            HBox.setHgrow(property, Priority.ALWAYS);
            folderStructureEditor.getChildren().add(row);
        }
    }

    @FXML
    private void scan() {
        scanCompleted = false;
        statistics.clear();
        updateOrganizeButtonState();
        if (sourceFolders.isEmpty()) {
            photos.clear();
            imageFormatSelection.getChildren().clear();
            updateTreePreview();
            setBusy(false, "Add at least one source folder.");
            return;
        }
        List<Path> sources = new ArrayList<>();
        for (String sourceFolder : sourceFolders) {
            Path source = validDirectory(sourceFolder, "Select valid source folders.");
            if (source == null) return;
            sources.add(source);
        }
        setBusy(true, "Scanning image files...");
        boolean includeSubfolders = includeSubfoldersCheckBox.isSelected();
        Task<ObservableList<PhotoFile>> task = new Task<ObservableList<PhotoFile>>() {
            @Override protected ObservableList<PhotoFile> call() throws Exception {
                ObservableList<PhotoFile> found = FXCollections.observableArrayList();
                List<Path> files = new ArrayList<>();
                for (Path source : sources) {
                    collectImageFiles(source, files, includeSubfolders);
                }
                Collections.sort(files);
                int total = files.size();
                long lastUiUpdate = 0L;
                updateMessage("Reading EXIF metadata... (0 of " + total + ")");
                for (int index = 0; index < total; index++) {
                    try {
                        Path path = files.get(index);
                        found.add(new PhotoFile(path, ExifReader.read(path)));
                    } catch (IOException | RuntimeException ex) {
                        Path path = files.get(index);
                        PhotoFile photo = new PhotoFile(path, new ExifData(
                                null, null, null, null, null, null, new java.util.LinkedHashMap<>()));
                        String detail = ex.getMessage() == null
                                ? ex.getClass().getSimpleName() : ex.getMessage();
                        photo.setStatus("Metadata error: " + detail);
                        found.add(photo);
                    }
                    int completed = index + 1;
                    long now = System.nanoTime();
                    if (completed == total || completed % 25 == 0
                            || now - lastUiUpdate >= 100_000_000L) {
                        updateProgress(completed, total);
                        updateMessage("Reading EXIF metadata... (" + completed + " of " + total + ")");
                        lastUiUpdate = now;
                    }
                }
                return found;
            }
        };
        task.messageProperty().addListener((observable, oldValue, newValue) -> statusLabel.setText(newValue));
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(event -> {
            unbindProgress(task);
            photos.setAll(task.getValue());
            updateImageFormatSelection();
            scanCompleted = true;
            updateAvailableProperties();
            updateTargets();
            updateStatistics();
            setBusy(false, photos.size() + " image file(s) found.");
        });
        task.setOnFailed(event -> {
            unbindProgress(task);
            Throwable failure = task.getException();
            String detail = failure == null || failure.getMessage() == null
                    ? "unknown error" : failure.getMessage();
            setBusy(false, "Scan failed: " + detail);
        });
        task.setOnCancelled(event -> {
            unbindProgress(task);
            setBusy(false, "Scan cancelled.");
        });
        new Thread(task, "exif-scan").start();
    }

    private void updateAvailableProperties() {
        java.util.Map<String, Integer> present = new java.util.HashMap<>();
        present.put("FileName", photos.size());
        for (PhotoFile photo : photos) {
            for (String name : photo.getMetadata().getProperties().keySet()) {
                String value = photo.getMetadata().getProperty(name);
                if (hasPropertyName(name) && hasPropertyValue(value)) {
                    present.merge(name, 1, Integer::sum);
                }
            }
        }
        List<String> sortedProperties = new ArrayList<>(present.keySet());
        sortedProperties.sort((left, right) -> {
            int countComparison = Integer.compare(present.get(right), present.get(left));
            return countComparison != 0 ? countComparison : left.compareToIgnoreCase(right);
        });
        List<String> displayProperties = new ArrayList<>();
        for (String name : sortedProperties) {
            displayProperties.add("{" + name + "}");
        }
        availableProperties.setAll(displayProperties);
        if (!availableProperties.isEmpty()) {
            for (int index = 0; index < structureTokens.size(); index++) {
                if (!availableProperties.contains(structureTokens.get(index))) {
                    structureTokens.set(index, availableProperties.get(0));
                }
            }
        }
        rebuildStructureEditor();
    }

    private List<String> filteredPropertiesFor(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String property : availableProperties) {
            if (normalized.isEmpty() || property.toLowerCase(Locale.ROOT).contains(normalized)) {
                result.add(property);
            }
        }
        return result;
    }

    private boolean hasPropertyValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.contains("unknown")
                && !normalized.contains("no gps location")
                && !normalized.contains("unavailable")
                && !normalized.contains("invalid gps location")
                && !normalized.equals("n/a")
                && !normalized.equals("na")
                && !normalized.contains("not available")
                && !normalized.equals("null")
                && !normalized.equals("undefined")
                && !normalized.equals("none");
    }

    private boolean hasPropertyName(String name) {
        return name != null
                && !name.trim().isEmpty()
                && !name.toLowerCase(Locale.ROOT).contains("unknown");
    }

    @FXML
    private void updatePreview() {
        updateTargets();
    }

    private void updateTargets() {
        Path destination = destinationPath();
        String template = String.join("\\", structureTokens);
        for (PhotoFile photo : photos) {
            String relativeTarget = PathTemplate.resolve(template, photo);
            Path targetPath = Paths.get(relativeTarget);
            if (targetPath.getFileName() == null
                    || !targetPath.getFileName().toString().equals(photo.getFileName())) {
                targetPath = targetPath.resolve(photo.getFileName());
            }
            photo.setTarget(destination == null
                    ? targetPath.toString()
                    : destination.resolve(targetPath).normalize().toString());
            if (!photo.getStatus().startsWith("Metadata error")) photo.setStatus("Ready");
        }
        updateTreePreview();
    }

    private void updateStatistics() {
        int total = photos.size();
        Map<String, Integer> occurrences = new HashMap<>();
        for (PhotoFile photo : photos) {
            for (Map.Entry<String, String> property : photo.getMetadata().getProperties().entrySet()) {
                if (hasPropertyName(property.getKey()) && hasPropertyValue(property.getValue())) {
                    occurrences.merge(property.getKey(), 1, Integer::sum);
                }
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(occurrences.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        List<String> values = new ArrayList<>();
        values.add("Photos scanned: " + total);
        for (Map.Entry<String, Integer> property : sorted) {
            values.add(property.getKey() + ": " + property.getValue() + " / " + total);
        }
        statistics.setAll(values);
    }

    private void updateImageFormatSelection() {
        imageFormatSelection.getChildren().clear();
        Map<String, CheckBox> formats = new LinkedHashMap<>();
        for (PhotoFile photo : photos) {
            String format = fileExtension(photo.getSource());
            if (!formats.containsKey(format)) {
                CheckBox checkBox = new CheckBox(format.toUpperCase(Locale.ROOT));
                checkBox.setSelected(true);
                formats.put(format, checkBox);
                imageFormatSelection.getChildren().add(checkBox);
            }
        }
        imageFormatCheckBoxes = formats;
    }

    private List<PhotoFile> selectedPhotos() {
        List<PhotoFile> selected = new ArrayList<>();
        for (PhotoFile photo : photos) {
            CheckBox checkBox = imageFormatCheckBoxes.get(fileExtension(photo.getSource()));
            if (checkBox != null && checkBox.isSelected()) {
                selected.add(photo);
            }
        }
        return selected;
    }

    private static String fileExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void setDefaultDestination() {
        if (!sourceFolders.isEmpty() && !destinationField.getText().trim().isEmpty()
                && !destinationIsDefault) {
            return;
        }
        if (sourceFolders.isEmpty()) {
            if (destinationIsDefault) {
                settingDefaultDestination = true;
                destinationField.clear();
                settingDefaultDestination = false;
                destinationIsDefault = false;
            }
            return;
        }
        Path commonAncestor = commonAncestor();
        if (commonAncestor != null) {
            settingDefaultDestination = true;
            destinationField.setText(commonAncestor.toString());
            settingDefaultDestination = false;
            destinationIsDefault = true;
        }
    }

    private Path commonAncestor() {
        Path ancestor = Paths.get(sourceFolders.get(0)).toAbsolutePath().normalize();
        for (int index = 1; index < sourceFolders.size(); index++) {
            Path other = Paths.get(sourceFolders.get(index)).toAbsolutePath().normalize();
            int sharedNames = Math.min(ancestor.getNameCount(), other.getNameCount());
            int matchingNames = 0;
            while (matchingNames < sharedNames
                    && ancestor.getName(matchingNames).equals(other.getName(matchingNames))) {
                matchingNames++;
            }
            if (matchingNames == 0 || !ancestor.getRoot().equals(other.getRoot())) {
                return ancestor.getRoot();
            }
            ancestor = ancestor.getRoot().resolve(ancestor.subpath(0, matchingNames));
        }
        return ancestor;
    }

    @FXML
    private void organize() {
        Path destination = validDirectory(destinationField.getText(), "Select a valid destination folder.");
        if (destination == null || photos.isEmpty()) return;
        List<PhotoFile> selected = selectedPhotos();
        if (selected.isEmpty()) {
            setBusy(false, "Select at least one image format to organize.");
            return;
        }
        updateTargets();
        setBusy(true, "Organizing files...");
        Task<Integer> task = new Task<Integer>() {
            @Override protected Integer call() throws Exception {
                int completed = 0;
                for (PhotoFile photo : selected) {
                    updateMessage("Organizing files... " + photo.getFileName());
                    Path requestedTarget = Paths.get(photo.getTarget()).isAbsolute()
                            ? Paths.get(photo.getTarget())
                            : destination.resolve(photo.getTarget());
                    boolean moving = moveCheckBox.isSelected();
                    Path target = moving ? uniqueTarget(requestedTarget) : requestedTarget;
                    Files.createDirectories(target.getParent());
                    if (moving) {
                        Files.move(photo.getSource(), target);
                        photo.setStatus("Moved to " + target);
                    } else {
                        try {
                            Files.copy(photo.getSource(), target, StandardCopyOption.COPY_ATTRIBUTES);
                            photo.setStatus("Copied to " + target);
                        } catch (FileAlreadyExistsException ex) {
                            photo.setStatus("Skipped (already exists): " + target);
                        }
                    }
                    completed++;
                    updateProgress(completed, selected.size());
                }
                return completed;
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnFailed(event -> {
            unbindProgress(task);
            Throwable failure = task.getException();
            String detail = failure == null || failure.getMessage() == null
                    ? "unknown error" : failure.getMessage();
            setBusy(false, "Organization failed: " + detail);
        });
        task.setOnCancelled(event -> {
            unbindProgress(task);
            setBusy(false, "Organization cancelled.");
        });
        task.setOnSucceeded(event -> {
            unbindProgress(task);
            setBusy(false, task.getValue() + " selected file(s) organized.");
        });
        new Thread(task, "exif-organize").start();
    }

    private Path uniqueTarget(Path target) {
        if (!Files.exists(target)) return target;
        String name = target.getFileName().toString();
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        int index = 1;
        Path candidate;
        do {
            candidate = target.resolveSibling(base + "_" + index++ + extension);
        } while (Files.exists(candidate));
        return candidate;
    }

    private static boolean isSupportedImage(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 0) {
            return false;
        }
        return SUPPORTED_IMAGE_EXTENSIONS.contains(fileName.substring(extensionStart + 1));
    }

    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of(
            "3fr", "arw", "cr2", "cr3", "dcr", "dng", "erf", "fff", "iiq", "k25",
            "kdc", "mef", "mos", "mrw", "nef", "nrw", "orf", "pef", "raf", "raw",
            "rw2", "rwl", "sr2", "srf", "srw", "x3f",
            "jpg", "jpeg", "jpe", "png", "gif", "bmp", "tif", "tiff", "webp", "heic");

    private static void collectImageFiles(Path directory, List<Path> files, boolean includeSubfolders)
            throws IOException {
        DirectoryStream<Path> entries = Files.newDirectoryStream(directory);
        try {
            for (Path entry : entries) {
                if (includeSubfolders && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    collectImageFiles(entry, files, true);
                } else if (Files.isRegularFile(entry) && isSupportedImage(entry)) {
                    files.add(entry);
                }
            }
        } finally {
            entries.close();
        }
    }

    private Path validDirectory(String value, String message) {
        if (value == null || value.trim().isEmpty() || !Files.isDirectory(Paths.get(value.trim()))) {
            new Alert(Alert.AlertType.WARNING, message).showAndWait();
            return null;
        }
        return Paths.get(value.trim());
    }

    private Path destinationPath() {
        String value = destinationField.getText().trim();
        if (value.isEmpty()) {
            return null;
        }
        Path destination = Paths.get(value);
        return Files.isDirectory(destination) ? destination.toAbsolutePath().normalize() : null;
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        scanButton.setDisable(busy);
        updateOrganizeButtonState();
        progressBar.setVisible(busy);
        if (!busy) {
            progressBar.setProgress(0);
        }
        statusLabel.setText(message);
    }

    private void updateOrganizeButtonState() {
        if (organizeButton != null) {
            organizeButton.setDisable(busy || !scanCompleted || destinationField.getText().trim().isEmpty());
        }
    }

    private void updateTreePreview() {
        Path destination = destinationPath();
        String rootLabel = destination == null ? "Target preview" : destination.toString();
        TreeItem<String> root = new TreeItem<>(rootLabel);
        root.setExpanded(true);

        for (PhotoFile photo : photos) {
            Path target = Paths.get(photo.getTarget());
            Path relative = destination != null && target.isAbsolute()
                    ? destination.relativize(target) : target;
            addTreePath(root, relative);
        }
        previewTree.setRoot(root);
    }

    private void addTreePath(TreeItem<String> root, Path path) {
        if (path.getNameCount() == 0) {
            return;
        }
        TreeItem<String> parent = root;
        for (int index = 0; index < path.getNameCount() - 1; index++) {
            String name = path.getName(index).toString();
            TreeItem<String> child = null;
            for (TreeItem<String> candidate : parent.getChildren()) {
                if (candidate.getValue().equals(name)) {
                    child = candidate;
                    break;
                }
            }
            if (child == null) {
                child = new TreeItem<>(name);
                parent.getChildren().add(child);
            }
            parent = child;
            parent.setExpanded(true);
        }

        String fileName = path.getFileName().toString();
        int visibleFiles = 0;
        TreeItem<String> summary = null;
        for (TreeItem<String> child : parent.getChildren()) {
            if (child.getValue().startsWith("+") && child.getValue().endsWith(" more files")) {
                summary = child;
            } else if (child.getChildren().isEmpty()) {
                visibleFiles++;
            }
        }
        if (visibleFiles < 5) {
            parent.getChildren().add(new TreeItem<>(fileName));
        } else {
            if (summary == null) {
                summary = new TreeItem<>("+1 more files");
                parent.getChildren().add(summary);
            } else {
                int hidden = Integer.parseInt(summary.getValue().substring(1,
                        summary.getValue().indexOf(" more files")));
                summary.setValue("+" + (hidden + 1) + " more files");
            }
        }
    }

    private void addSourceFolder(Path folder) {
        String path = folder.toAbsolutePath().normalize().toString();
        if (!sourceFolders.contains(path)) {
            sourceFolders.add(path);
            sourceFoldersView.getSelectionModel().select(path);
            setDefaultDestination();
            scan();
        }
    }

    private void unbindProgress(Task<?> task) {
        if (progressBar.progressProperty().isBound()) {
            progressBar.progressProperty().unbind();
        }
        progressBar.setProgress(0);
    }
}
