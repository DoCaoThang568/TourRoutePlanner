package tourrouteplanner.util;

/**
 * Centralized UI messages and strings for the application.
 * Enables easier localization and consistent messaging.
 */
public final class Messages {

    private Messages() {
        // Prevent instantiation
    }

    // ==================== Status Bar Messages ====================

    public static final String STATUS_READY = "Ready";
    public static final String STATUS_SEARCHING = "Searching...";
    public static final String STATUS_LOADING_MAP = "Loading map...";
    public static final String STATUS_CALCULATING_ROUTE = "Calculating route...";
    public static final String STATUS_ROUTE_FOUND = "Route found successfully!";
    public static final String STATUS_SAVING = "Saving...";
    public static final String STATUS_LOADING = "Loading...";

    // ==================== Alert Titles ====================

    public static final String ALERT_INFO_TITLE = "Information";
    public static final String ALERT_WARNING_TITLE = "Warning";
    public static final String ALERT_ERROR_TITLE = "Error";
    public static final String ALERT_CONFIRM_TITLE = "Confirm";
    public static final String ALERT_ABOUT_TITLE = "About " + Constants.APP_NAME;

    // ==================== Error Messages ====================

    public static final String ERROR_SEARCH_FAILED = "Could not search for places. Please check your internet connection.";
    public static final String ERROR_ROUTE_FAILED = "Could not calculate route. Please try again.";
    public static final String ERROR_SAVE_FAILED = "Could not save route. Please check file permissions.";
    public static final String ERROR_LOAD_FAILED = "Could not load route file. The file may be corrupted or in wrong format.";
    public static final String ERROR_REVERSE_GEOCODE_FAILED = "Could not get address for this location.";
    public static final String ERROR_MIN_PLACES = "Please add at least 2 places to calculate a route.";
    public static final String ERROR_MAP_INIT_FAILED = "Could not initialize map. Please check your configuration.";
    public static final String ERROR_CONFIG_NOT_FOUND = "Configuration file not found. Using default settings.";

    // ==================== Warning Messages ====================

    public static final String WARN_PLACE_EXISTS = "This place is already in your route.";
    public static final String WARN_CLEAR_ALL = "Are you sure you want to remove all places from the route?";
    public static final String WARN_UNSAVED_CHANGES = "You have unsaved changes. Do you want to save before closing?";

    // ==================== Success Messages ====================

    public static final String SUCCESS_ROUTE_SAVED = "Route saved successfully!";
    public static final String SUCCESS_ROUTE_LOADED = "Route loaded successfully!";
    public static final String SUCCESS_PLACE_ADDED = "Place added to route.";

    // ==================== Button Labels ====================

    public static final String BTN_SEARCH = "Search";
    public static final String BTN_FIND_ROUTE = "🚀 Find Optimal Route";
    public static final String BTN_REMOVE = "Remove";
    public static final String BTN_CLEAR_ALL = "Clear All";
    public static final String BTN_SAVE = "Save";
    public static final String BTN_LOAD = "Load";
    public static final String BTN_OK = "OK";
    public static final String BTN_CANCEL = "Cancel";
    public static final String BTN_YES = "Yes";
    public static final String BTN_NO = "No";

    // ==================== Menu Items ====================

    public static final String MENU_FILE = "File";
    public static final String MENU_SAVE_ROUTE = "Save Route...";
    public static final String MENU_LOAD_ROUTE = "Load Route...";
    public static final String MENU_EXIT = "Exit";
    public static final String MENU_HELP = "Help";
    public static final String MENU_ABOUT = "About";

    // ==================== Section Headers ====================

    public static final String HEADER_SEARCH = "Search Places:";
    public static final String HEADER_RESULTS = "Search Results:";
    public static final String HEADER_ROUTE = "Selected Places in Route:";

    // ==================== Placeholders ====================

    public static final String PLACEHOLDER_SEARCH = "Enter place name...";
    public static final String PLACEHOLDER_RESULTS = "Search results will appear here";
    public static final String PLACEHOLDER_ROUTE = "No places selected yet";

    // ==================== Tooltips ====================

    public static final String TOOLTIP_SEARCH = "Enter at least 2 characters to search for places. Suggestions will appear automatically.";
    public static final String TOOLTIP_SEARCH_BTN = "Search for places";
    public static final String TOOLTIP_FIND_ROUTE = "Find optimal route for selected places";
    public static final String TOOLTIP_REMOVE = "Remove selected place from route";
    public static final String TOOLTIP_CLEAR_ALL = "Clear all places from route";
    public static final String TOOLTIP_MOVE_UP = "Move up";
    public static final String TOOLTIP_MOVE_DOWN = "Move down";
    public static final String TOOLTIP_DARK_MODE = "Switch to dark mode";
    public static final String TOOLTIP_LIGHT_MODE = "Switch to light mode";

    // ==================== Table Headers ====================

    public static final String TABLE_COL_NAME = "Place Name";
    public static final String TABLE_COL_ADDRESS = "Address";

    // ==================== Route Info Format ====================

    public static final String ROUTE_INFO_DISTANCE = "Total distance: %.2f km";
    public static final String ROUTE_INFO_DURATION = "Estimated time: %.0f minutes";
    public static final String ROUTE_INFO_PLACES = "Places in route: %d";

    // ==================== File Dialog ====================

    public static final String DIALOG_SAVE_TITLE = "Save Route to JSON File";
    public static final String DIALOG_LOAD_TITLE = "Open Route from JSON File";
    public static final String DIALOG_FILTER_JSON = "JSON Files (*.json)";
    public static final String DIALOG_FILTER_ALL = "All Files (*.*)";

    // ==================== About Dialog ====================

    public static final String ABOUT_CONTENT = String.format(
            "%s v%s\n\n" +
                    "A tour route planning application with interactive maps.\n\n" +
                    "Technologies: JavaFX, JxBrowser, OpenLayers, OSRM, Nominatim\n\n" +
                    "GitHub: %s",
            Constants.APP_NAME, Constants.APP_VERSION, Constants.GITHUB_URL);
}
