package tourrouteplanner.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tourrouteplanner.model.Place;
import tourrouteplanner.service.IGeocodingService;
import tourrouteplanner.util.Utils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for managing search operations and UI.
 * Handles place search, suggestions, and search result display.
 */
public class SearchHelper {

    private static final Logger log = LoggerFactory.getLogger(SearchHelper.class);
    private static final int DEBOUNCE_DELAY_MS = 300;
    private static final int MIN_SEARCH_LENGTH = 2;

    private final IGeocodingService geocodingService;
    private final ListView<Place> placeListView;
    private final ListView<String> suggestionsListView;
    private final TextField searchBox;

    private final ObservableList<Place> searchResults = FXCollections.observableArrayList();
    private final ObservableList<String> searchSuggestions = FXCollections.observableArrayList();
    private ScheduledExecutorService suggestionsScheduler;

    // Callback interfaces
    private PlaceAddCallback onPlaceAdd;
    private PlaceSelectCallback onPlaceSelect;

    @FunctionalInterface
    public interface PlaceAddCallback {
        void onAdd(Place place);
    }

    @FunctionalInterface
    public interface PlaceSelectCallback {
        void onSelect(Place place);
    }

    /**
     * Creates a SearchHelper.
     */
    public SearchHelper(IGeocodingService geocodingService,
            TextField searchBox,
            ListView<Place> placeListView,
            ListView<String> suggestionsListView,
            Label statusLabel) {
        this.geocodingService = geocodingService;
        this.searchBox = searchBox;
        this.placeListView = placeListView;
        this.suggestionsListView = suggestionsListView;
        // statusLabel reserved for future use

        initializeScheduler();
    }

    private void initializeScheduler() {
        suggestionsScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Sets callback for when a place is added.
     */
    public void setOnPlaceAdd(PlaceAddCallback callback) {
        this.onPlaceAdd = callback;
    }

    /**
     * Sets callback for when a place is selected.
     */
    public void setOnPlaceSelect(PlaceSelectCallback callback) {
        this.onPlaceSelect = callback;
    }

    /**
     * Gets the search results list.
     */
    public ObservableList<Place> getSearchResults() {
        return searchResults;
    }

    /**
     * Sets up the place list view with custom cell factory.
     */
    public void setupPlaceListView(ObservableList<Place> currentRoutePlaces) {
        placeListView.setItems(searchResults);
        placeListView.setCellFactory(param -> new ListCell<Place>() {
            private Button addButton;
            private HBox hbox;
            private VBox vbox;
            private Label nameLabel;
            private Label addressLabel;
            private final Tooltip tooltip = new Tooltip();

            {
                addButton = new Button("Add");
                addButton.getStyleClass().add("place-add-button");
                addButton.setOnAction(event -> {
                    Place place = getItem();
                    if (place != null && onPlaceAdd != null) {
                        onPlaceAdd.onAdd(place);
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
                HBox.setHgrow(vbox, javafx.scene.layout.Priority.ALWAYS);
            }

            @Override
            protected void updateItem(Place place, boolean empty) {
                super.updateItem(place, empty);
                if (empty || place == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    nameLabel.setText(place.getName());
                    addressLabel.setText(place.getAddress());

                    boolean alreadyInRoute = currentRoutePlaces.contains(place);
                    addButton.setDisable(alreadyInRoute);
                    addButton.setText(alreadyInRoute ? "Added" : "Add");

                    tooltip.setText(place.getName() + "\n" + place.getAddress());
                    setTooltip(tooltip);
                    setGraphic(hbox);
                }
            }
        });

        // Handle selection
        placeListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onPlaceSelect != null) {
                onPlaceSelect.onSelect(newVal);
            }
        });
    }

    /**
     * Sets up the suggestions list view.
     */
    public void setupSuggestionsListView() {
        suggestionsListView.setItems(searchSuggestions);
        suggestionsListView.setVisible(false);
        suggestionsListView.setManaged(false);

        suggestionsListView.setOnMouseClicked(event -> {
            String selectedSuggestion = suggestionsListView.getSelectionModel().getSelectedItem();
            if (selectedSuggestion != null && !selectedSuggestion.isEmpty()) {
                searchBox.setText(selectedSuggestion);
                hideSuggestions();
                handleSearch(selectedSuggestion);
            }
        });
    }

    /**
     * Sets up search box text listener for auto-suggestions.
     */
    public void setupSearchBoxListener() {
        searchBox.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.length() >= MIN_SEARCH_LENGTH) {
                fetchSearchSuggestions(newValue);
            } else {
                hideSuggestions();
            }
        });

        searchBox.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                // Small delay before hiding to allow click on suggestion
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                    String text = searchBox.getText();
                    if (text == null || text.length() < MIN_SEARCH_LENGTH) {
                        hideSuggestions();
                    }
                });
            } else {
                String text = searchBox.getText();
                if (text != null && text.length() >= MIN_SEARCH_LENGTH && !searchSuggestions.isEmpty()) {
                    showSuggestions();
                }
            }
        });
    }

    /**
     * Performs a search for the given query.
     */
    public void handleSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchResults.clear();
            hideSuggestions();
            return;
        }

        try {
            List<Place> places = geocodingService.searchPlaces(query);
            searchResults.setAll(places);
            if (!places.isEmpty()) {
                placeListView.getSelectionModel().selectFirst();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Search Error", "Could not perform search: " + e.getMessage());
        }
    }

    /**
     * Fetches search suggestions with debouncing.
     */
    private void fetchSearchSuggestions(String query) {
        if (query.isEmpty()) {
            hideSuggestions();
            return;
        }

        // Cancel previous task
        if (suggestionsScheduler != null && !suggestionsScheduler.isShutdown()) {
            suggestionsScheduler.shutdownNow();
        }
        initializeScheduler();

        suggestionsScheduler.schedule(() -> {
            try {
                List<Place> suggestedPlaces = geocodingService.searchPlaces(query);
                String normalizedQuery = Utils.normalizeForSearch(query);

                if (normalizedQuery == null || normalizedQuery.isEmpty()) {
                    Platform.runLater(this::hideSuggestions);
                    return;
                }

                // Convert places to suggestion strings
                List<String> suggestions = suggestedPlaces.stream()
                        .limit(5)
                        .map(Place::getName)
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    searchSuggestions.setAll(suggestions);
                    if (!suggestions.isEmpty()) {
                        showSuggestions();
                    } else {
                        hideSuggestions();
                    }
                });
            } catch (IOException e) {
                log.warn("Error fetching suggestions: {}", e.getMessage());
            }
        }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void showSuggestions() {
        suggestionsListView.setVisible(true);
        suggestionsListView.setManaged(true);
    }

    private void hideSuggestions() {
        suggestionsListView.setVisible(false);
        suggestionsListView.setManaged(false);
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown() {
        if (suggestionsScheduler != null && !suggestionsScheduler.isShutdown()) {
            suggestionsScheduler.shutdownNow();
        }
    }
}
