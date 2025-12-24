package tourrouteplanner.util;

/**
 * Class containing constant values used throughout the application.
 * Centralizes all magic numbers, URLs, and configuration keys.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    // ==================== Application Info ====================

    /** Application name displayed in window title and about dialog. */
    public static final String APP_NAME = "Tour Route Planner";

    /** Application version. */
    public static final String APP_VERSION = "1.0.0";

    /** GitHub repository URL. */
    public static final String GITHUB_URL = "https://github.com/DoCaoThang568/TourRoutePlanner";

    // ==================== File Paths ====================

    /** Directory for storing application data such as saved routes and places. */
    public static final String DATA_PATH = "data/";

    /** JSON file name for storing the list of saved routes. */
    public static final String ROUTES_FILE = DATA_PATH + "routes.json";

    /** JSON file name for storing the list of saved places. */
    public static final String PLACES_FILE = DATA_PATH + "places.json";

    /** Configuration file name located in src/main/resources. */
    public static final String CONFIG_FILE = "config.properties";

    /** File extension for route files. */
    public static final String JSON_EXTENSION = "*.json";

    // ==================== API Configuration Keys ====================

    /** Config property key for OSRM server URL. */
    public static final String CONFIG_OSRM_URL = "osrm.server.url";

    /** Config property key for Nominatim server URL. */
    public static final String CONFIG_NOMINATIM_URL = "nominatim.server.url";

    /** Config property key for MapTiler API key. */
    public static final String CONFIG_MAPTILER_KEY = "maptiler.api.key";

    /** Config property key for JxBrowser license key. */
    public static final String CONFIG_JXBROWSER_KEY = "jxbrowser.license.key";

    // ==================== Default API URLs ====================

    /** Default OSRM public server URL (fallback). */
    public static final String DEFAULT_OSRM_URL = "http://router.project-osrm.org";

    /** Default Nominatim public server URL (fallback). */
    public static final String DEFAULT_NOMINATIM_URL = "https://nominatim.openstreetmap.org";

    // ==================== HTTP Configuration ====================

    /** User-Agent header for API requests. */
    public static final String USER_AGENT = "TourRoutePlanner/1.0 (" + GITHUB_URL + ")";

    /** HTTP connection timeout in milliseconds. */
    public static final int HTTP_TIMEOUT_MS = 10000;

    // ==================== Map Configuration ====================

    /** Default map center latitude (Hanoi, Vietnam). */
    public static final double DEFAULT_MAP_LAT = 21.0278;

    /** Default map center longitude (Hanoi, Vietnam). */
    public static final double DEFAULT_MAP_LNG = 105.8342;

    /** Default map zoom level. */
    public static final int DEFAULT_MAP_ZOOM = 6;

    /** Zoom level when focusing on a single place. */
    public static final int PLACE_FOCUS_ZOOM = 15;

    /** Maximum zoom level for route fitting. */
    public static final int MAX_ROUTE_ZOOM = 16;

    // ==================== Search Configuration ====================

    /** Minimum characters required to trigger search. */
    public static final int MIN_SEARCH_CHARS = 2;

    /** Debounce delay for search suggestions in milliseconds. */
    public static final int SEARCH_DEBOUNCE_MS = 300;

    /** Maximum number of search results to fetch. */
    public static final int MAX_SEARCH_RESULTS = 20;

    /** Minimum places required to calculate a route. */
    public static final int MIN_ROUTE_PLACES = 2;

    // ==================== UI Configuration ====================

    /** Dark mode CSS class name. */
    public static final String DARK_MODE_CLASS = "dark-mode";

    /** Light mode emoji for toggle button. */
    public static final String LIGHT_MODE_EMOJI = "🌞";

    /** Dark mode emoji for toggle button. */
    public static final String DARK_MODE_EMOJI = "🌙";
}