package tourrouteplanner.service;

import javafx.stage.Window;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;

import java.io.File;
import java.util.List;

/**
 * Interface for storage operations.
 * Implementations handle saving and loading routes to/from files.
 */
public interface IStorageService {

    /**
     * Shows a file save dialog and returns the selected file.
     *
     * @param ownerWindow The parent window for the dialog.
     * @return The selected file, or null if cancelled.
     */
    File showSaveFileDialog(Window ownerWindow);

    /**
     * Shows a file open dialog and returns the selected file.
     *
     * @param ownerWindow The parent window for the dialog.
     * @return The selected file, or null if cancelled.
     */
    File showOpenFileDialog(Window ownerWindow);

    /**
     * Saves a route (places and route info) to the specified file.
     *
     * @param file   The file to save to.
     * @param places List of places in the route.
     * @param route  The calculated route information (can be null).
     * @return true if save was successful, false otherwise.
     */
    boolean saveRoute(File file, List<Place> places, Route route);

    /**
     * Loads a route from the specified file.
     *
     * @param file The file to load from.
     * @return The loaded route data, or null if loading failed.
     */
    StorageService.LoadedRouteData loadRoute(File file);
}
