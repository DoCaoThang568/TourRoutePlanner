package tourrouteplanner.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;
import tourrouteplanner.util.Constants;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class responsible for storing and loading application data.
 * This data includes list of places ({@link Place}) and route information
 * ({@link Route}).
 * Uses JSON format for file storage.
 */
public class StorageService {
    /** Gson object used for converting between Java objects and JSON strings. */
    private final Gson gson;

    /**
     * Static inner class encapsulating data loaded from a route file.
     * Includes list of places and detailed route information.
     */
    public static class LoadedRouteData {
        /** List of places (waypoints) belonging to the loaded route. */
        private List<Place> places;
        /** Detailed information of the loaded route. */
        private Route route;

        /**
         * Creates a {@code LoadedRouteData} object.
         * 
         * @param places List of {@link Place} of the route.
         * @param route  The {@link Route} object containing route information.
         */
        public LoadedRouteData(List<Place> places, Route route) {
            this.places = places;
            this.route = route;
        }

        /** Gets the list of places of the loaded route. */
        public List<Place> getPlaces() {
            return places;
        }

        /** Gets the detailed information of the loaded route. */
        public Route getRoute() {
            return route;
        }
    }

    /**
     * Creates a {@code StorageService} object.
     * Configures the {@link Gson} object for "pretty printing" (human-readable)
     * JSON output.
     */
    public StorageService() {
        // Configure Gson for pretty print to make JSON files more readable.
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Displays a dialog allowing the user to choose location and filename to save
     * the route.
     * 
     * @param ownerWindow The owner {@link Window} of this dialog.
     *                    The dialog will be shown as modal relative to this window.
     * @return A {@link File} object representing the file selected by the user for
     *         saving.
     *         Returns {@code null} if the user cancels the file selection.
     */
    public File showSaveFileDialog(Window ownerWindow) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Route to JSON File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*"));
        // Set initial directory to application data directory.
        File initialDirectory = new File(Constants.DATA_PATH);
        if (initialDirectory.exists() && initialDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(initialDirectory);
        } else {
            // If DATA_PATH doesn't exist, let the OS decide the default directory.
            System.out.println("DATA_PATH directory does not exist, using system default directory for FileChooser.");
        }
        return fileChooser.showSaveDialog(ownerWindow);
    }

    /**
     * Displays a dialog allowing the user to choose a file to load (open) a route.
     * 
     * @param ownerWindow The owner {@link Window} of this dialog.
     * @return A {@link File} object representing the file selected by the user to
     *         open.
     *         Returns {@code null} if the user cancels the file selection.
     */
    public File showOpenFileDialog(Window ownerWindow) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Route from JSON File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*"));
        // Set initial directory to application data directory.
        File initialDirectory = new File(Constants.DATA_PATH);
        if (initialDirectory.exists() && initialDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(initialDirectory);
        } else {
            System.out.println("DATA_PATH directory does not exist, using system default directory for FileChooser.");
        }
        return fileChooser.showOpenDialog(ownerWindow);
    }

    /**
     * Saves route information, including list of places and route details,
     * to a specified file in JSON format.
     * 
     * @param file   The {@link File} where route data will be saved.
     * @param places List of {@link Place} (waypoints) of the route.
     * @param route  The {@link Route} object containing detailed route information.
     * @return {@code true} if saving was successful; {@code false} if an error
     *         occurred (e.g., {@link IOException}).
     */
    public boolean saveRoute(File file, List<Place> places, Route route) {
        LoadedRouteData dataToSave = new LoadedRouteData(places, route);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(dataToSave, writer);
            System.out.println("Route successfully saved to: " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("Error saving route to file " + file.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
            // Consider showing an error message to the user via UI.
            return false;
        }
    }

    /**
     * Loads route data (including list of places and route information) from a JSON
     * file.
     * 
     * @param file The {@link File} from which data will be loaded.
     * @return A {@link LoadedRouteData} object containing the loaded data.
     *         Returns {@code null} if file not found, read error, or JSON syntax
     *         error.
     */
    public LoadedRouteData loadRoute(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            System.err.println("Invalid or unreadable file: " + (file != null ? file.getAbsolutePath() : "null"));
            return null;
        }
        try (Reader reader = new FileReader(file)) {
            Type dataType = new TypeToken<LoadedRouteData>() {
            }.getType();
            LoadedRouteData loadedData = gson.fromJson(reader, dataType);
            if (loadedData != null) {
                System.out.println("Route successfully loaded from: " + file.getAbsolutePath());
            } else {
                System.err.println("Could not parse route data from file (null result): " + file.getAbsolutePath());
            }
            return loadedData;
        } catch (FileNotFoundException e) {
            System.err.println("File not found when loading route: " + file.getAbsolutePath());
            // This case is unlikely since file.exists() was already checked above.
            return null;
        } catch (IOException e) {
            System.err
                    .println("IO error when loading route from file " + file.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("JSON syntax error in route file " + file.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Saves a list of places ({@link Place}) to a JSON file at the specified path.
     * This method is typically used to save favorite or searched places.
     * 
     * @param places   List of {@link Place} objects to save.
     * @param filePath Absolute path to the file where the place list will be saved.
     * @return {@code true} if saving was successful; {@code false} if an error
     *         occurred.
     */
    public boolean savePlaces(List<Place> places, String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            System.err.println("File path for saving places must not be empty.");
            return false;
        }
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(places, writer);
            System.out.println("Place list successfully saved to: " + filePath);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving place list to file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Loads a list of places ({@link Place}) from a JSON file at the specified
     * path.
     * 
     * @param filePath Absolute path to the file from which the place list will be
     *                 loaded.
     * @return A {@link List} of loaded {@link Place} objects.
     *         Returns a new empty list if file not found, read error, JSON syntax
     *         error,
     *         or if the JSON file contains {@code null}.
     */
    public List<Place> loadPlaces(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            System.err.println("File path for loading places must not be empty.");
            return new ArrayList<>(); // Return empty list instead of null
        }
        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            System.err.println("Places file does not exist or is unreadable: " + filePath);
            return new ArrayList<>(); // Return empty list instead of null
        }

        try (Reader reader = new FileReader(filePath)) {
            Type placeListType = new TypeToken<ArrayList<Place>>() {
            }.getType();
            List<Place> loadedPlaces = gson.fromJson(reader, placeListType);
            if (loadedPlaces != null) {
                System.out.println("Place list successfully loaded from: " + filePath);
                return loadedPlaces;
            } else {
                System.err.println(
                        "Could not parse place data from file (null result): " + filePath + ". Returning empty list.");
                return new ArrayList<>(); // Ensure never returning null
            }
        } catch (FileNotFoundException e) {
            // This case is unlikely since file.exists() was already checked above.
            System.err.println("File not found when loading places: " + filePath);
            return new ArrayList<>();
        } catch (IOException e) {
            System.err.println("IO error when loading places from file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("JSON syntax error in places file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Ensures that the application data directory (defined in
     * {@link Constants#DATA_PATH}) exists.
     * If this directory does not exist, the method will attempt to create it
     * (including parent directories if needed).
     */
    public void ensureDataDirectoryExists() {
        File dataDir = new File(Constants.DATA_PATH);
        if (!dataDir.exists()) {
            if (dataDir.mkdirs()) {
                System.out.println("Directory " + Constants.DATA_PATH + " has been created by StorageService.");
            } else {
                System.err.println("StorageService: Could not create directory " + Constants.DATA_PATH);
            }
        }
    }
}