package tourrouteplanner.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;
import tourrouteplanner.service.IRoutingService;
import tourrouteplanner.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

/**
 * Helper class for managing route table operations.
 * Handles adding/removing places, reordering, and route calculation.
 */
public class RouteHelper {

    private final IRoutingService routingService;
    private final TableView<Place> routeTableView;
    private final TableColumn<Place, String> nameColumn;
    private final TableColumn<Place, String> addressColumn;
    private final Label statusLabel;

    private final ObservableList<Place> currentRoutePlaces = FXCollections.observableArrayList();
    private boolean routeCalculated = false;

    // Callback interfaces
    private RouteUpdateCallback onRouteUpdate;
    private MapRefreshCallback onMapRefresh;
    private PlaceSelectCallback onPlaceSelect;

    @FunctionalInterface
    public interface RouteUpdateCallback {
        void onUpdate(Route route);
    }

    @FunctionalInterface
    public interface MapRefreshCallback {
        void onRefesh(List<Place> places);
    }

    @FunctionalInterface
    public interface PlaceSelectCallback {
        void onSelect(Place place);
    }

    /**
     * Creates a RouteHelper.
     */
    public RouteHelper(IRoutingService routingService,
            TableView<Place> routeTableView,
            TableColumn<Place, String> nameColumn,
            TableColumn<Place, String> addressColumn,
            Label statusLabel) {
        this.routingService = routingService;
        this.routeTableView = routeTableView;
        this.nameColumn = nameColumn;
        this.addressColumn = addressColumn;
        this.statusLabel = statusLabel;
    }

    /**
     * Sets callback for route updates.
     */
    public void setOnRouteUpdate(RouteUpdateCallback callback) {
        this.onRouteUpdate = callback;
    }

    /**
     * Sets callback for map refresh.
     */
    public void setOnMapRefresh(MapRefreshCallback callback) {
        this.onMapRefresh = callback;
    }

    /**
     * Sets callback for place selection.
     */
    public void setOnPlaceSelect(PlaceSelectCallback callback) {
        this.onPlaceSelect = callback;
    }

    /**
     * Gets the current route places list.
     */
    public ObservableList<Place> getCurrentRoutePlaces() {
        return currentRoutePlaces;
    }

    /**
     * Checks if route has been calculated.
     */
    public boolean isRouteCalculated() {
        return routeCalculated;
    }

    /**
     * Sets route calculated flag.
     */
    public void setRouteCalculated(boolean calculated) {
        this.routeCalculated = calculated;
    }

