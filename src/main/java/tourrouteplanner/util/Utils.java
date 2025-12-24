package tourrouteplanner.util;

import javafx.scene.control.Alert;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.Locale;

/**
 * Utility class containing commonly used helper functions.
 */
public class Utils {

    /**
     * Displays an alert dialog.
     * 
     * @param alertType The type of alert.
     * @param title     The title of the alert.
     * @param message   The content of the alert.
     */
    public static void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Loads a configuration property from the config.properties file.
     * 
     * @param propertyName The name of the property to load.
     * @param defaultValue The default value to return if property is not found or
     *                     an error occurs.
     * @return The property value, or the default value.
     */
    public static String loadConfigProperty(String propertyName, String defaultValue) {
        Properties prop = new Properties();
        String propFileName = Constants.CONFIG_FILE;

        // Attempt 1: Load from classpath (preferred method)
        try (InputStream inputStream = Utils.class.getClassLoader().getResourceAsStream(propFileName)) {
            if (inputStream != null) {
                prop.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String value = prop.getProperty(propertyName);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        } catch (IOException e) {
            // Log error or handle, but currently will proceed to next attempt
            System.err.println("Error loading config from classpath: " + e.getMessage());
        }

        // Attempt 2: Load from explicit target/classes path (fallback)
        prop.clear(); // Clear properties from previous attempt
        Path configPath = Paths.get("target", "classes", propFileName).toAbsolutePath();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                prop.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                String value = prop.getProperty(propertyName);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            } catch (IOException e) {
                // Log error or handle, but currently will return defaultValue
                System.err.println("Error loading config from explicit path: " + e.getMessage());
            }
        }

        // If property not found in any location, return default value
        return defaultValue;
    }

    /**
     * Loads a configuration property from the config.properties file.
     * Returns null if property is not found or an error occurs.
     * 
     * @param propertyName The name of the property to load.
     * @return The property value, or null.
     */
    public static String loadConfigProperty(String propertyName) {
        return loadConfigProperty(propertyName, null);
    }

    /**
     * Loads the API key from the configuration file for map services (e.g.,
     * MapTiler).
     * 
     * @return The API key as a String, or null if not found, error occurs, or value
     *         is placeholder.
     */
    public static String loadApiKey() {
        String apiKey = loadConfigProperty("maptiler.api.key"); // Use key for MapTiler
        if (apiKey == null || apiKey.trim().isEmpty() || "YOUR_MAPTILER_API_KEY".equals(apiKey)) {
            showAlert(Alert.AlertType.WARNING, "API Key Warning", "MapTiler API Key is not configured in "
                    + Constants.CONFIG_FILE + " or is the default placeholder value.");
            return null;
        }
        return apiKey.trim();
    }

    /**
     * Escapes special characters in a string for use in JavaScript.
     * 
     * @param value The string to escape.
     * @return The escaped string.
     */
    public static String escapeJavaScriptString(String value) {
        if (value == null) {
            return ""; // Return empty string for null to avoid JS errors
        }
        return value.replace("\\", "\\\\") // Escape backslashes first
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }

    /**
     * Removes diacritics (accents) from a string.
     * Example: "Hà Nội" becomes "Ha Noi".
     * 
     * @param input The input string.
     * @return The string with diacritics removed.
     */
    public static String removeAccents(String input) {
        if (input == null) {
            return null;
        }
        String nfdNormalizedString = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    /**
     * Normalizes a string for search purposes.
     * Converts to lowercase, removes diacritics, and removes all whitespace.
     * Example: "Hà Nội" -> "hanoi"
     * 
     * @param input The input string.
     * @return The normalized string for searching.
     */
    public static String normalizeForSearch(String input) {
        if (input == null) {
            return null;
        }
        // 1. Convert to lowercase
        String lowercased = input.toLowerCase(Locale.ROOT); // Use Locale.ROOT for consistency
        // 2. Remove diacritics
        String noAccents = removeAccents(lowercased);
        // 3. Remove all whitespace (including in the middle)
        // Keep only letters and numbers, remove other characters and spaces
        return noAccents.replaceAll("[^a-z0-9]", "");
    }
}