package tourrouteplanner;

import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.engine.RenderingMode;
import com.teamdev.jxbrowser.frame.Frame;
import com.teamdev.jxbrowser.js.JsAccessible;
import com.teamdev.jxbrowser.js.JsObject;
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived;
import com.teamdev.jxbrowser.navigation.event.LoadFinished;
import com.teamdev.jxbrowser.view.javafx.BrowserView;
import com.teamdev.jxbrowser.browser.callback.InjectJsCallback;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
import tourrouteplanner.util.Messages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Main controller for the Tour Route Planner application user interface.
 * Manages user interactions, map display, place search,
 * route planning and saving/loading routes.
 */
public class MainController {

    @FXML
    private TextField searchBox; // Input field for place search.
    @FXML
    private ListView<Place> placeListView; // List view displaying search results.
    @FXML
    private ListView<String> suggestionsListView; // List view displaying search suggestions.
    @FXML
    private TableView<Place> routeTableView; // Table displaying selected places in the current route.
    @FXML
    private TableColumn<Place, String> routePlaceNameColumn; // Place name column in route table.
    @FXML
    private TableColumn<Place, String> routePlaceAddressColumn; // Address column in route table.
    @FXML
    private Button removeSelectedButton; // Button to remove selected place from route table.
    @FXML
    private Button findRouteButton; // Button to find and display route between selected places.
    @FXML
    private Button saveRouteButton; // Button to save current route to file.
    @FXML
    private Button loadRouteButton; // Button to load route from file.
    @FXML
    private Button moveUpButton; // Up arrow button to move place up
    @FXML
    private Button moveDownButton; // Down arrow button to move place down
    @FXML
    private Button clearAllButton; // Button to clear all places
    @FXML
    private Button darkModeToggle; // Dark mode toggle button
    @FXML
    private Label searchPlaceholder; // Placeholder label for search results.
    @FXML
    private Label routePlaceholder; // Placeholder label for route list.
    @FXML
    private ProgressIndicator loadingSpinner; // Loading spinner
    @FXML
    private javafx.scene.layout.HBox loadingContainer; // Container for loading animation

    @FXML
    private StackPane mapPane; // Container for JxBrowser map.

    @FXML
    private BorderPane mapAndControlsPane;
    @FXML
    private ScrollPane dynamicRouteInfoScrollPane;
    @FXML
    private TextArea dynamicRouteInfoTextArea;

    // Observable list of search result places for UI updates.
    private ObservableList<Place> searchResults = FXCollections.observableArrayList();
    // Observable list of places in current route for UI updates.
    private ObservableList<Place> currentRoutePlaces = FXCollections.observableArrayList();
    // Service handling routing logic (OSRM API).
    private IRoutingService routingService;
    // Service handling geocoding logic (Nominatim API).
    private IGeocodingService geocodingService;
    // Service handling route save and load logic.
    private IStorageService storageService;
    // Timer for debouncing search suggestions
    private ScheduledExecutorService suggestionsScheduler;
    private final ObservableList<String> searchSuggestions = FXCollections.observableArrayList();

    // Dark mode state
    private boolean isDarkMode = false;

    // JxBrowser components for map display.
    private Engine engine;
    private Browser browser;
    private BrowserView browserView;

    @FXML
    private Label statusLabel; // Label displaying status messages to user.

    // Track whether route has been calculated
    private boolean routeCalculated = false;

    /**
     * Default constructor for MainController.
     * Called when FXML is loaded.
     */
    public MainController() {
        // Initial setup can be done here if needed,
        // but most UI initialization logic should be in initialize() method.
    }