    /**
     * Sets up the route table view.
     */
    public void setupRouteTableView() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));

        // Set cell factory for wrapping text
        nameColumn.setCellFactory(column -> new TableCell<Place, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label label = new Label(item);
                    label.setWrapText(true);
                    label.setMaxWidth(column.getWidth() - 10);
                    setGraphic(label);
                }
            }
        });

        addressColumn.setCellFactory(column -> new TableCell<Place, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label label = new Label(item);
                    label.setWrapText(true);
                    label.setMaxWidth(column.getWidth() - 10);
                    setGraphic(label);
                }
            }
        });

        routeTableView.setItems(currentRoutePlaces);

        // Handle selection
        routeTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onPlaceSelect != null) {
                onPlaceSelect.onSelect(newVal);
            }
        });
    }

    /**
     * Adds a place to the route.
     *
     * @param place The place to add.
     * @return true if added successfully, false if already exists.
     */
    public boolean addPlace(Place place) {
        if (place == null)
            return false;

        if (currentRoutePlaces.contains(place)) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice", "This place is already in the route.");
            return false;
        }

        currentRoutePlaces.add(place);
        statusLabel.setText("Added: " + place.getName());
        return true;
    }

    /**
     * Removes the selected place from the route.
     */
    public void removeSelected() {
        Place selected = routeTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            currentRoutePlaces.remove(selected);
            statusLabel.setText("Removed: " + selected.getName());

            if (onMapRefresh != null) {
                onMapRefresh.onRefesh(new ArrayList<>(currentRoutePlaces));
            }

            // Recalculate route if needed
            if (routeCalculated && currentRoutePlaces.size() >= 2) {
                findRoute();
            }
        } else {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice", "Please select a place to remove.");
        }
    }

    /**
     * Clears all places from the route.
     */
    public void clearAll() {
        if (!currentRoutePlaces.isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Clear");
            confirm.setHeaderText("Clear all places?");
            confirm.setContentText("Are you sure you want to remove all places from the route?");

            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    currentRoutePlaces.clear();
                    routeCalculated = false;
                    statusLabel.setText("All places cleared.");

                    if (onRouteUpdate != null) {
                        onRouteUpdate.onUpdate(null);
                    }
                }
            });
        }
    }

    /**
     * Moves selected place up in the list.
     */
    public void moveUp() {
        int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
        if (selectedIndex > 0) {
            Place selected = currentRoutePlaces.get(selectedIndex);
            currentRoutePlaces.remove(selectedIndex);
            currentRoutePlaces.add(selectedIndex - 1, selected);
            routeTableView.getSelectionModel().select(selectedIndex - 1);

            if (onMapRefresh != null) {
                onMapRefresh.onRefesh(new ArrayList<>(currentRoutePlaces));
            }
            if (onPlaceSelect != null) {
                onPlaceSelect.onSelect(selected);
            }

            if (routeCalculated && currentRoutePlaces.size() >= 2) {
                findRoute();
            }
        } else {
            statusLabel.setText("This place is already at the first position.");
        }
    }

    /**
     * Moves selected place down in the list.
     */
    public void moveDown() {
        int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < currentRoutePlaces.size() - 1) {
            Place selected = currentRoutePlaces.get(selectedIndex);
            currentRoutePlaces.remove(selectedIndex);
            currentRoutePlaces.add(selectedIndex + 1, selected);
            routeTableView.getSelectionModel().select(selectedIndex + 1);

            if (onMapRefresh != null) {
                onMapRefresh.onRefesh(new ArrayList<>(currentRoutePlaces));
            }
            if (onPlaceSelect != null) {
                onPlaceSelect.onSelect(selected);
            }

            if (routeCalculated && currentRoutePlaces.size() >= 2) {
                findRoute();
            }
        } else if (selectedIndex == currentRoutePlaces.size() - 1) {
            statusLabel.setText("This place is already at the last position.");
        }
    }

    /**
     * Calculates and returns route between current places.
     *
     * @return The calculated Route, or null if failed.
     */
    public Route findRoute() {
        if (currentRoutePlaces.size() < 2) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Notice",
                    "At least 2 places are required to find a route.");
            routeCalculated = false;
            if (onRouteUpdate != null) {
                onRouteUpdate.onUpdate(null);
            }
            return null;
        }

        try {
            Route route = routingService.getRoute(new ArrayList<>(currentRoutePlaces));
            if (route != null && route.getCoordinates() != null && !route.getCoordinates().isEmpty()) {
                routeCalculated = true;
                if (onRouteUpdate != null) {
                    onRouteUpdate.onUpdate(route);
                }
                return route;
            } else {
                Utils.showAlert(Alert.AlertType.ERROR, "Route Finding Error",
                        "Could not find route for selected places.");
                routeCalculated = false;
                if (onRouteUpdate != null) {
                    onRouteUpdate.onUpdate(null);
                }
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Route Finding Error",
                    "Error connecting to routing service: " + e.getMessage());
            routeCalculated = false;
            if (onRouteUpdate != null) {
                onRouteUpdate.onUpdate(null);
            }
            return null;
        }
    }

    /**
     * Sets places from loaded data.
     */
    public void setPlaces(List<Place> places) {
        currentRoutePlaces.setAll(places);
    }

    /**
     * Gets the last calculated route.
     */
    public Route getLastRoute() {
        return routingService.getLastRoute();
    }
}
