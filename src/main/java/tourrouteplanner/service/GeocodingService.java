package tourrouteplanner.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tourrouteplanner.model.Place;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Service for geocoding operations using Nominatim API.
 * Handles place search (forward geocoding) and address lookup (reverse
 * geocoding).
 */
public class GeocodingService implements IGeocodingService {

    private static final String DEFAULT_NOMINATIM_URL = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "TourRoutePlanner/1.0 (https://github.com/DoCaoThang568/TourRoutePlanner)";

    private String nominatimServerUrl;
    private String lastNormalizedQuery = "";

    /**
     * Creates a GeocodingService and loads configuration.
     */
    public GeocodingService() {
        loadConfig();
    }

    /**
     * Loads server URL configuration from config.properties.
     */
    private void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Error: config.properties file not found. Using default Nominatim URL.");
                nominatimServerUrl = DEFAULT_NOMINATIM_URL;
                return;
            }
            prop.load(input);
            nominatimServerUrl = prop.getProperty("nominatim.server.url", DEFAULT_NOMINATIM_URL);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Error reading config.properties, using default Nominatim URL.");
            nominatimServerUrl = DEFAULT_NOMINATIM_URL;
        }
    }

    @Override
    public List<Place> searchPlaces(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query string must not be empty.");
        }
        if (nominatimServerUrl == null || nominatimServerUrl.trim().isEmpty()) {
            System.err.println("Nominatim server URL is not configured. Returning empty list.");
            return new ArrayList<>();
        }

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
        String apiUrl = String.format(
                "%s/search?q=%s&format=json&addressdetails=1&limit=20&polygon_geojson=1&countrycodes=vn&accept-language=vi",
                nominatimServerUrl, encodedQuery);

        lastNormalizedQuery = normalizeString(query);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.toString());

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return parseSearchResponse(response.toString());
        } else {
            handleErrorResponse(connection, responseCode, "Nominatim place search");
            return new ArrayList<>();
        }
    }

    @Override
    public Place reverseGeocode(double latitude, double longitude) throws IOException {
        if (nominatimServerUrl == null || nominatimServerUrl.trim().isEmpty()) {
            throw new IllegalStateException("Nominatim server URL is not configured.");
        }

        String reversePath = nominatimServerUrl.startsWith("http://localhost") ? "/reverse.php" : "/reverse";
        String apiUrl = String.format(Locale.US,
                "%s%s?lat=%f&lon=%f&format=json&addressdetails=1&zoom=18&accept-language=vi",
                nominatimServerUrl, reversePath, latitude, longitude);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return parseReverseResponse(response.toString(), latitude, longitude);
        } else {
            System.err.println("Error from Nominatim Reverse Geocoding API: " + responseCode);
            return null;
        }
    }

    @Override
    public String getLastNormalizedQuery() {
        return lastNormalizedQuery;
    }

    /**
     * Parses Nominatim search response JSON.
     */
    private List<Place> parseSearchResponse(String jsonResponse) {
        List<Place> foundPlaces = new ArrayList<>();
        JsonArray resultsArray = JsonParser.parseString(jsonResponse).getAsJsonArray();

        if (resultsArray.isEmpty()) {
            return foundPlaces;
        }

        for (int i = 0; i < resultsArray.size(); i++) {
            JsonObject placeObject = resultsArray.get(i).getAsJsonObject();
            Place place = parsePlaceFromJson(placeObject);
            if (place != null) {
                foundPlaces.add(place);
            }
        }

        // Sort by importance descending
        foundPlaces.sort((p1, p2) -> Double.compare(p2.getImportance(), p1.getImportance()));
        return foundPlaces;
    }

    /**
     * Parses a single place from JSON object.
     */
    private Place parsePlaceFromJson(JsonObject placeObject) {
        String displayName = placeObject.has("display_name") ? placeObject.get("display_name").getAsString()
                : "No display name";
        double lat = placeObject.get("lat").getAsDouble();
        double lon = placeObject.get("lon").getAsDouble();

        double[] boundingBox = parseBoundingBox(placeObject);
        String geoJsonString = parseGeoJson(placeObject);
        double importance = parseImportance(placeObject);
        String placeId = parsePlaceId(placeObject);

        // Parse name and address
        String[] nameAndAddress = parseNameAndAddress(placeObject, displayName);
        String name = nameAndAddress[0];
        String address = nameAndAddress[1];

        return new Place(placeId, name, lat, lon, address, boundingBox, geoJsonString, importance);
    }

    /**
     * Parses reverse geocoding response.
     */
    private Place parseReverseResponse(String jsonResponse, double originalLat, double originalLng) {
        JsonObject resultObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

        if (resultObject.has("error")) {
            System.err.println("Error from Nominatim: " + resultObject.get("error").getAsString());
            return null;
        }

        double lat = resultObject.has("lat") ? resultObject.get("lat").getAsDouble() : originalLat;
        double lon = resultObject.has("lon") ? resultObject.get("lon").getAsDouble() : originalLng;
        String displayName = resultObject.has("display_name") ? resultObject.get("display_name").getAsString()
                : "No display name";

        double[] boundingBox = parseBoundingBox(resultObject);
        String geoJsonString = parseGeoJson(resultObject);
        String placeId = parsePlaceId(resultObject);

        String name = displayName;
        String address = displayName;

        if (resultObject.has("address") && resultObject.get("address").isJsonObject()) {
            JsonObject addressObject = resultObject.getAsJsonObject("address");
            name = extractNameFromAddress(addressObject, displayName);
            address = buildDetailedAddress(addressObject);
            if (address.isEmpty()) {
                address = displayName;
            }
        }

        return new Place(placeId, name, lat, lon, address, boundingBox, geoJsonString);
    }

    // Helper methods for parsing

    private double[] parseBoundingBox(JsonObject obj) {
        if (obj.has("boundingbox") && obj.get("boundingbox").isJsonArray()) {
            JsonArray bboxArray = obj.getAsJsonArray("boundingbox");
            if (bboxArray.size() == 4) {
                return new double[] {
                        bboxArray.get(0).getAsDouble(),
                        bboxArray.get(1).getAsDouble(),
                        bboxArray.get(2).getAsDouble(),
                        bboxArray.get(3).getAsDouble()
                };
            }
        }
        return null;
    }

    private String parseGeoJson(JsonObject obj) {
        if (obj.has("geojson") && obj.get("geojson").isJsonObject()) {
            return obj.getAsJsonObject("geojson").toString();
        }
        return null;
    }

    private double parseImportance(JsonObject obj) {
        if (obj.has("importance") && obj.get("importance").isJsonPrimitive()
                && obj.get("importance").getAsJsonPrimitive().isNumber()) {
            return obj.get("importance").getAsDouble();
        }
        return 0.0;
    }

    private String parsePlaceId(JsonObject obj) {
        if (obj.has("osm_type") && obj.has("osm_id")) {
            return obj.get("osm_type").getAsString().substring(0, 1).toUpperCase()
                    + obj.get("osm_id").getAsString();
        } else if (obj.has("place_id")) {
            return "nominatim_" + obj.get("place_id").getAsString();
        }
        return "unknown_id";
    }

    private String[] parseNameAndAddress(JsonObject placeObject, String displayName) {
        String name = null;
        String address = null;

        int firstCommaIndex = displayName.indexOf(',');
        if (firstCommaIndex > 0) {
            name = displayName.substring(0, firstCommaIndex).trim();
            if (firstCommaIndex + 1 < displayName.length()) {
                address = displayName.substring(firstCommaIndex + 1).trim();
            }
        } else {
            name = displayName;
            address = "";
        }

        if (placeObject.has("addressdetails") && placeObject.get("addressdetails").isJsonObject()) {
            JsonObject addressDetailsObj = placeObject.getAsJsonObject("addressdetails");
            String extractedName = extractNameFromAddressDetails(addressDetailsObj);
            if (extractedName != null && !extractedName.isEmpty()) {
                name = extractedName;
            }
            String constructedAddress = buildDetailedAddress(addressDetailsObj);
            if (!constructedAddress.isEmpty()) {
                address = constructedAddress;
            }
        }

        if (name != null && address != null && name.equalsIgnoreCase(address)) {
            address = "";
        }
        if (name == null || name.trim().isEmpty()) {
            name = displayName;
        }
        if (address == null) {
            address = "";
        }

        return new String[] { name, address };
    }

    private String extractNameFromAddressDetails(JsonObject addressObj) {
        if (addressObj.has("name"))
            return addressObj.get("name").getAsString();
        if (addressObj.has("road") && addressObj.has("house_number")) {
            return addressObj.get("house_number").getAsString() + " " + addressObj.get("road").getAsString();
        }
        if (addressObj.has("road"))
            return addressObj.get("road").getAsString();
        if (addressObj.has("amenity"))
            return addressObj.get("amenity").getAsString();
        if (addressObj.has("shop"))
            return addressObj.get("shop").getAsString();
        if (addressObj.has("tourism"))
            return addressObj.get("tourism").getAsString();
        return null;
    }

    private String extractNameFromAddress(JsonObject addressObject, String displayName) {
        if (addressObject.has("road")) {
            String name = addressObject.get("road").getAsString();
            if (addressObject.has("house_number")) {
                name = addressObject.get("house_number").getAsString() + " " + name;
            }
            return name;
        }
        if (addressObject.has("amenity"))
            return addressObject.get("amenity").getAsString();
        if (addressObject.has("shop"))
            return addressObject.get("shop").getAsString();
        if (addressObject.has("tourism"))
            return addressObject.get("tourism").getAsString();
        if (addressObject.has("city"))
            return addressObject.get("city").getAsString();
        if (addressObject.has("county"))
            return addressObject.get("county").getAsString();
        if (addressObject.has("state"))
            return addressObject.get("state").getAsString();
        if (addressObject.has("country"))
            return addressObject.get("country").getAsString();

        if (displayName.contains(",")) {
            return displayName.substring(0, displayName.indexOf(",")).trim();
        }
        return displayName;
    }

    private String buildDetailedAddress(JsonObject addressObj) {
        StringBuilder sb = new StringBuilder();
        appendAddressComponent(sb, addressObj, "house_number");
        appendAddressComponent(sb, addressObj, "road");
        appendAddressComponent(sb, addressObj, "suburb");
        appendAddressComponent(sb, addressObj, "village");
        appendAddressComponent(sb, addressObj, "town");
        appendAddressComponent(sb, addressObj, "city_district");
        appendAddressComponent(sb, addressObj, "city");
        appendAddressComponent(sb, addressObj, "county");
        appendAddressComponent(sb, addressObj, "state_district");
        appendAddressComponent(sb, addressObj, "state");
        appendAddressComponent(sb, addressObj, "postcode");
        appendAddressComponent(sb, addressObj, "country");

        String result = sb.toString().trim();
        if (result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void appendAddressComponent(StringBuilder builder, JsonObject addressObject, String key) {
        if (addressObject.has(key)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(addressObject.get(key).getAsString());
        }
    }

    private void handleErrorResponse(HttpURLConnection connection, int responseCode, String apiName)
            throws IOException {
        System.err.println("Error from " + apiName + " API: " + responseCode + " - " + connection.getResponseMessage());
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
            StringBuilder errorResponse = new StringBuilder();
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                errorResponse.append(errorLine);
            }
            System.err.println("Error details: " + errorResponse.toString());
            throw new IOException(
                    "Error calling " + apiName + " API: " + responseCode + ". Details: " + errorResponse.toString());
        } catch (IOException e) {
            throw new IOException(
                    "Error calling " + apiName + " API: " + responseCode + ". Could not read error details.");
        }
    }

    /**
     * Normalizes an input string for comparison.
     */
    public static String normalizeString(String input) {
        if (input == null)
            return "";
        String temp = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        temp = temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return temp.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
    }
}
