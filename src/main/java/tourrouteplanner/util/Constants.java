package tourrouteplanner.util;

/**
 * Class containing constant values used throughout the application.
 */
public class Constants {
    public static final String APP_NAME = "Tour Route Planner";
    /** Directory for storing application data such as saved routes and places. */
    public static final String DATA_PATH = "data/";
    /** JSON file name for storing the list of saved routes. */
    public static final String ROUTES_FILE = DATA_PATH + "routes.json";
    /** JSON file name for storing the list of saved places. */
    public static final String PLACES_FILE = DATA_PATH + "places.json";
    /** Configuration file name located in src/main/resources. */
    public static final String CONFIG_FILE = "config.properties";

    // API Key is read from config.properties, not hardcoded here.
}