package tourrouteplanner.controller;

import com.teamdev.jxbrowser.js.JsAccessible;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;
import tourrouteplanner.service.RoutingService;
import tourrouteplanner.service.GeocodingService;
import tourrouteplanner.service.IRoutingService;
import tourrouteplanner.service.IGeocodingService;
import tourrouteplanner.service.IStorageService;
import tourrouteplanner.service.StorageService;
import tourrouteplanner.util.Utils;
import tourrouteplanner.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main controller for the Tour Route Planner application.
 * Acts as coordinator delegating to helper classes for specific functionality:
 * - MapHelper: Map display and interactions
 * - SearchHelper: Place search and suggestions
 * - RouteHelper: Route table management
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // ==================== FXML UI Components ====================

    @FXML
    private TextField searchBox;
    @FXML
    private ListView<Place> placeListView;
    @FXML
    private ListView<String> suggestionsListView;
    @FXML
    private TableView<Place> routeTableView;
    @FXML
    private TableColumn<Place, String> routePlaceNameColumn;
    @FXML
    private TableColumn<Place, String> routePlaceAddressColumn;
    @FXML
    private Button removeSelectedButton;
    @FXML
    private Button findRouteButton;
    @FXML
    private Button saveRouteButton;
    @FXML
    private Button loadRouteButton;
    @FXML
    private Button moveUpButton;
    @FXML
    private Button moveDownButton;
    @FXML
    private Button clearAllButton;
    @FXML
    private Button darkModeToggle;
    @FXML
    private Label searchPlaceholder;
    @FXML
    private Label routePlaceholder;
    @FXML
    private ProgressIndicator loadingSpinner;
    @FXML
    private HBox loadingContainer;
    @FXML
    private StackPane mapPane;
    @FXML
    private BorderPane mapAndControlsPane;
    @FXML
    private ScrollPane dynamicRouteInfoScrollPane;
    @FXML
    private TextArea dynamicRouteInfoTextArea;
    @FXML
    private Label statusLabel;

    // ==================== Services ====================

    private IRoutingService routingService;
    private IGeocodingService geocodingService;
    private IStorageService storageService;

    // ==================== Helper Classes ====================

    private MapHelper mapHelper;
    private SearchHelper searchHelper;
    private RouteHelper routeHelper;

    // ==================== State ====================

    private boolean isDarkMode = false;

    // ==================== Initialization ====================

    @FXML
    public void initialize() {
        // Initialize services
        routingService = new RoutingService();
        geocodingService = new GeocodingService();
        storageService = new StorageService();

        // Initialize helpers (order matters: routeHelper first for searchHelper
        // callback)
        initializeRouteHelper();
        initializeMapHelper();
        initializeSearchHelper();

        // Setup dark mode button
        setDarkModeButtonIcon(false);
        Tooltip.install(darkModeToggle, new Tooltip("Switch to dark mode"));

        // Setup route info area
        setupRouteInfoArea();

        // Setup placeholder listeners
        setupPlaceholderListeners();

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        // Initial placeholder state
        updateRoutePlaceholderVisibility();
        updateSearchPlaceholderVisibility();
    }

    private void initializeMapHelper() {
        mapHelper = new MapHelper(mapPane, this);
        mapHelper.initialize();
    }

    private void initializeSearchHelper() {
        searchHelper = new SearchHelper(geocodingService, searchBox, placeListView,
                suggestionsListView, statusLabel, this::setLoading);

        // Setup callbacks
        searchHelper.setOnPlaceAdd(place -> {
            if (routeHelper.addPlace(place)) {
                mapHelper.addMarker(place.getName(), place.getLatitude(),
                        place.getLongitude(), place.getAddress());
                placeListView.refresh();
            }
        });

        searchHelper.setOnPlaceSelect(place -> {
            mapHelper.showPlace(place);
            statusLabel.setText("Selected: " + place.getName());
        });

        // Setup UI components
        searchHelper.setupPlaceListView(routeHelper.getCurrentRoutePlaces());
        searchHelper.setupSuggestionsListView();
        searchHelper.setupSearchBoxListener();
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            loadingContainer.setVisible(loading);
            loadingContainer.setManaged(loading);
        });
    }

    private void initializeRouteHelper() {
        routeHelper = new RouteHelper(routingService, routeTableView,
                routePlaceNameColumn, routePlaceAddressColumn, statusLabel, this::setLoading);

        // Setup callbacks
        routeHelper.setOnRouteUpdate(route -> {
            if (route != null) {
                mapHelper.drawRoute(route.getCoordinates());
                mapHelper.fitToRoute();
                updateDynamicRouteInfo(
                        String.format(Locale.US, "Total distance: %.2f km", route.getTotalDistanceKm()),
                        route.getTurnByTurnInstructions());
            } else {
                mapHelper.clearRoute();
                updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), "");
            }
        });

        routeHelper.setOnMapRefresh(places -> mapHelper.refreshMarkers(places));

        routeHelper.setOnPlaceSelect(place -> {
            mapHelper.showPlace(place);
            statusLabel.setText("Moved to: " + place.getName());
        });

        routeHelper.setupRouteTableView();
    }

    private void setupRouteInfoArea() {
        if (dynamicRouteInfoScrollPane != null) {
            dynamicRouteInfoScrollPane.setVisible(false);
            dynamicRouteInfoScrollPane.setManaged(false);
            dynamicRouteInfoScrollPane.setPrefHeight(150);
            dynamicRouteInfoScrollPane.setMinHeight(100);
        }
        if (dynamicRouteInfoTextArea != null) {
            dynamicRouteInfoTextArea.setEditable(false);
            dynamicRouteInfoTextArea.setWrapText(true);
        }
    }

    private void setupPlaceholderListeners() {
        searchHelper.getSearchResults().addListener(
                (javafx.collections.ListChangeListener.Change<? extends Place> c) -> {
                    updateSearchPlaceholderVisibility();
                    if (searchHelper.getSearchResults().isEmpty()) {
                        placeListView.getSelectionModel().clearSelection();
                    }
                });

        routeHelper.getCurrentRoutePlaces().addListener(
                (javafx.collections.ListChangeListener.Change<? extends Place> c) -> {
                    updateRoutePlaceholderVisibility();
                });
    }

    private void setupKeyboardShortcuts() {
        // Keyboard shortcuts require scene to be initialized
        Platform.runLater(() -> {
            if (mapPane.getScene() != null) {
                mapPane.getScene().setOnKeyPressed(event -> {
                    if (event.isControlDown()) {
                        switch (event.getCode()) {
                            case S -> handleSaveRoute(); // Ctrl+S: Save
                            case O -> handleLoadRoute(); // Ctrl+O: Open/Load
                            case F -> searchBox.requestFocus(); // Ctrl+F: Focus search
                            default -> {
                            }
                        }
                    } else {
                        switch (event.getCode()) {
                            case DELETE -> handleRemoveSelected(); // Delete key
                            case ESCAPE -> {
                                mapHelper.clearHighlight();
                                searchBox.clear();
                            }
                            default -> {
                            }
                        }
                    }
                });
                log.debug("Keyboard shortcuts initialized");
            }
        });
    }

    // ==================== FXML Event Handlers ====================

    @FXML
    private void handleSearch() {
        searchHelper.handleSearch(searchBox.getText());
    }

    @FXML
    private void handleFindRoute() {
        routeHelper.findRoute();
    }

    @FXML
    private void handleRemoveSelected() {
        routeHelper.removeSelected();
        updateRoutePlaceholderVisibility();
    }

    @FXML
    private void handleClearAllPlaces() {
        routeHelper.clearAll();
        mapHelper.clearAllMarkers();
        mapHelper.clearRoute();
        mapHelper.clearHighlight();
        updateRoutePlaceholderVisibility();
    }

    @FXML
    private void handleMoveUp() {
        routeHelper.moveUp();
    }

    @FXML
    private void handleMoveDown() {
        routeHelper.moveDown();
    }

    @FXML
    private void handleSaveRoute() {
        if (routeHelper.getCurrentRoutePlaces().isEmpty()) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice", "No route to save.");
            return;
        }
        File file = storageService.showSaveFileDialog(mapPane.getScene().getWindow());
        if (file != null) {
            storageService.saveRoute(file, new ArrayList<>(routeHelper.getCurrentRoutePlaces()),
                    routeHelper.getLastRoute());
            statusLabel.setText("Route saved successfully.");
        }
    }

    @FXML
    private void handleLoadRoute() {
        File file = storageService.showOpenFileDialog(mapPane.getScene().getWindow());
        if (file != null) {
            mapHelper.clearHighlight();
            StorageService.LoadedRouteData loadedData = storageService.loadRoute(file);

            if (loadedData != null && loadedData.getPlaces() != null) {
                routeHelper.setPlaces(loadedData.getPlaces());
                mapHelper.refreshMarkers(loadedData.getPlaces());

                Route loadedRoute = loadedData.getRoute();
                if (loadedRoute != null && loadedRoute.getCoordinates() != null
                        && !loadedRoute.getCoordinates().isEmpty()) {
                    mapHelper.drawRoute(loadedRoute.getCoordinates());
                    updateDynamicRouteInfo(
                            String.format(Locale.US, "Total distance: %.2f km", loadedRoute.getTotalDistanceKm()),
                            loadedRoute.getTurnByTurnInstructions());
                    routeHelper.setRouteCalculated(true);
                } else if (routeHelper.getCurrentRoutePlaces().size() >= 2) {
                    routeHelper.findRoute();
                } else {
                    mapHelper.clearRoute();
                    updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), null);
                }
                statusLabel.setText("Route loaded successfully.");
            } else {
                Utils.showAlert(Alert.AlertType.ERROR, "Route Loading Error",
                        "Could not load route data from selected file.");
            }
        }
    }

    @FXML
    private void onExit() {
        shutdownJxBrowser();
        Platform.exit();
    }

    @FXML
    private void handleAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About " + Constants.APP_NAME);
        about.setHeaderText(Constants.APP_NAME + " v" + Constants.APP_VERSION);
        about.setContentText("A tour route planning application with interactive maps.\n\n" +
                "Technologies: JavaFX, JxBrowser, OpenLayers, OSRM, Nominatim\n\n" +
                "GitHub: " + Constants.GITHUB_URL);
        about.showAndWait();
    }

    @FXML
    private void toggleDarkMode() {
        isDarkMode = !isDarkMode;

        if (placeListView.getScene() != null && placeListView.getScene().getRoot() != null) {
            if (isDarkMode) {
                placeListView.getScene().getRoot().getStyleClass().add("dark-mode");
                statusLabel.setText("🌙 Switched to dark mode");
                setDarkModeButtonIcon(true);
                Tooltip.install(darkModeToggle, new Tooltip("Switch to light mode"));
            } else {
                placeListView.getScene().getRoot().getStyleClass().remove("dark-mode");
                statusLabel.setText("☀️ Switched to light mode");
                setDarkModeButtonIcon(false);
                Tooltip.install(darkModeToggle, new Tooltip("Switch to dark mode"));
            }
        }
    }

    // ==================== JavaScript Callbacks (from map.html)
    // ====================

    @JsAccessible
    public void handleMapClick(double lat, double lng) {
        Platform.runLater(() -> {
            try {
                Place clickedPlace = geocodingService.reverseGeocode(lat, lng);
                if (clickedPlace != null) {
                    Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmDialog.setTitle("Add Place");
                    confirmDialog.setHeaderText("Add place from map?");
                    confirmDialog.setContentText("Do you want to add \"" + clickedPlace.getName() + "\" to the route?");

                    Optional<ButtonType> result = confirmDialog.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        if (routeHelper.addPlace(clickedPlace)) {
                            mapHelper.addMarker(clickedPlace.getName(), clickedPlace.getLatitude(),
                                    clickedPlace.getLongitude(), clickedPlace.getAddress());
                        }
                    }
                } else {
                    Utils.showAlert(Alert.AlertType.INFORMATION, "Notice",
                            "Could not find information for clicked location.");
                }
            } catch (IOException e) {
                log.error("Error in reverse geocoding: {}", e.getMessage(), e);
                Utils.showAlert(Alert.AlertType.ERROR, "Geocoding Error",
                        "Error getting address: " + e.getMessage());
            }
        });
    }

    @JsAccessible
    public void logFromJs(String message) {
        log.debug("[JS] {}", message);
    }

    // ==================== UI Helper Methods ====================

    private void updateDynamicRouteInfo(String totalDistanceText, String turnByTurnInstructions) {
        if (dynamicRouteInfoScrollPane != null && dynamicRouteInfoTextArea != null) {
            StringBuilder infoBuilder = new StringBuilder();
            infoBuilder.append(totalDistanceText);

            if (turnByTurnInstructions != null && !turnByTurnInstructions.trim().isEmpty()) {
                infoBuilder.append("\n\nDetailed directions:\n");
                infoBuilder.append(turnByTurnInstructions);
            }

            dynamicRouteInfoTextArea.setText(infoBuilder.toString());

            boolean hasContent = totalDistanceText != null && !totalDistanceText.trim().isEmpty();
            dynamicRouteInfoScrollPane.setVisible(hasContent);
            dynamicRouteInfoScrollPane.setManaged(hasContent);
        }
    }

    private void setDarkModeButtonIcon(boolean isDarkMode) {
        try {
            String iconName = isDarkMode ? "sun.png" : "moon.png";
            Image image = new Image(getClass().getResourceAsStream("/tourrouteplanner/icons/" + iconName));
            ImageView imageView = new ImageView(image);
            imageView.setFitHeight(20);
            imageView.setFitWidth(20);
            darkModeToggle.setGraphic(imageView);
            darkModeToggle.setText("");
        } catch (Exception e) {
            darkModeToggle.setText(isDarkMode ? "☀️" : "🌙");
        }
    }

    private void updateRoutePlaceholderVisibility() {
        if (routePlaceholder != null) {
            boolean empty = routeHelper.getCurrentRoutePlaces().isEmpty();
            routePlaceholder.setVisible(empty);
        }
    }

    private void updateSearchPlaceholderVisibility() {
        if (searchPlaceholder != null) {
            boolean hasResults = !searchHelper.getSearchResults().isEmpty();
            searchPlaceholder.setVisible(!hasResults);
            searchPlaceholder.setManaged(!hasResults);
        }
    }

    // ==================== Lifecycle ====================

    public void shutdownJxBrowser() {
        if (searchHelper != null) {
            searchHelper.shutdown();
        }
        if (mapHelper != null) {
            mapHelper.shutdown();
        }
    }
}
