package tourrouteplanner.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;

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
import java.util.stream.Collectors;

/**
 * Service class responsible for handling route and place-related requests.
 * Interacts with external APIs such as OSRM (Open Source Routing Machine) for
 * route calculation
 * and Nominatim for place search (geocoding) and address lookup from
 * coordinates (reverse geocoding).
 */
public class RouteService {
    /** URL of the OSRM server used for route calculation requests. */
    private String osrmServerUrl;
    /** URL of the Nominatim server used for geocoding services. */
    private String nominatimServerUrl;
    /** Stores the most recently calculated {@link Route} object. */
    private Route lastCalculatedRoute;

    /**
     * Stores the normalized query string from the last place search.
     * Used for post-processing and sorting search results for better relevance.
     */
    public static String lastNormalizedQuery = "";

    /**
     * Creates a {@code RouteService} object.
     * Loads URL configuration for OSRM and Nominatim servers from
     * {@code config.properties}.
     */
    public RouteService() {
        loadConfig();
    }

    /**
     * Loads server URL configuration from {@code config.properties}.
     * If file is not found or an error occurs, uses default URLs.
     */
    private void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Error: config.properties file not found. Using default URLs.");
                // Assign default values if config file not found
                osrmServerUrl = "http://router.project-osrm.org"; // Public OSRM URL
                nominatimServerUrl = "https://nominatim.openstreetmap.org"; // Public Nominatim URL
                return;
            }
            prop.load(input);
            osrmServerUrl = prop.getProperty("osrm.server.url", "http://router.project-osrm.org");
            nominatimServerUrl = prop.getProperty("nominatim.server.url", "https://nominatim.openstreetmap.org");
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Error reading config.properties, using default URLs.");
            osrmServerUrl = "http://router.project-osrm.org";
            nominatimServerUrl = "https://nominatim.openstreetmap.org";
        }
    }

    /**
     * Gets route information from OSRM server for a given list of places
     * (waypoints).
     * 
     * @param places List of {@link Place} objects representing waypoints. Must
     *               contain at least 2 places.
     * @return A {@link Route} object containing route details (path, distance,
     *         duration).
     *         Returns {@code null} if route cannot be found or an error occurs
     *         during processing.
     * @throws IOException              If there is a network connection error or
     *                                  error communicating with OSRM API.
     * @throws IllegalArgumentException If the {@code places} list is {@code null}
     *                                  or contains fewer than 2 places.
     * @throws IllegalStateException    If the OSRM server URL is not properly
     *                                  configured.
     */
    public Route getRoute(List<Place> places) throws IOException {
        if (places == null || places.size() < 2) {
            throw new IllegalArgumentException("At least 2 places are required to find a route.");
        }
        if (osrmServerUrl == null || osrmServerUrl.trim().isEmpty()) {
            System.err.println("OSRM server URL is not configured in config.properties or is invalid.");
            throw new IllegalStateException("OSRM server URL is not configured.");
        }

        // Format coordinate string for OSRM: longitude,latitude;longitude,latitude;...
        String coordinatesString = places.stream()
                .map(p -> String.format(Locale.US, "%.5f,%.5f", p.getLongitude(), p.getLatitude()))
                .collect(Collectors.joining(";"));

        // Build URL for OSRM API - Add steps=true to get detailed instructions
        String apiUrl = String.format(
                "%s/route/v1/driving/%s?overview=full&geometries=geojson&alternatives=false&steps=true",
                osrmServerUrl, coordinatesString);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        // Add User-Agent to comply with some public OSRM service requirements
        connection.setRequestProperty("User-Agent",
                "TourRoutePlanner/1.0 (https://github.com/DoCaoThang568/TourRoutePlanner)");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            // Pass 'places' (original waypoints) to parseOSRMResponse
            Route calculatedRoute = parseOSRMResponse(response.toString(), places);
            this.lastCalculatedRoute = calculatedRoute; // Store the calculated route
            return calculatedRoute;
        } else {
            System.err.println("Error from OSRM API: " + responseCode + " - " + connection.getResponseMessage());
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                System.err.println("Error details: " + errorResponse.toString());
                throw new IOException(
                        "Error calling OSRM routing API: " + responseCode + ". Details: " + errorResponse.toString());
            } catch (IOException e) {
                throw new IOException(
                        "Error calling OSRM routing API: " + responseCode + ". Could not read error details.");
            }
        }
    }

    /**
     * Parses JSON response string from OSRM API to create a {@link Route} object.
     * 
     * @param jsonResponse      JSON string received from OSRM.
     * @param originalWaypoints List of original {@link Place} used to request the
     *                          route.
     *                          This information is used to initialize the
     *                          {@code Route} object.
     * @return A {@link Route} object parsed from JSON,
     *         or {@code null} if JSON is invalid, route not found, or other errors
     *         occur.
     */
    private Route parseOSRMResponse(String jsonResponse, List<Place> originalWaypoints) {
        JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        String code = responseObject.get("code").getAsString();

        if (!"Ok".equalsIgnoreCase(code)) {
            System.err.print("OSRM API returned error code: " + code);
            if (responseObject.has("message")) {
                System.err.println(". Message: " + responseObject.get("message").getAsString());
            } else {
                System.err.println(".");
            }
            return null;
        }

        JsonArray routesArray = responseObject.getAsJsonArray("routes");
        if (routesArray.isEmpty()) {
            System.err.println("No route found in OSRM response.");
            return null;
        }

        JsonObject routeObject = routesArray.get(0).getAsJsonObject(); // Get first route (usually only one if
                                                                       // alternatives=false)

        double totalDistanceMeters = routeObject.get("distance").getAsDouble();
        double totalDurationSeconds = routeObject.get("duration").getAsDouble();

        List<Route.Coordinate> polyline = new ArrayList<>();
        if (routeObject.has("geometry") && routeObject.getAsJsonObject("geometry").has("coordinates")) {
            JsonArray coordinatesArray = routeObject.getAsJsonObject("geometry").getAsJsonArray("coordinates");
            for (int i = 0; i < coordinatesArray.size(); i++) {
                JsonArray coordPair = coordinatesArray.get(i).getAsJsonArray();
                // OSRM GeoJSON returns coordinates in order: [longitude, latitude]
                double lng = coordPair.get(0).getAsDouble();
                double lat = coordPair.get(1).getAsDouble();
                polyline.add(new Route.Coordinate(lat, lng));
            }
        }

        // Extract turn-by-turn instructions
        StringBuilder turnByTurnInstructionsBuilder = new StringBuilder();
        if (routeObject.has("legs") && routeObject.getAsJsonArray("legs").size() > 0) {
            JsonArray legsArray = routeObject.getAsJsonArray("legs");
            for (int i = 0; i < legsArray.size(); i++) {
                JsonObject legObject = legsArray.get(i).getAsJsonObject();
                if (legObject.has("steps")) {
                    JsonArray stepsArray = legObject.getAsJsonArray("steps");
                    for (int j = 0; j < stepsArray.size(); j++) {
                        JsonObject stepObject = stepsArray.get(j).getAsJsonObject();

                        String streetNameForStep = "";
                        if (stepObject.has("name") && !stepObject.get("name").isJsonNull()) {
                            streetNameForStep = stepObject.get("name").getAsString();
                        }
                        String rotaryNameForStep = "";
                        if (stepObject.has("rotary_name") && !stepObject.get("rotary_name").isJsonNull()) {
                            rotaryNameForStep = stepObject.get("rotary_name").getAsString();
                        }

                        if (stepObject.has("maneuver")) {
                            JsonObject maneuverObj = stepObject.getAsJsonObject("maneuver");
                            if (maneuverObj.has("instruction") && !maneuverObj.get("instruction").isJsonNull()) {
                                String instruction = maneuverObj.get("instruction").getAsString();
                                if (instruction != null && !instruction.trim().isEmpty()) {
                                    turnByTurnInstructionsBuilder.append(instruction.trim()).append("\n");
                                }
                            } else {
                                // Fallback: Generate instruction from type, modifier, and name
                                String generatedInstruction = generateSimpleInstruction(maneuverObj, streetNameForStep,
                                        rotaryNameForStep);
                                if (generatedInstruction != null && !generatedInstruction.isEmpty()) {
                                    turnByTurnInstructionsBuilder.append(generatedInstruction).append("\n");
                                }
                            }
                        }
                    }
                }
            }
        }

        return new Route(originalWaypoints, polyline, totalDistanceMeters, totalDurationSeconds,
                turnByTurnInstructionsBuilder.toString());
    }

    private String generateSimpleInstruction(JsonObject maneuverObj, String streetName, String rotaryName) {
        if (maneuverObj == null)
            return "";

        String type = maneuverObj.has("type") ? maneuverObj.get("type").getAsString() : "";
        String modifier = maneuverObj.has("modifier") ? maneuverObj.get("modifier").getAsString() : "";

        StringBuilder instruction = new StringBuilder();

        switch (type.toLowerCase()) {
            case "depart":
                instruction.append("Depart");
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                if (!streetName.isEmpty())
                    instruction.append(" onto ").append(streetName);
                break;
            case "turn":
                instruction.append("Turn");
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                else
                    instruction.append(" in unknown direction");
                if (!streetName.isEmpty())
                    instruction.append(" onto ").append(streetName);
                break;
            case "continue":
                instruction.append("Continue straight");
                if (!modifier.isEmpty() && !modifier.equalsIgnoreCase("straight")) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (!streetName.isEmpty())
                    instruction.append(" on ").append(streetName);
                break;
            case "new name":
                instruction.append("Continue onto new road");
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                if (!streetName.isEmpty())
                    instruction.append(": ").append(streetName);
                break;
            case "arrive":
                instruction.append("Arrive");
                if (!streetName.isEmpty())
                    instruction.append(" at ").append(streetName);
                if (!modifier.isEmpty() && (modifier.equalsIgnoreCase("left") || modifier.equalsIgnoreCase("right"))) {
                    instruction.append(" (on the ").append(translateModifier(modifier)).append(")");
                }
                break;
            case "merge":
                instruction.append("Merge");
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                if (!streetName.isEmpty())
                    instruction.append(" onto ").append(streetName);
                break;
            case "fork":
                instruction.append("Take the fork");
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                else
                    instruction.append(" unknown");
                if (!streetName.isEmpty())
                    instruction.append(" on ").append(streetName);
                break;
            case "end of road":
                instruction.append("At end of road, turn");
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                if (!streetName.isEmpty())
                    instruction.append(" onto ").append(streetName);
                break;
            case "use lane":
                instruction.append("Use lane");
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                if (!streetName.isEmpty())
                    instruction.append(" on ").append(streetName);
                break;
            case "roundabout":
            case "rotary":
                instruction.append("Enter ");
                if (rotaryName != null && !rotaryName.isEmpty()) {
                    instruction.append(rotaryName);
                } else {
                    instruction.append("roundabout");
                }
                if (maneuverObj.has("exit") && !maneuverObj.get("exit").isJsonNull()) {
                    instruction.append(" and take exit ").append(maneuverObj.get("exit").getAsInt());
                }
                if (streetName != null && !streetName.isEmpty()
                        && (rotaryName == null || rotaryName.isEmpty() || !streetName.equals(rotaryName))) {
                    instruction.append(" onto ").append(streetName);
                }
                break;
            case "exit roundabout":
            case "exit rotary":
                instruction.append("Exit ");
                if (rotaryName != null && rotaryName.isEmpty()) {
                    instruction.append(rotaryName);
                } else {
                    instruction.append("roundabout");
                }
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                if (streetName != null && !streetName.isEmpty())
                    instruction.append(" onto ").append(streetName);
                break;
            default:
                if (!type.isEmpty())
                    instruction.append(capitalize(type));
                if (!modifier.isEmpty())
                    instruction.append(" ").append(translateModifier(modifier));
                if (streetName != null && !streetName.isEmpty())
                    instruction.append(" on ").append(streetName);
                if (instruction.length() == 0) {
                    return "";
                }
                break;
        }
        return instruction.toString().trim();
    }

    private String translateModifier(String osrmModifier) {
        if (osrmModifier == null)
            return "";
        switch (osrmModifier.toLowerCase()) {
            case "uturn":
                return "U-turn";
            case "sharp right":
                return "sharp right";
            case "right":
                return "right";
            case "slight right":
                return "slight right";
            case "straight":
                return "straight";
            case "slight left":
                return "slight left";
            case "left":
                return "left";
            case "sharp left":
                return "sharp left";
            default:
                return osrmModifier;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Searches for places based on a query string using Nominatim API.
     * 
     * @param query Search query string (e.g., "Eiffel Tower", "Ho Guom Hanoi").
     * @return A list of {@link Place} objects matching the query.
     *         Returns an empty list if no results found or an error occurs.
     * @throws IOException              If there is a network connection error or
     *                                  error communicating with Nominatim API.
     * @throws IllegalArgumentException If the {@code query} string is {@code null}
     *                                  or empty.
     */
    public List<Place> searchPlaces(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query string must not be empty.");
        }
        if (nominatimServerUrl == null || nominatimServerUrl.trim().isEmpty()) {
            System.err.println("Nominatim server URL is not configured. Returning empty list.");
            return new ArrayList<>();
        }

        // Encode query for URL (UTF-8 encoding)
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());

        // Build URL for Nominatim API
        // Add addressdetails=1 to get address details
        // Add format=json to receive results in JSON format
        // Add limit=20 to limit number of results (adjustable)
        // Add polygon_geojson=1 to get GeoJSON details for polygons
        // Add countrycodes=vn and accept-language=vi for Vietnam focus
        String apiUrl = String.format(
                "%s/search?q=%s&format=json&addressdetails=1&limit=20&polygon_geojson=1&countrycodes=vn&accept-language=vi",
                nominatimServerUrl, encodedQuery);

        // Store normalized query for later use (e.g., for sorting results)
        lastNormalizedQuery = normalizeString(query);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        // Add User-Agent to comply with some public service requirements
        connection.setRequestProperty("User-Agent",
                "TourRoutePlanner/1.0 (https://github.com/DoCaoThang568/TourRoutePlanner)");
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
            return parseNominatimResponse(response.toString());
        } else {
            System.err.println("Error from Nominatim API: " + responseCode + " - " + connection.getResponseMessage());
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                System.err.println("Error details: " + errorResponse.toString());
                throw new IOException("Error calling Nominatim place search API: " + responseCode + ". Details: "
                        + errorResponse.toString());
            } catch (IOException e) {
                throw new IOException("Error calling Nominatim place search API: " + responseCode
                        + ". Could not read error details.");
            }
        }
    }

    /**
     * Parses JSON response string from Nominatim API (for place search) to create a
     * list of {@link Place} objects.
     * 
     * @param jsonResponse JSON string received from Nominatim.
     * @return A list of parsed {@link Place} objects.
     *         Returns an empty list if JSON is invalid or contains no results.
     */
    private List<Place> parseNominatimResponse(String jsonResponse) {
        List<Place> foundPlaces = new ArrayList<>();
        JsonArray resultsArray = JsonParser.parseString(jsonResponse).getAsJsonArray();

        if (resultsArray.isEmpty()) {
            return foundPlaces; // Return empty list
        }

        for (int i = 0; i < resultsArray.size(); i++) {
            JsonObject placeObject = resultsArray.get(i).getAsJsonObject();

            String displayName = placeObject.has("display_name") ? placeObject.get("display_name").getAsString()
                    : "No display name";
            double lat = placeObject.get("lat").getAsDouble();
            double lon = placeObject.get("lon").getAsDouble();

            double[] boundingBox = null;
            if (placeObject.has("boundingbox") && placeObject.get("boundingbox").isJsonArray()) {
                JsonArray bboxArray = placeObject.getAsJsonArray("boundingbox");
                if (bboxArray.size() == 4) {
                    boundingBox = new double[4];
                    boundingBox[0] = bboxArray.get(0).getAsDouble();
                    boundingBox[1] = bboxArray.get(1).getAsDouble();
                    boundingBox[2] = bboxArray.get(2).getAsDouble();
                    boundingBox[3] = bboxArray.get(3).getAsDouble();
                }
            }

            String geoJsonString = null;
            if (placeObject.has("geojson") && placeObject.get("geojson").isJsonObject()) {
                JsonObject geoJsonObject = placeObject.getAsJsonObject("geojson");
                geoJsonString = geoJsonObject.toString();
            }

            double importance = 0.0;
            if (placeObject.has("importance") && placeObject.get("importance").isJsonPrimitive()
                    && placeObject.get("importance").getAsJsonPrimitive().isNumber()) {
                importance = placeObject.get("importance").getAsDouble();
            }

            String placeId = "unknown_id";
            if (placeObject.has("osm_type") && placeObject.has("osm_id")) {
                placeId = placeObject.get("osm_type").getAsString().substring(0, 1).toUpperCase()
                        + placeObject.get("osm_id").getAsString(); // Example: N123, W456, R789
            } else if (placeObject.has("place_id")) {
                placeId = "nominatim_" + placeObject.get("place_id").getAsString();
            }

            // Split displayName into two parts: name and address
            String name = null;
            String address = null;

            // Part 1: Try to split displayName by comma
            int firstCommaIndex = displayName.indexOf(',');
            if (firstCommaIndex > 0) {
                // Default: first part is name
                name = displayName.substring(0, firstCommaIndex).trim();
                // Remaining part is address
                if (firstCommaIndex + 1 < displayName.length()) {
                    address = displayName.substring(firstCommaIndex + 1).trim();
                }
            } else {
                // If no comma, entire displayName is name
                name = displayName;
                address = ""; // Empty address
            }

            // Part 2: Try to extract shorter name and detailed address from addressdetails
            // if available
            if (placeObject.has("addressdetails") && placeObject.get("addressdetails").isJsonObject()) {
                JsonObject addressDetailsObj = placeObject.getAsJsonObject("addressdetails");

                // Try to get name from specific fields
                String extractedName = null;
                if (addressDetailsObj.has("name")) {
                    extractedName = addressDetailsObj.get("name").getAsString();
                } else if (addressDetailsObj.has("road") && addressDetailsObj.has("house_number")) {
                    extractedName = addressDetailsObj.get("house_number").getAsString() + " "
                            + addressDetailsObj.get("road").getAsString();
                } else if (addressDetailsObj.has("road")) {
                    extractedName = addressDetailsObj.get("road").getAsString();
                } else if (addressDetailsObj.has("amenity")) {
                    extractedName = addressDetailsObj.get("amenity").getAsString();
                } else if (addressDetailsObj.has("shop")) {
                    extractedName = addressDetailsObj.get("shop").getAsString();
                } else if (addressDetailsObj.has("tourism")) {
                    extractedName = addressDetailsObj.get("tourism").getAsString();
                }

                if (extractedName != null && !extractedName.isEmpty()) {
                    name = extractedName; // Update name if better one found
                }

                // Build detailed address from components
                StringBuilder sb = new StringBuilder();
                appendAddressComponent(sb, addressDetailsObj, "house_number");
                appendAddressComponent(sb, addressDetailsObj, "road");
                appendAddressComponent(sb, addressDetailsObj, "suburb");
                appendAddressComponent(sb, addressDetailsObj, "village");
                appendAddressComponent(sb, addressDetailsObj, "town");
                appendAddressComponent(sb, addressDetailsObj, "city_district");
                appendAddressComponent(sb, addressDetailsObj, "city");
                appendAddressComponent(sb, addressDetailsObj, "county");
                appendAddressComponent(sb, addressDetailsObj, "state_district");
                appendAddressComponent(sb, addressDetailsObj, "state");
                appendAddressComponent(sb, addressDetailsObj, "postcode");
                appendAddressComponent(sb, addressDetailsObj, "country");

                String constructedAddress = sb.toString().trim();
                if (!constructedAddress.isEmpty()) {
                    // Remove trailing comma if present
                    if (constructedAddress.endsWith(",")) {
                        constructedAddress = constructedAddress.substring(0, constructedAddress.length() - 1);
                    }
                    address = constructedAddress; // Update address if constructed
                }
            }

            // Ensure name and address are not unnecessarily identical
            if (name != null && address != null && name.equalsIgnoreCase(address)) {
                // If name and address are identical, keep name and set address to empty
                address = "";
            }

            // Ensure name is not null or empty
            if (name == null || name.trim().isEmpty()) {
                name = displayName; // Use displayName if no suitable name found
            }

            // Ensure address is not null
            if (address == null) {
                address = ""; // Set to empty string if null
            }

            // Use constructor including importance
            foundPlaces.add(new Place(placeId, name, lat, lon, address, boundingBox, geoJsonString, importance));
        }

        // Sort results by 'importance' in descending order
        foundPlaces.sort((p1, p2) -> Double.compare(p2.getImportance(), p1.getImportance()));

        return foundPlaces;
    }

    /**
     * Utility function to append an address component to StringBuilder if it exists
     * in JsonObject.
     * 
     * @param builder       StringBuilder to append to.
     * @param addressObject JsonObject containing address components.
     * @param key           Key of the address component to get (e.g., "road",
     *                      "city").
     */
    private void appendAddressComponent(StringBuilder builder, JsonObject addressObject, String key) {
        if (addressObject.has(key)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(addressObject.get(key).getAsString());
        }
    }

    /**
     * Normalizes an input string by:
     * 1. Converting to non-accented form (removing diacritics).
     * 2. Converting to lowercase.
     * 3. Removing special characters that are not letters, numbers, or spaces.
     * 4. Trimming leading and trailing whitespace.
     * Purpose is to enable more flexible string comparison.
     * 
     * @param input String to normalize.
     * @return Normalized string. Returns empty string if input is {@code null}.
     */
    public static String normalizeString(String input) {
        if (input == null)
            return "";
        String temp = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        temp = temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return temp.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
    }

    /**
     * Finds address (reverse geocode) from a coordinate pair (latitude, longitude)
     * using Nominatim API.
     * 
     * @param lat Latitude of the point to find address for.
     * @param lng Longitude of the point to find address for.
     * @return A {@link Place} object containing address information for the given
     *         coordinates.
     *         Returns {@code null} if no address found, an error occurs, or
     *         Nominatim URL is not configured.
     * @throws IOException           If there is a network connection error or error
     *                               communicating with Nominatim API.
     * @throws IllegalStateException If the Nominatim server URL is not properly
     *                               configured.
     */
    public Place reverseGeocode(double lat, double lng) throws IOException {
        if (nominatimServerUrl == null || nominatimServerUrl.trim().isEmpty()) {
            System.err.println("Nominatim server URL is not configured in config.properties or is invalid.");
            throw new IllegalStateException("Nominatim server URL is not configured.");
        }

        // Determine API path based on Nominatim server URL
        String reversePath = "/reverse";
        if (nominatimServerUrl != null && nominatimServerUrl.startsWith("http://localhost")) {
            reversePath = "/reverse.php"; // Use .php for localhost
        }

        // Use /reverse endpoint for Nominatim (usually /reverse.php or /reverse)
        // zoom=18 to request detailed results at street/building level
        String apiUrl = String.format(Locale.US,
                "%s%s?lat=%f&lon=%f&format=json&addressdetails=1&zoom=18&accept-language=vi",
                nominatimServerUrl,
                reversePath,
                lat, lng);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        // User-Agent is very important when using public Nominatim.
        connection.setRequestProperty("User-Agent",
                "TourRoutePlanner/1.0 (https://github.com/DoCaoThang568/TourRoutePlanner)");

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
            return parseNominatimReverseResponse(response.toString(), lat, lng);
        } else {
            System.err.println("Error from Nominatim Reverse Geocoding API: " + responseCode + " - "
                    + connection.getResponseMessage());
            // Handle error similar to other API call methods (e.g., read errorStream)
            // Currently returns null for simplicity
            return null;
        }
    }

    /**
     * Parses JSON response string from Nominatim API (for reverse geocoding) to
     * create a {@link Place} object.
     * 
     * @param jsonResponse JSON string received from Nominatim.
     * @param originalLat  Original latitude used to request reverse geocoding.
     * @param originalLng  Original longitude used to request reverse geocoding.
     * @return A parsed {@link Place} object.
     *         Returns {@code null} if JSON is invalid, API returns error, or no
     *         address information found.
     */
    private Place parseNominatimReverseResponse(String jsonResponse, double originalLat, double originalLng) {
        JsonObject resultObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

        if (resultObject.has("error")) {
            System.err.println(
                    "Error from Nominatim during reverse geocoding: " + resultObject.get("error").getAsString());
            return null;
        }

        // Get basic information
        // Nominatim may return slightly different coordinates than original, so we can
        // use original or returned coordinates.
        // Here we prefer coordinates returned from API if available, otherwise use
        // original coordinates.
        double lat = resultObject.has("lat") ? resultObject.get("lat").getAsDouble() : originalLat;
        double lon = resultObject.has("lon") ? resultObject.get("lon").getAsDouble() : originalLng;
        String displayName = resultObject.has("display_name") ? resultObject.get("display_name").getAsString()
                : "No display name";

        // Get boundingbox if available from reverse geocoding (less common but
        // possible)
        double[] boundingBox = null;
        if (resultObject.has("boundingbox") && resultObject.get("boundingbox").isJsonArray()) {
            JsonArray bboxArray = resultObject.getAsJsonArray("boundingbox");
            if (bboxArray.size() == 4) {
                boundingBox = new double[4];
                boundingBox[0] = bboxArray.get(0).getAsDouble();
                boundingBox[1] = bboxArray.get(1).getAsDouble();
                boundingBox[2] = bboxArray.get(2).getAsDouble();
                boundingBox[3] = bboxArray.get(3).getAsDouble();
            }
        }

        // Get GeoJSON if available
        String geoJsonString = null;
        if (resultObject.has("geojson") && resultObject.get("geojson").isJsonObject()) {
            JsonObject geoJsonObject = resultObject.getAsJsonObject("geojson");
            geoJsonString = geoJsonObject.toString();
        }

        String placeId = "unknown_id";
        if (resultObject.has("osm_type") && resultObject.has("osm_id")) {
            placeId = resultObject.get("osm_type").getAsString().substring(0, 1).toUpperCase()
                    + resultObject.get("osm_id").getAsString();
        } else if (resultObject.has("place_id")) {
            placeId = "nominatim_" + resultObject.get("place_id").getAsString();
        }

        String name = displayName; // Default name
        String address = displayName; // Default address

        // Try to extract more detailed name and address from 'address' field
        if (resultObject.has("address") && resultObject.get("address").isJsonObject()) {
            JsonObject addressObject = resultObject.getAsJsonObject("address");
            // Name extraction logic similar to parseNominatimResponse
            // Prioritize specific components like street name, POI, etc.
            if (addressObject.has("road")) { // Street name is usually best info for reverse geocoding
                name = addressObject.get("road").getAsString();
                if (addressObject.has("house_number")) {
                    name = addressObject.get("house_number").getAsString() + " " + name;
                }
            } else if (addressObject.has("amenity"))
                name = addressObject.get("amenity").getAsString();
            else if (addressObject.has("shop"))
                name = addressObject.get("shop").getAsString();
            else if (addressObject.has("tourism"))
                name = addressObject.get("tourism").getAsString();
            else if (addressObject.has("city"))
                name = addressObject.get("city").getAsString(); // Last fallback could be city name
            else if (addressObject.has("county"))
                name = addressObject.get("county").getAsString();
            else if (addressObject.has("state"))
                name = addressObject.get("state").getAsString();
            else if (addressObject.has("country"))
                name = addressObject.get("country").getAsString();
            else { // If no specific name, use first part of display_name
                if (displayName.contains(",")) {
                    name = displayName.substring(0, displayName.indexOf(",")).trim();
                } else {
                    name = displayName;
                }
            }

            // Rebuild detailed address from available components
            StringBuilder detailedAddressBuilder = new StringBuilder();
            appendAddressComponent(detailedAddressBuilder, addressObject, "road");
            appendAddressComponent(detailedAddressBuilder, addressObject, "house_number");
            appendAddressComponent(detailedAddressBuilder, addressObject, "suburb");
            appendAddressComponent(detailedAddressBuilder, addressObject, "city_district");
            appendAddressComponent(detailedAddressBuilder, addressObject, "city");
            appendAddressComponent(detailedAddressBuilder, addressObject, "county");
            appendAddressComponent(detailedAddressBuilder, addressObject, "state");
            appendAddressComponent(detailedAddressBuilder, addressObject, "postcode");
            appendAddressComponent(detailedAddressBuilder, addressObject, "country");

            String tempAddress = detailedAddressBuilder.toString().trim();
            if (tempAddress.endsWith(",")) {
                tempAddress = tempAddress.substring(0, tempAddress.length() - 1).trim();
            }
            if (!tempAddress.isEmpty()) {
                address = tempAddress;
            }
        }

        return new Place(placeId, name, lat, lon, address, boundingBox, geoJsonString);
    }

    /**
     * Returns the last {@link Route} object calculated by this service.
     * 
     * @return The last {@code Route} object, or {@code null} if no route has been
     *         successfully calculated yet.
     */
    public Route getLastRoute() {
        return lastCalculatedRoute;
    }
}
