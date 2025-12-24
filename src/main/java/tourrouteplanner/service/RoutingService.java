package tourrouteplanner.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;
import tourrouteplanner.model.Route.Coordinate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Service for routing operations using OSRM API.
 * Handles route calculation between waypoints.
 */
public class RoutingService implements IRoutingService {

    private static final String DEFAULT_OSRM_URL = "http://router.project-osrm.org";
    private static final String USER_AGENT = "TourRoutePlanner/1.0 (https://github.com/DoCaoThang568/TourRoutePlanner)";

    private String osrmServerUrl;
    private Route lastRoute;
    private final InstructionFormatter instructionFormatter;

    /**
     * Creates a RoutingService and loads configuration.
     */
    public RoutingService() {
        this.instructionFormatter = new InstructionFormatter();
        loadConfig();
    }

    /**
     * Loads server URL configuration from config.properties.
     */
    private void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Error: config.properties file not found. Using default OSRM URL.");
                osrmServerUrl = DEFAULT_OSRM_URL;
                return;
            }
            prop.load(input);
            osrmServerUrl = prop.getProperty("osrm.server.url", DEFAULT_OSRM_URL);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Error reading config.properties, using default OSRM URL.");
            osrmServerUrl = DEFAULT_OSRM_URL;
        }
    }

    @Override
    public Route getRoute(List<Place> waypoints) throws IOException {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("Routing requires at least 2 waypoints.");
        }
        if (osrmServerUrl == null || osrmServerUrl.isEmpty()) {
            throw new IllegalStateException("OSRM server URL is not configured.");
        }

        String coordinatesParam = waypoints.stream()
                .map(p -> String.format(Locale.US, "%f,%f", p.getLongitude(), p.getLatitude()))
                .collect(Collectors.joining(";"));

        String apiUrl = String.format("%s/route/v1/driving/%s?overview=full&geometries=geojson&steps=true",
                osrmServerUrl, coordinatesParam);

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

            Route route = parseRouteResponse(response.toString(), waypoints);
            this.lastRoute = route;
            return route;
        } else {
            handleErrorResponse(connection, responseCode);
            return null;
        }
    }

    @Override
    public Route getLastRoute() {
        return lastRoute;
    }

    /**
     * Parses OSRM route response JSON.
     */
    private Route parseRouteResponse(String jsonResponse, List<Place> waypoints) {
        JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

        String code = responseObject.has("code") ? responseObject.get("code").getAsString() : "";
        if (!"Ok".equalsIgnoreCase(code)) {
            String message = responseObject.has("message") ? responseObject.get("message").getAsString()
                    : "Unknown error";
            System.err.println("OSRM API error: " + message);
            return null;
        }

        if (!responseObject.has("routes") || !responseObject.get("routes").isJsonArray()
                || responseObject.getAsJsonArray("routes").isEmpty()) {
            System.err.println("OSRM response contains no routes.");
            return null;
        }

        JsonObject routeObject = responseObject.getAsJsonArray("routes").get(0).getAsJsonObject();

        // Parse distance (meters to km) and duration (seconds to minutes)
        double distanceInKm = routeObject.get("distance").getAsDouble() / 1000.0;
        double durationInMinutes = routeObject.get("duration").getAsDouble() / 60.0;

        // Parse coordinates
        List<Coordinate> pathCoordinates = parseCoordinates(routeObject);

        // Parse turn-by-turn instructions
        String instructions = parseInstructions(routeObject);

        return new Route(waypoints, pathCoordinates, distanceInKm * 1000, durationInMinutes * 60, instructions);
    }

    /**
     * Parses route coordinates from OSRM response.
     */
    private List<Coordinate> parseCoordinates(JsonObject routeObject) {
        List<Coordinate> coordinates = new ArrayList<>();

        if (!routeObject.has("geometry") || !routeObject.get("geometry").isJsonObject()) {
            return coordinates;
        }

        JsonObject geometryObject = routeObject.getAsJsonObject("geometry");
        if (!geometryObject.has("coordinates") || !geometryObject.get("coordinates").isJsonArray()) {
            return coordinates;
        }

        JsonArray coordsArray = geometryObject.getAsJsonArray("coordinates");
        for (int i = 0; i < coordsArray.size(); i++) {
            JsonArray pointArray = coordsArray.get(i).getAsJsonArray();
            if (pointArray.size() >= 2) {
                double lng = pointArray.get(0).getAsDouble();
                double lat = pointArray.get(1).getAsDouble();
                coordinates.add(new Coordinate(lat, lng));
            }
        }

        return coordinates;
    }

    /**
     * Parses turn-by-turn instructions from OSRM response.
     */
    private String parseInstructions(JsonObject routeObject) {
        List<String> instructions = new ArrayList<>();

        if (!routeObject.has("legs") || !routeObject.get("legs").isJsonArray()) {
            return "";
        }

        JsonArray legsArray = routeObject.getAsJsonArray("legs");
        int stepGlobalIndex = 0;

        for (int legIndex = 0; legIndex < legsArray.size(); legIndex++) {
            JsonObject legObj = legsArray.get(legIndex).getAsJsonObject();

            if (!legObj.has("steps") || !legObj.get("steps").isJsonArray()) {
                continue;
            }

            JsonArray stepsArray = legObj.getAsJsonArray("steps");
            for (int stepIndex = 0; stepIndex < stepsArray.size(); stepIndex++) {
                JsonObject stepObj = stepsArray.get(stepIndex).getAsJsonObject();

                if (!stepObj.has("maneuver") || !stepObj.get("maneuver").isJsonObject()) {
                    continue;
                }

                JsonObject maneuverObj = stepObj.getAsJsonObject("maneuver");
                String stepName = stepObj.has("name") ? stepObj.get("name").getAsString() : "";
                String rotaryName = stepObj.has("rotary_name") ? stepObj.get("rotary_name").getAsString() : "";
                double stepDistance = stepObj.has("distance") ? stepObj.get("distance").getAsDouble() : 0.0;

                String instruction = instructionFormatter.generateInstruction(maneuverObj, stepName, rotaryName);

                if (!instruction.isEmpty()) {
                    String formattedInstruction = formatInstructionWithDistance(stepGlobalIndex + 1, instruction,
                            stepDistance);
                    instructions.add(formattedInstruction);
                    stepGlobalIndex++;
                }
            }
        }

        return String.join("\n", instructions);
    }

    /**
     * Formats instruction with step number and distance.
     */
    private String formatInstructionWithDistance(int stepNumber, String instruction, double distanceInMeters) {
        String distanceStr;
        if (distanceInMeters >= 1000) {
            distanceStr = String.format(Locale.US, "%.1f km", distanceInMeters / 1000);
        } else {
            distanceStr = String.format(Locale.US, "%.0f m", distanceInMeters);
        }
        return String.format("%d. %s (%s)", stepNumber, instruction, distanceStr);
    }

    /**
     * Handles HTTP error response from OSRM API.
     */
    private void handleErrorResponse(HttpURLConnection connection, int responseCode) throws IOException {
        System.err.println("Error from OSRM API: " + responseCode);
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
            StringBuilder errorResponse = new StringBuilder();
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                errorResponse.append(errorLine);
            }
            System.err.println("Error details: " + errorResponse.toString());
            throw new IOException("OSRM API error: " + responseCode + ". " + errorResponse.toString());
        } catch (Exception e) {
            throw new IOException("OSRM API error: " + responseCode + ". Could not read error details.");
        }
    }
}