    /**
     * Initializes the controller after FXML fields have been injected.
     * Sets up services, lists, tables, and JxBrowser.
     */
    @FXML
    public void initialize() {
        // Initialize services with new separated implementations.
        routingService = new RoutingService();
        geocodingService = new GeocodingService();
        storageService = new StorageService();
        suggestionsScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true); // Allow application to exit even if this thread is running
            return thread;
        });

        // Set up icon and tooltip for Dark Mode button
        setDarkModeButtonIcon(false); // false = light mode (default)
        Tooltip.install(darkModeToggle, new Tooltip("Switch to dark mode"));

        // Set up ListView for place search results.
        placeListView.setItems(searchResults);
        placeListView.setCellFactory(param -> new ListCell<Place>() {
            private Button addButton;
            private HBox hbox;
            private VBox vbox;
            private Label nameLabel;
            private Label addressLabel;
            private final Tooltip tooltip = new Tooltip();

            { // Instance initializer block for the ListCell
              // Create UI components
                addButton = new Button("Add");
                addButton.getStyleClass().add("place-add-button");
                addButton.setOnAction(event -> {
                    Place place = getItem();
                    if (place != null && !currentRoutePlaces.contains(place)) {
                        currentRoutePlaces.add(place);
                        addMapMarker(place.getName(), place.getLatitude(), place.getLongitude(), place.getAddress());
                        statusLabel.setText("Added: " + place.getName());
                        // Refresh the ListView to update button states
                        placeListView.refresh();
                    }
                });

                nameLabel = new Label();
                nameLabel.getStyleClass().add("place-name");
                nameLabel.setWrapText(true);

                addressLabel = new Label();
                addressLabel.getStyleClass().add("place-address");
                addressLabel.setWrapText(true);

                vbox = new VBox(2);
                vbox.getChildren().addAll(nameLabel, addressLabel);
                vbox.setMaxWidth(Double.MAX_VALUE);

                hbox = new HBox(10);
                hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                hbox.getChildren().addAll(vbox, addButton);

                // Make vbox grow to fill available space
                javafx.scene.layout.HBox.setHgrow(vbox, javafx.scene.layout.Priority.ALWAYS);
            }

            @Override
            protected void updateItem(Place item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.getName());
                    String address = item.getAddress();
                    addressLabel.setText(address != null ? address : "");

                    // Disable button if place is already in route
                    addButton.setDisable(currentRoutePlaces.contains(item));

                    setGraphic(hbox);
                    setText(null);

                    tooltip.setText(item.getName() + "\n" + (address != null ? address : ""));
                    setTooltip(tooltip);
                }
            }
        });

        // Add listener to pan map when a place is selected in placeListView.
        placeListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String geoJson = newSelection.getGeoJson();
                double[] boundingBox = newSelection.getBoundingBox();

                if (geoJson != null && !geoJson.trim().isEmpty()) {
                    // Prioritize GeoJSON highlight if available
                    highlightGeoJsonOnMap(geoJson);
                    // Still zoom to bounding box if available, since GeoJSON might be a point or
                    // small area
                    if (boundingBox != null && boundingBox.length == 4) {
                        zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                    } else {
                        // If no bounding box, pan to center point with default zoom level
                        panTo(newSelection.getLatitude(), newSelection.getLongitude(), 15);
                    }
                } else if (boundingBox != null && boundingBox.length == 4) {
                    // If no GeoJSON but has bounding box, highlight bounding box
                    highlightBoundingBoxOnMap(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                    zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                } else {
                    // If neither GeoJSON nor bounding box, pan to center point
                    panTo(newSelection.getLatitude(), newSelection.getLongitude(), 15);
                    clearMapHighlight(); // Clear old highlight
                }
            } else {
                clearMapHighlight(); // Clear highlight if no place selected
            }
        });

        // Configure TableView for selected places in route.
        routePlaceNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        routePlaceAddressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        routeTableView.setItems(currentRoutePlaces);

        // Add listener to enable/disable control buttons based on selection
        routeTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            if (removeSelectedButton != null) {
                removeSelectedButton.setDisable(!hasSelection);
            }

            if (hasSelection && moveUpButton != null && moveDownButton != null) {
                int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
                moveUpButton.setDisable(selectedIndex <= 0); // Disable if the selected item is the first
                moveDownButton.setDisable(selectedIndex >= currentRoutePlaces.size() - 1); // Disable if the selected
                                                                                           // item is the last
            } else if (moveUpButton != null && moveDownButton != null) {
                moveUpButton.setDisable(true);
                moveDownButton.setDisable(true);
            }

            // Force UI refresh to reflect button state changes
            Platform.runLater(() -> {
                if (moveUpButton != null && moveDownButton != null) {
                    moveUpButton.setOpacity(moveUpButton.isDisable() ? 0.5 : 1.0);
                    moveDownButton.setOpacity(moveDownButton.isDisable() ? 0.5 : 1.0);
                }
            });

            // Update placeholder visibility
            updateRoutePlaceholderVisibility();
        });

        // Set up column width configuration to ensure name column doesn't take too much
        // space
        routePlaceNameColumn.setMinWidth(100);
        routePlaceNameColumn.setPrefWidth(180); // Default value, will be adjusted based on content
        routePlaceNameColumn.setMaxWidth(250); // Maximum size limit

        // Address column will be more flexible and stretch to fill remaining space
        routePlaceAddressColumn.setMinWidth(200);

        // Use TableView column resize policy (set only once)
        routeTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // Listen for data changes to auto-adjust name column width
        currentRoutePlaces.addListener((javafx.collections.ListChangeListener.Change<? extends Place> c) -> {
            boolean hasPlaces = !currentRoutePlaces.isEmpty();
            boolean hasEnoughPlaces = currentRoutePlaces.size() >= 2;

            // Enable/disable buttons based on number of places
            if (clearAllButton != null) {
                clearAllButton.setDisable(!hasPlaces);
            }
            if (findRouteButton != null) {
                findRouteButton.setDisable(!hasEnoughPlaces);
            }

            // Update move button state based on selection and position
            Place selectedPlace = routeTableView.getSelectionModel().getSelectedItem();
            if (selectedPlace != null && hasPlaces && moveUpButton != null && moveDownButton != null) {
                int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
                moveUpButton.setDisable(selectedIndex <= 0);
                moveDownButton.setDisable(selectedIndex >= currentRoutePlaces.size() - 1);
            } else if (moveUpButton != null && moveDownButton != null) {
                // If no place selected or no places in list
                moveUpButton.setDisable(true);
                moveDownButton.setDisable(true);
            }

            // Update placeholder visibility when list changes
            updateRoutePlaceholderVisibility();

            if (!currentRoutePlaces.isEmpty()) {
                Platform.runLater(() -> {
                    // Calculate optimal width for name column based on current content
                    double prefWidth = computePrefColumnWidth(routePlaceNameColumn, currentRoutePlaces);
                    if (prefWidth > 0) {
                        // Set width for name column within defined limits
                        routePlaceNameColumn.setPrefWidth(Math.max(100, Math.min(prefWidth + 10, 250)));
                    }
                });
            }
        });

        // Add listener for TableView size to adjust relatively when window resizes
        routeTableView.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) {
                double tableWidth = newVal.doubleValue();
                routePlaceNameColumn.setMaxWidth(tableWidth * 0.4); // Take up to 40% of table width
            }
        });

        // Simple CellFactory to display text with wrap
        routePlaceNameColumn.setCellFactory(tc -> {
            TableCell<Place, String> cell = new TableCell<Place, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                    }
                    setWrapText(true);
                }
            };
            return cell;
        });

        routePlaceAddressColumn.setCellFactory(tc -> {
            TableCell<Place, String> cell = new TableCell<Place, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                    }
                    setWrapText(true);
                }
            };
            return cell;
        });

        // Initialize suggestions ListView
        suggestionsListView.setItems(searchSuggestions);

        // Handle when user clicks on a suggestion
        suggestionsListView.setOnMouseClicked(event -> {
            String selectedSuggestion = suggestionsListView.getSelectionModel().getSelectedItem();
            if (selectedSuggestion != null && !selectedSuggestion.isEmpty()) {
                // Hide suggestions list immediately
                suggestionsListView.setVisible(false);
                suggestionsListView.setManaged(false);

                // Fill in search box
                searchBox.setText(selectedSuggestion);

                // Automatically trigger search to display detailed results
                handleSearch();
            }
        });

        // Add listener to searchBox text property for suggestions
        searchBox.textProperty().addListener((observable, oldValue, newValue) -> {
            if (suggestionsScheduler != null && !suggestionsScheduler.isShutdown()) {
                suggestionsScheduler.shutdownNow(); // Cancel previous tasks
            }
            suggestionsScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
            });

            if (newValue == null || newValue.trim().length() < 2) {
                searchSuggestions.clear();
                suggestionsListView.setVisible(false);
                suggestionsListView.setManaged(false);
                return;
            }

            suggestionsScheduler.schedule(() -> {
                fetchSearchSuggestions(newValue.trim());
            }, 200, TimeUnit.MILLISECONDS); // Reduced debounce time to 200ms for faster response
        });

        // Hide suggestions when searchBox loses focus, but allow clicking on
        // suggestions
        searchBox.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                // Add delay to allow clicking on suggestions to work
                Platform.runLater(() -> {
                    if (!suggestionsListView.isFocused() && !searchBox.isFocused()) {
                        suggestionsListView.setVisible(false);
                        suggestionsListView.setManaged(false);
                    }
                });
            } else {
                // When focus on searchBox, show suggestions again if there's suitable text
                String currentText = searchBox.getText();
                if (currentText != null && currentText.length() >= 2 && !searchSuggestions.isEmpty()) {
                    suggestionsListView.setVisible(true);
                    suggestionsListView.setManaged(true);
                }
            }
        });

        // Initialize JxBrowser to display map.
        initializeJxBrowser();

        // Initially hide the dynamic route info scroll pane and set its size
        if (dynamicRouteInfoScrollPane != null) {
            dynamicRouteInfoScrollPane.setVisible(false);
            dynamicRouteInfoScrollPane.setManaged(false); // So it doesn't take up space when invisible
            // Set preferred and minimum height. Adjust these values as needed.
            dynamicRouteInfoScrollPane.setPrefHeight(150);
            dynamicRouteInfoScrollPane.setMinHeight(100);
        }

        if (dynamicRouteInfoTextArea != null) {
            dynamicRouteInfoTextArea.setEditable(false);
            dynamicRouteInfoTextArea.setWrapText(true);
        }

        // Add listener to auto-update placeholder when search list changes
        searchResults.addListener((javafx.collections.ListChangeListener.Change<? extends Place> c) -> {
            updateSearchPlaceholderVisibility();
            if (searchResults.isEmpty()) {
                // Ensure nothing is selected in placeListView
                placeListView.getSelectionModel().clearSelection();
            }
        });

        // Initialize initial placeholder state
        updateRoutePlaceholderVisibility();
        updateSearchPlaceholderVisibility();
    }

    /**
     * Initializes and configures JxBrowser Engine, Browser and BrowserView.
     * Loads map HTML file and sets up Java-JavaScript communication.
     */
    private void initializeJxBrowser() {
        // Load JxBrowser license key from config.properties file.
        String licenseKey = Utils.loadConfigProperty("jxbrowser.license.key");
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            Utils.showAlert(Alert.AlertType.ERROR, "License Key Error",
                    "JxBrowser License Key not found in config.properties.");
            System.err.println(
                    "JxBrowser License Key is missing in config.properties. JxBrowser will not be initialized.");
            // Consider disabling map-related features if no license key.
            return;
        }

        try {
            // Configure and initialize JxBrowser Engine.
            EngineOptions options = EngineOptions.newBuilder(RenderingMode.HARDWARE_ACCELERATED)
                    .licenseKey(licenseKey) // Use loaded license key.
                    .build();
            engine = Engine.newInstance(options);
            browser = engine.newBrowser();

            // Allow JavaScript to call Java methods marked @JsAccessible.
            // Set 'javaConnector' object (this MainController instance) to global window
            // object of JavaScript.
            browser.set(InjectJsCallback.class, params -> {
                Frame frame = params.frame();
                JsObject window = frame.executeJavaScript("window");
                if (window != null) {
                    window.putProperty("javaConnector", MainController.this);
                } else {
                    System.err.println("Could not get window object from frame.");
                }
                return InjectJsCallback.Response.proceed();
            });

            // Listen and log JavaScript console messages.
            browser.on(ConsoleMessageReceived.class, event -> {
                String message = "[JS " + event.consoleMessage().level() + "] " + event.consoleMessage().message();
                System.out.println(message);
            });

            // Handle event when web page (map.html) has finished loading.
            browser.navigation().on(LoadFinished.class, event -> {
                String loadedUrl = "unknown";
                try {
                    if (event.navigation() != null && event.navigation().browser() != null) {
                        loadedUrl = event.navigation().browser().url();
                    }
                } catch (Exception e) {
                    System.err.println("Error retrieving URL in LoadFinished: " + e.getMessage());
                }

                if (loadedUrl.endsWith("map.html")) {
                    String maptilerApiKey = Utils.loadConfigProperty("maptiler.api.key");
                    if (maptilerApiKey != null && !maptilerApiKey.trim().isEmpty()) {
                        browser.mainFrame().ifPresent(frame -> {
                            // Create script to assign API key and call map initialization function in
                            // JavaScript.
                            String script = String.format(
                                    "window.MAPTILER_API_KEY = '%s'; if(typeof initializeMapWithApiKey === 'function') { console.log('Calling initializeMapWithApiKey from Java'); initializeMapWithApiKey(); } else { console.error('initializeMapWithApiKey function not found in map.html'); }",
                                    Utils.escapeJavaScriptString(maptilerApiKey));
                            frame.executeJavaScript(script);
                        });
                    } else {
                        Utils.showAlert(Alert.AlertType.ERROR, "API Key Error",
                                "Could not load MapTiler API Key from config.properties.");
                        System.err.println("MapTiler API Key is missing or empty in config.properties.");
                        // If no API key, try initializing map in fallback mode (e.g., default OSM).
                        browser.mainFrame().ifPresent(frame -> {
                            frame.executeJavaScript(
                                    "if(typeof initializeMapWithApiKey === 'function') { initializeMapWithApiKey(true); } else { console.error('initializeMapWithApiKey function not found for fallback.'); }");
                        });
                    }
                } else {
                    System.err.println("LoadFinished: Loaded URL does NOT end with map.html. URL: \"" + loadedUrl
                            + "\". Map specific initialization will be skipped.");
                }
            });

            // Create BrowserView and add to mapPane.
            browserView = BrowserView.newInstance(browser);
            mapPane.getChildren().add(browserView);

            // Load map.html file.
            Path mapHtmlPath = Paths.get("src/main/resources/tourrouteplanner/map.html").toAbsolutePath();
            if (Files.exists(mapHtmlPath)) {
                browser.navigation().loadUrl(mapHtmlPath.toUri().toString());
            } else {
                String errorMessage = "map.html not found at: " + mapHtmlPath.toString();
                System.err.println(errorMessage);
                Utils.showAlert(Alert.AlertType.ERROR, "Map Loading Error", errorMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "JxBrowser Initialization Error",
                    "Could not initialize JxBrowser: " + e.getMessage());
        }
    }

    /**
     * Reinitializes all markers on the map according to current order of places in
     * route.
     */
    private void refreshMapMarkers() {
        clearAllMarkers();

        // Re-add markers for all places in new order
        for (Place place : currentRoutePlaces) {
            addMapMarker(place.getName(), place.getLatitude(),
                    place.getLongitude(), place.getAddress());
        }
    }

    /**
     * Handles place search event when user types in searchBox.
     * Calls RouteService to search and updates placeListView.
     * Pans map to first result location (if any).
     */
    @FXML
    private void handleSearch() {
        String query = searchBox.getText();
        clearMapHighlight(); // Clear old highlight when starting new search

        // Hide suggestions when a search is explicitly triggered
        if (suggestionsListView != null) {
            suggestionsListView.setVisible(false);
            suggestionsListView.setManaged(false);
        }

        if (query == null || query.trim().isEmpty()) {
            searchResults.clear(); // Clear old results if query is empty.
            String currentText = searchBox.getText();
            if (currentText != null && !currentText.trim().isEmpty() && currentText.length() >= 2) {
                // Don't hide suggestions if there's still text
            } else {
                suggestionsListView.setVisible(false);
                suggestionsListView.setManaged(false);
            }
            return;
        }

        try {
            List<Place> places = geocodingService.searchPlaces(query);
            searchResults.setAll(places); // Update results list.

            if (!places.isEmpty()) {
                placeListView.getSelectionModel().selectFirst(); // Select first result.
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Search Error", "Could not perform search: " + e.getMessage());
        }
    }

    private void fetchSearchSuggestions(String query) {
        if (query.isEmpty()) {
            suggestionsListView.setVisible(false);
            suggestionsListView.setManaged(false);
            return;
        }

        // Cancel any previous suggestion tasks
        if (suggestionsScheduler != null && !suggestionsScheduler.isShutdown()) {
            suggestionsScheduler.shutdownNow();
        }
        suggestionsScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        suggestionsScheduler.schedule(() -> {
            try {
                List<Place> suggestedPlaces = geocodingService.searchPlaces(query);

                String normalizedUserQuery = Utils.normalizeForSearch(query);
                if (normalizedUserQuery == null || normalizedUserQuery.isEmpty()) {
                    Platform.runLater(() -> {
                        suggestionsListView.setVisible(false);
                        suggestionsListView.setManaged(false);
                    });
                    return;
                }

                // Improved suggestion filtering logic to avoid duplicates and better display
                List<String> suggestionNames = suggestedPlaces.stream()
                        .filter(place -> {
                            String placeName = place.getName();
                            String placeAddress = place.getAddress();
                            String combinedInfo = (placeName != null ? placeName : "")
                                    + (placeAddress != null ? " " + placeAddress : "");
                            String normalizedPlaceInfo = Utils.normalizeForSearch(combinedInfo);
                            return normalizedPlaceInfo != null && normalizedPlaceInfo.contains(normalizedUserQuery);
                        })
                        .sorted((p1, p2) -> {
                            // Prioritize places with names starting with query
                            String name1 = p1.getName() != null ? p1.getName().toLowerCase() : "";
                            String name2 = p2.getName() != null ? p2.getName().toLowerCase() : "";
                            String lowerQuery = query.toLowerCase();

                            boolean starts1 = name1.startsWith(lowerQuery);
                            boolean starts2 = name2.startsWith(lowerQuery);

                            if (starts1 && !starts2)
                                return -1;
                            if (!starts1 && starts2)
                                return 1;

                            // If both start or don't start, sort by name length
                            return name1.length() - name2.length();
                        })
                        .map(place -> {
                            String name = place.getName() != null ? place.getName() : "N/A";
                            String address = place.getAddress();

                            // Only show address if it provides meaningful additional information
                            if (address != null && !address.isEmpty() &&
                                    !address.equalsIgnoreCase(name) &&
                                    address.length() > 10 &&
                                    !address.toLowerCase().equals(name.toLowerCase())) {
                                return name + ", " + address;
                            }
                            return name;
                        })
                        .distinct()
                        .limit(5) // Reduce number of suggestions to avoid clutter
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    if (!suggestionNames.isEmpty()) {
                        searchSuggestions.setAll(suggestionNames);
                        suggestionsListView.setVisible(true);
                        suggestionsListView.setManaged(true);
                    } else {
                        suggestionsListView.setVisible(false);
                        suggestionsListView.setManaged(false);
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    System.err.println("Error fetching suggestions: " + e.getMessage());
                    suggestionsListView.setVisible(false);
                    suggestionsListView.setManaged(false);
                });
            }
        }, 200, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculates appropriate width for a column based on its content
     * 
     * @param column Column to calculate width for
     * @param items  List of data items
     * @return Appropriate width for the column
     */
    private double computePrefColumnWidth(TableColumn<Place, String> column, ObservableList<Place> items) {
        double maxWidth = 50; // Minimum width

        // Create temporary Text to measure width
        javafx.scene.text.Text text = new javafx.scene.text.Text();

        // Iterate through each item to measure width
        for (Place item : items) {
            String value = "";
            if (column == routePlaceNameColumn && item.getName() != null) {
                value = item.getName();
            } else if (column == routePlaceAddressColumn && item.getAddress() != null) {
                value = item.getAddress();
            }

            // Measure text width
            text.setText(value);
            double width = text.getBoundsInLocal().getWidth();

            // Update maximum width
            if (width > maxWidth) {
                maxWidth = width;
            }
        }

        // Add padding for scrollbar
        return maxWidth + 20;
    }

    /**
     * Handles removing selected place from routeTableView.
     * Removes corresponding marker and updates route on map.
     */
    @FXML
    private void handleRemoveSelected() {
        // Get selected place from route table.
        Place selectedRoutePlace = routeTableView.getSelectionModel().getSelectedItem();
        if (selectedRoutePlace != null) {
            // Save selected index to select another item after removal
            int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();

            // Check if removed place is currently highlighted
            Place currentlySelectedInList = placeListView.getSelectionModel().getSelectedItem();
            boolean wasHighlightTarget = selectedRoutePlace.equals(currentlySelectedInList);

            currentRoutePlaces.remove(selectedRoutePlace); // Remove from route list.
            clearAllMarkers();
            currentRoutePlaces
                    .forEach(p -> addMapMarker(p.getName(), p.getLatitude(), p.getLongitude(), p.getAddress()));

            // Select a new place in table after removal, if any remain
            if (!currentRoutePlaces.isEmpty()) {
                // If there's item at old position, select it; otherwise select previous item
                if (selectedIndex < currentRoutePlaces.size()) {
                    routeTableView.getSelectionModel().select(selectedIndex);
                } else {
                    routeTableView.getSelectionModel().select(currentRoutePlaces.size() - 1);
                }
            } else {
                // If no places remain, clear highlight
                if (wasHighlightTarget) {
                    clearMapHighlight();
                }
            }

            if (routeCalculated && currentRoutePlaces.size() > 1) {
                // Only recalculate route if calculated before and at least 2 places remain
                handleFindRoute();
            } else if (currentRoutePlaces.size() <= 1) {
                clearRoute(); // Clear route on map if not enough places.
                updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), null);
                routeCalculated = false;
            }
        } else {
            // Notify if no place selected in TableView.
            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice",
                    "Please select a place from the 'Selected places in route' table to remove.");
        }
    }

    /**
     * Handles finding route between places in currentRoutePlaces.
     * Calls RouteService to get route information and draws on map.
     * Updates total distance.
     */
    @FXML
    private void handleFindRoute() {
        if (currentRoutePlaces.size() < 2) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice", "At least 2 places are required to find a route.");
            clearRoute(); // Clear old route (if any).
            updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), "");
            return;
        }
        try {
            Route route = routingService.getRoute(new ArrayList<>(currentRoutePlaces));
            if (route != null && route.getCoordinates() != null && !route.getCoordinates().isEmpty()) {
                drawRoute(route.getCoordinates()); // Draw route on map.

                // Auto zoom to show entire route
                fitToRoute();

                updateDynamicRouteInfo(
                        String.format(Locale.US, "Total distance: %.2f km", route.getTotalDistanceKm()),
                        route.getTurnByTurnInstructions());
                routeCalculated = true;
            } else {
                Utils.showAlert(Alert.AlertType.ERROR, "Route Finding Error",
                        "Could not find route for selected places.");
                clearRoute();
                updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), "");
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Route Finding Error",
                    "Error connecting to routing service: " + e.getMessage());
            clearRoute();
            updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), "");
        }
    }

    /**
     * Updates the dynamic route information display area.
     * 
     * @param totalDistanceText      String displaying total distance.
     * @param turnByTurnInstructions String with turn-by-turn directions.
     */
    private void updateDynamicRouteInfo(String totalDistanceText, String turnByTurnInstructions) {
        // Set routeCalculated variable
        if (totalDistanceText != null
                && !totalDistanceText.equals(String.format(Locale.US, "Total distance: %.2f km", 0.0))) {
            routeCalculated = true;
        } else {
            routeCalculated = false;
        }

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
        } else {
            System.err.println(
                    "dynamicRouteInfoScrollPane or dynamicRouteInfoTextArea is null. Cannot update route info.");
        }
    }

    /**
     * Handles saving current route (place list and route info) to file.
     * Uses StorageService to show save file dialog and perform save.
     */
    @FXML
    private void handleSaveRoute() {
        if (currentRoutePlaces.isEmpty()) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice", "No route to save.");
            return;
        }
        File file = storageService.showSaveFileDialog(mapPane.getScene().getWindow());
        if (file != null) {
            // Pass place list and last route info (if any) to save.
            storageService.saveRoute(file, new ArrayList<>(currentRoutePlaces), routingService.getLastRoute());
        }
    }

    /**
     * Handles loading route from a saved file.
     * Uses StorageService to show open file dialog and load data.
     * Updates UI (place list, map, total distance) with loaded data.
     */
    @FXML
    private void handleLoadRoute() {
        File file = storageService.showOpenFileDialog(mapPane.getScene().getWindow());
        if (file != null) {
            clearMapHighlight(); // Clear old highlight before loading new route
            StorageService.LoadedRouteData loadedData = storageService.loadRoute(file);
            // Check loaded data and place list are not null.
            if (loadedData != null && loadedData.getPlaces() != null) {
                currentRoutePlaces.setAll(loadedData.getPlaces());
                clearAllMarkers();
                currentRoutePlaces
                        .forEach(p -> addMapMarker(p.getName(), p.getLatitude(), p.getLongitude(), p.getAddress()));

                Route loadedRouteInfo = loadedData.getRoute();
                if (loadedRouteInfo != null && loadedRouteInfo.getCoordinates() != null
                        && !loadedRouteInfo.getCoordinates().isEmpty()) {
                    drawRoute(loadedRouteInfo.getCoordinates());
                    updateDynamicRouteInfo(
                            String.format(Locale.US, "Total distance: %.2f km", loadedRouteInfo.getTotalDistanceKm()),
                            loadedRouteInfo.getTurnByTurnInstructions());
                } else {
                    // If no route info in file (e.g., old file only saved places)
                    // or route info is invalid, recalculate if possible.
                    if (currentRoutePlaces.size() >= 2) {
                        handleFindRoute(); // Try to find route based on loaded places.
                    } else {
                        clearRoute();
                        updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), null);
                    }
                }
            } else {
                Utils.showAlert(Alert.AlertType.ERROR, "Route Loading Error",
                        "Could not load route data from selected file or file is invalid.");
                clearRoute();
                updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), null);
            }
        }
    }

    /**
     * Executes a JavaScript code snippet in the main frame of JxBrowser.
     * 
     * @param script JavaScript code to execute.
     */
    private void executeJavaScript(String script) {
        // Ensure browser and mainFrame are available before executing.
        if (browser != null && browser.mainFrame().isPresent()) {
            browser.mainFrame().get().executeJavaScript(script);
        } else {
            System.err.println("Cannot execute JavaScript, browser or main frame not ready. Script: " + script);
        }
    }

    // --- Methods for communicating with JavaScript on the map ---

    /**
     * Pans and zooms map to specific coordinates and zoom level.
     * Called from Java to control the JavaScript map.
     *
     * @param latitude  Latitude of destination.
     * @param longitude Longitude of destination.
     * @param zoomLevel Zoom level (integer).
     */
    @JsAccessible
    public void panTo(double latitude, double longitude, int zoomLevel) {
        if (browser != null && browser.mainFrame().isPresent()) {
            String script = String.format(Locale.US,
                    "if(typeof panTo === 'function') { panTo(%f, %f, %d); } else { console.error('JavaScript function panTo not found.'); }",
                    latitude, longitude, zoomLevel);
            browser.mainFrame().get().executeJavaScript(script);
        }
    }

    /**
     * Requests JavaScript to zoom map to a bounding box.
     * 
     * @param southLat South latitude
     * @param northLat North latitude
     * @param westLon  West longitude
     * @param eastLon  East longitude
     */
    private void zoomToBoundingBox(double southLat, double northLat, double westLon, double eastLon) {
        if (browser != null && browser.mainFrame().isPresent()) {
            String script = String.format(Locale.US,
                    "if(typeof zoomToBoundingBox === 'function') { zoomToBoundingBox(%f, %f, %f, %f); } else { console.error('JavaScript function zoomToBoundingBox not found.'); }",
                    southLat, northLat, westLon, eastLon);
            browser.mainFrame().get().executeJavaScript(script);
        }
    }

    /**
     * Requests JavaScript to highlight a bounding box on map.
     * 
     * @param southLat South latitude
     * @param northLat North latitude
     * @param westLon  West longitude
     * @param eastLon  East longitude
     */
    private void highlightBoundingBoxOnMap(double southLat, double northLat, double westLon, double eastLon) {
        if (browser != null && browser.mainFrame().isPresent()) {
            String script = String.format(Locale.US,
                    "if(typeof highlightBoundingBox === 'function') { highlightBoundingBox(%f, %f, %f, %f); } else { console.error('JavaScript function highlightBoundingBox not found.'); }",
                    southLat, northLat, westLon, eastLon);
            browser.mainFrame().get().executeJavaScript(script);
        }
    }

    /**
     * Requests JavaScript to highlight a geographic object from GeoJSON string.
     * 
     * @param geoJsonString GeoJSON string of the object.
     */
    private void highlightGeoJsonOnMap(String geoJsonString) {
        if (browser != null && browser.mainFrame().isPresent()) {
            // Need to escape GeoJSON string to be valid in a JavaScript string
            String escapedGeoJson = Utils.escapeJavaScriptString(geoJsonString);
            String script = String.format(
                    "if(typeof highlightGeoJsonFeature === 'function') { highlightGeoJsonFeature('%s'); } else { console.error('JavaScript function highlightGeoJsonFeature not found.'); }",
                    escapedGeoJson);
            browser.mainFrame().get().executeJavaScript(script);
        }
    }

    /**
     * Requests JavaScript to clear all current highlights on map.
     */
    private void clearMapHighlight() {
        if (browser != null && browser.mainFrame().isPresent()) {
            executeJavaScript(
                    "if(typeof clearHighlight === 'function') { clearHighlight(); } else { console.error('clearHighlight function not found in map.html'); }");
        } else {
            System.err.println("Cannot call clearMapHighlight: browser or mainFrame not available.");
        }
    }

    /**
     * Adds a marker to the map at position (lat, lng) with given name and
     * description.
     * Calls JavaScript function 'addMapMarker' in map.html.
     * 
     * @param name        Name of the place (displayed in marker popup).
     * @param lat         Latitude of the place.
     * @param lng         Longitude of the place.
     * @param description Detailed description of the place.
     */
    public void addMapMarker(String name, double lat, double lng, String description) {
        String script = String.format(Locale.US, "addMapMarker('%s', %f, %f, '%s');",
                Utils.escapeJavaScriptString(name), lat, lng, Utils.escapeJavaScriptString(description));
        executeJavaScript(script);
    }

    /**
     * Draws a route on the map based on list of coordinates.
     * Calls JavaScript function 'drawRoute' in map.html.
     * 
     * @param coordinates List of Route.Coordinate objects representing the route.
     */
    public void drawRoute(List<Route.Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            clearRoute(); // Clear old route if no new coordinates.
            return;
        }
        // Build a JavaScript array from coordinate list.
        StringBuilder jsRouteArray = new StringBuilder("[");
        for (int i = 0; i < coordinates.size(); i++) {
            Route.Coordinate coord = coordinates.get(i);
            if (coord != null) {
                jsRouteArray.append(
                        String.format(Locale.US, "{lat: %f, lng: %f}", coord.getLatitude(), coord.getLongitude()));
                if (i < coordinates.size() - 1) {
                    jsRouteArray.append(",");
                }
            }
        }
        jsRouteArray.append("]");
        String script = String.format("drawRoute(%s);", jsRouteArray.toString());
        executeJavaScript(script);
    }

    /**
     * Clears all markers from the map.
     * Calls JavaScript function 'clearAllMarkers' in map.html.
     */
    public void clearAllMarkers() {
        executeJavaScript("clearAllMarkers();");
    }

    /**
     * Clears current route from the map.
     * Calls JavaScript function 'clearRoute' in map.html.
     */
    public void clearRoute() {
        if (browser != null && browser.mainFrame().isPresent()) {
            executeJavaScript(
                    "if(typeof clearRoute === 'function') { clearRoute(); } else { console.error('clearRoute function not found in map.html'); }");
        } else {
            System.err.println("Cannot call clearRoute: browser or mainFrame not available.");
        }
    }

    /**
     * Auto zooms map to show entire route.
     * Calls JavaScript function 'fitToRoute' in map.html.
     */
    public void fitToRoute() {
        executeJavaScript(
                "if(typeof fitToRoute === 'function') { fitToRoute(); } else { console.error('JavaScript function fitToRoute not found.'); }");
    }

    /**
     * Shows and highlights selected place on map.
     * 
     * @param place Place to show and highlight
     */
    private void showSelectedPlaceOnMap(Place place) {
        if (place != null) {
            String geoJson = place.getGeoJson();
            double[] boundingBox = place.getBoundingBox();

            // Show selected place on map
            if (geoJson != null && !geoJson.trim().isEmpty()) {
                // Prioritize GeoJSON highlight if available
                highlightGeoJsonOnMap(geoJson);
                // Still zoom to bounding box if available
                if (boundingBox != null && boundingBox.length == 4) {
                    zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                } else {
                    // If no bounding box, pan to center point with default zoom level
                    panTo(place.getLatitude(), place.getLongitude(), 15);
                }
            } else if (boundingBox != null && boundingBox.length == 4) {
                // If no GeoJSON but has bounding box, highlight bounding box
                highlightBoundingBoxOnMap(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
            } else {
                // If neither GeoJSON nor bounding box, pan to center point
                panTo(place.getLatitude(), place.getLongitude(), 15);
                clearMapHighlight(); // Clear old highlight
            }

            // Show notification to user
            statusLabel.setText("Moved to place: " + place.getName());
        }
    }

    /**
     * Handles event when user clicks on the map.
     * This method is called from JavaScript via 'javaConnector'.
     * Performs reverse geocoding to get place info at clicked location
     * and asks user if they want to add that place to route.
     * 
     * @param lat Latitude of clicked point.
     * @param lng Longitude of clicked point.
     */
    @JsAccessible
    public void handleMapClick(double lat, double lng) {
        Platform.runLater(() -> { // Ensure UI changes are made on JavaFX Application Thread.
            try {
                Place clickedPlace = geocodingService.reverseGeocode(lat, lng); // Get place info.
                if (clickedPlace != null) {
                    // Show confirmation dialog to add place.
                    Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmDialog.setTitle("Add Place");
                    confirmDialog.setHeaderText("Add place from map?");
                    confirmDialog.setContentText("Do you want to add \"" + clickedPlace.getName() + "\" to the route?");
                    Optional<ButtonType> result = confirmDialog.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        if (!currentRoutePlaces.contains(clickedPlace)) {
                            currentRoutePlaces.add(clickedPlace);
                            addMapMarker(clickedPlace.getName(), clickedPlace.getLatitude(),
                                    clickedPlace.getLongitude(), clickedPlace.getAddress());
                        } else {
                            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice", "Place is already in route.");
                        }
                    }
                } else {
                    Utils.showAlert(Alert.AlertType.INFORMATION, "Notice",
                            "Could not find information for clicked location.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                Utils.showAlert(Alert.AlertType.ERROR, "Reverse Geocoding Error",
                        "Could not get place information: " + e.getMessage());
            }
        });
    }

    /**
     * Logs a message from JavaScript to Java console.
     * This method can be called from JavaScript via
     * 'javaConnector.logJs("message")'.
     * 
     * @param message Message to log.
     */
    @JsAccessible
    public void logJs(String message) {
        System.out.println("JS (direct call via javaConnector): " + message);
    }

    /**
     * Closes JxBrowser engine when application terminates.
     * Important to release resources.
     */
    public void shutdownJxBrowser() {
        if (engine != null && !engine.isClosed()) {
            System.out.println("Shutting down JxBrowser engine...");
            engine.close();
            System.out.println("JxBrowser engine closed.");
        }
    }

    /**
     * Handles application exit from menu.
     * Closes JavaFX platform and terminates process.
     */
    @FXML
    private void onExit() {
        Platform.exit(); // Request JavaFX platform to exit.
        System.exit(0); // Ensure JVM process terminates.
    }

    /**
     * Shows "About" dialog of the application.
     */
    @FXML
    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Tour Route Planner");
        alert.setContentText("A travel route planning application.\\nVersion 1.0\\nDeveloped by Development Team.");
        alert.showAndWait();
    }

    /**
     * Handles clearing all places from current route.
     * Shows confirmation dialog before clearing.
     * Updates UI (list, map, total distance).
     */
    @FXML
    private void onClearAllPlaces() {
        if (currentRoutePlaces.isEmpty()) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice", "No places to clear.");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Clear");
        confirmDialog.setHeaderText("Clear all selected places?");
        confirmDialog.setContentText("Are you sure you want to clear all places from the current route?");
        Optional<ButtonType> result = confirmDialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            currentRoutePlaces.clear();
            clearAllMarkers();
            clearRoute();
            clearMapHighlight();
            updateDynamicRouteInfo(String.format(Locale.US, "Total distance: %.2f km", 0.0), null);
            if (statusLabel != null) {
                statusLabel.setText("Cleared all places. Route is empty.");
            }
        }
    }

    /**
     * Handles event when user clicks up arrow button.
     * Moves selected place up one position in route list.
     */
    @FXML
    private void handleMoveUp() {
        int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
        if (selectedIndex > 0) { // Ensure not the first place
            // Save selected place
            Place selectedPlace = currentRoutePlaces.get(selectedIndex);

            // Swap positions
            currentRoutePlaces.remove(selectedIndex);
            currentRoutePlaces.add(selectedIndex - 1, selectedPlace);

            // Refresh markers on map
            refreshMapMarkers();

            // Update table and reselect moved place
            routeTableView.getSelectionModel().select(selectedIndex - 1);

            // Ensure selected place is highlighted on map
            showSelectedPlaceOnMap(selectedPlace);

            // If route was calculated before, recalculate
            if (routeCalculated && currentRoutePlaces.size() > 1) {
                handleFindRoute();
            }
        } else {
            // Notify if cannot move up (already at top of list)
            statusLabel.setText("This place is already at the first position in the route.");
        }
    }

    /**
     * Handles event when user clicks down arrow button.
     * Moves selected place down one position in route list.
     */
    @FXML
    private void handleMoveDown() {
        int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < currentRoutePlaces.size() - 1) { // Ensure not the last place
            // Save selected place
            Place selectedPlace = currentRoutePlaces.get(selectedIndex);

            // Swap positions
            currentRoutePlaces.remove(selectedIndex);
            currentRoutePlaces.add(selectedIndex + 1, selectedPlace);

            // Refresh markers on map
            refreshMapMarkers();

            // Update table and reselect moved place
            routeTableView.getSelectionModel().select(selectedIndex + 1);

            // Ensure selected place is highlighted on map
            showSelectedPlaceOnMap(selectedPlace);

            // If route was calculated before, recalculate
            if (routeCalculated && currentRoutePlaces.size() > 1) {
                handleFindRoute();
            }
        } else if (selectedIndex == currentRoutePlaces.size() - 1) {
            // Notify if cannot move down (already at bottom of list)
            statusLabel.setText("This place is already at the last position in the route.");
        }
    }

    /**
     * Handles event when user clicks dark mode toggle button.
     * Switches between light and dark mode.
     */
    @FXML
    private void toggleDarkMode() {
        isDarkMode = !isDarkMode;

        // Get root node of scene
        if (placeListView.getScene() != null && placeListView.getScene().getRoot() != null) {
            // Add or remove "dark-mode" class from root node
            if (isDarkMode) {
                placeListView.getScene().getRoot().getStyleClass().add("dark-mode");
                statusLabel.setText("🌙 Switched to dark mode");
                // Update icon and tooltip for Dark Mode
                setDarkModeButtonIcon(true);
                Tooltip.install(darkModeToggle, new Tooltip("Switch to light mode"));
            } else {
                placeListView.getScene().getRoot().getStyleClass().remove("dark-mode");
                statusLabel.setText("☀️ Switched to light mode");
                // Update icon and tooltip for Light Mode
                setDarkModeButtonIcon(false);
                Tooltip.install(darkModeToggle, new Tooltip("Switch to dark mode"));
            }
        }
    }

    /**
     * Sets icon for Dark Mode toggle button based on current state
     * 
     * @param isDarkMode true if in Dark Mode, false if in Light Mode
     */
    private void setDarkModeButtonIcon(boolean isDarkMode) {
        try {
            String iconName = isDarkMode ? "sun.png" : "moon.png";
            Image image = new Image(getClass().getResourceAsStream("/tourrouteplanner/icons/" + iconName));
            ImageView imageView = new ImageView(image);
            imageView.setFitHeight(20);
            imageView.setFitWidth(20);
            darkModeToggle.setGraphic(imageView);
            darkModeToggle.setText(""); // Remove text since we use image
        } catch (Exception e) {
            // Fallback if icon not found
            darkModeToggle.setText(isDarkMode ? "☀️" : "🌙");
            System.err.println("Could not load Dark Mode icon: " + e.getMessage());
        }
    }

    /**
     * Updates visibility state of placeholder text for route table.
     * Placeholder will show when:
     * - Table has no data (empty)
     * Placeholder will hide when:
     * - Table has at least one place
     */
    private void updateRoutePlaceholderVisibility() {
        if (routePlaceholder != null) {
            boolean shouldShowPlaceholder = currentRoutePlaces.isEmpty();
            routePlaceholder.setVisible(shouldShowPlaceholder);
        }
    }

    /**
     * Updates visibility state of placeholder for search results
     */
    private void updateSearchPlaceholderVisibility() {
        if (searchPlaceholder != null) {
            boolean hasResults = !searchResults.isEmpty();
            searchPlaceholder.setVisible(!hasResults);
            searchPlaceholder.setManaged(!hasResults);
        }
    }
}
