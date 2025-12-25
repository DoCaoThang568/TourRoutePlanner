package tourrouteplanner.controller;

import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.browser.callback.InjectJsCallback;
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.engine.RenderingMode;
import com.teamdev.jxbrowser.frame.Frame;
import com.teamdev.jxbrowser.js.JsObject;
import com.teamdev.jxbrowser.navigation.event.LoadFinished;
import com.teamdev.jxbrowser.view.javafx.BrowserView;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;
import tourrouteplanner.util.Utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for managing JxBrowser map operations.
 * Handles map initialization, JavaScript communication, and all map-related
 * operations.
 */
public class MapHelper {

    private static final Logger log = LoggerFactory.getLogger(MapHelper.class);
    private Engine engine;
    private Browser browser;
    private BrowserView browserView;
    private final StackPane mapPane;
    private final Object javaConnector;

    /**
     * Creates a MapHelper.
     *
     * @param mapPane       The StackPane container for the map.
     * @param javaConnector The object to expose to JavaScript (typically
     *                      MainController).
     */
    public MapHelper(StackPane mapPane, Object javaConnector) {
        this.mapPane = mapPane;
        this.javaConnector = javaConnector;
    }

    /**
     * Initializes JxBrowser and loads the map.
     *
     * @return true if initialization successful, false otherwise.
     */
    public boolean initialize() {
        String licenseKey = Utils.loadConfigProperty("jxbrowser.license.key");
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            Utils.showAlert(Alert.AlertType.ERROR, "License Key Error",
                    "JxBrowser License Key not found in config.properties.");
            log.error("JxBrowser License Key is missing in config.properties");
            return false;
        }

        try {
            EngineOptions options = EngineOptions.newBuilder(RenderingMode.HARDWARE_ACCELERATED)
                    .licenseKey(licenseKey)
                    .build();
            engine = Engine.newInstance(options);
            browser = engine.newBrowser();

            // Allow JavaScript to call Java methods
            browser.set(InjectJsCallback.class, params -> {
                Frame frame = params.frame();
                JsObject window = frame.executeJavaScript("window");
                if (window != null) {
                    window.putProperty("javaConnector", javaConnector);
                } else {
                    log.warn("Could not get window object from frame");
                }
                return InjectJsCallback.Response.proceed();
            });

            // Log JavaScript console messages
            browser.on(ConsoleMessageReceived.class, event -> {
                String message = "[JS " + event.consoleMessage().level() + "] " + event.consoleMessage().message();
                log.debug(message);
            });

            // Handle map.html load completion
            browser.navigation().on(LoadFinished.class, event -> {
                String loadedUrl = getLoadedUrl(event);
                if (loadedUrl.endsWith("map.html")) {
                    initializeMapWithApiKey();
                }
            });

            browserView = BrowserView.newInstance(browser);
            mapPane.getChildren().add(browserView);

            loadMapHtml();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "JxBrowser Initialization Error",
                    "Could not initialize JxBrowser: " + e.getMessage());
            return false;
        }
    }

    private String getLoadedUrl(LoadFinished event) {
        try {
            if (event.navigation() != null && event.navigation().browser() != null) {
                return event.navigation().browser().url();
            }
        } catch (Exception e) {
            log.warn("Error retrieving URL in LoadFinished: {}", e.getMessage());
        }
        return "unknown";
    }

    private void loadMapHtml() {
        Path mapHtmlPath = Paths.get("src/main/resources/tourrouteplanner/map.html").toAbsolutePath();
        if (Files.exists(mapHtmlPath)) {
            browser.navigation().loadUrl(mapHtmlPath.toUri().toString());
        } else {
            String errorMessage = "map.html not found at: " + mapHtmlPath.toString();
            log.error("map.html not found at: {}", mapHtmlPath);
            Utils.showAlert(Alert.AlertType.ERROR, "Map Loading Error", errorMessage);
        }
    }

    private void initializeMapWithApiKey() {
        String maptilerApiKey = Utils.loadConfigProperty("maptiler.api.key");
        if (maptilerApiKey != null && !maptilerApiKey.trim().isEmpty()) {
            browser.mainFrame().ifPresent(frame -> {
                String script = String.format(
                        "window.MAPTILER_API_KEY = '%s'; if(typeof initializeMapWithApiKey === 'function') { initializeMapWithApiKey(); }",
                        Utils.escapeJavaScriptString(maptilerApiKey));
                frame.executeJavaScript(script);
            });
        } else {
            Utils.showAlert(Alert.AlertType.ERROR, "API Key Error",
                    "Could not load MapTiler API Key from config.properties.");
            // Fallback mode
            browser.mainFrame().ifPresent(frame -> {
                frame.executeJavaScript(
                        "if(typeof initializeMapWithApiKey === 'function') { initializeMapWithApiKey(true); }");
            });
        }
    }

    /**
     * Executes JavaScript in the browser.
     */
    public void executeJavaScript(String script) {
        if (browser != null && browser.mainFrame().isPresent()) {
            browser.mainFrame().get().executeJavaScript(script);
        } else {
            log.warn("Cannot execute JavaScript, browser not ready");
        }
    }

    /**
     * Pans map to coordinates with zoom level.
     */
    public void panTo(double latitude, double longitude, int zoomLevel) {
        String script = String.format(Locale.US,
                "if(typeof panTo === 'function') { panTo(%f, %f, %d); }",
                latitude, longitude, zoomLevel);
        executeJavaScript(script);
    }

    /**
     * Zooms map to bounding box.
     */
    public void zoomToBoundingBox(double southLat, double northLat, double westLon, double eastLon) {
        String script = String.format(Locale.US,
                "if(typeof zoomToBoundingBox === 'function') { zoomToBoundingBox(%f, %f, %f, %f); }",
                southLat, northLat, westLon, eastLon);
        executeJavaScript(script);
    }

    /**
     * Highlights GeoJSON on map.
     */
    public void highlightGeoJson(String geoJsonString) {
        if (geoJsonString != null && !geoJsonString.isEmpty()) {
            String script = String.format("if(typeof highlightGeoJson === 'function') { highlightGeoJson(%s); }",
                    geoJsonString);
            executeJavaScript(script);
        }
    }

    /**
     * Highlights bounding box on map.
     */
    public void highlightBoundingBox(double southLat, double northLat, double westLon, double eastLon) {
        String script = String.format(Locale.US,
                "if(typeof highlightBoundingBox === 'function') { highlightBoundingBox(%f, %f, %f, %f); }",
                southLat, northLat, westLon, eastLon);
        executeJavaScript(script);
    }

    /**
     * Clears highlight from map.
     */
    public void clearHighlight() {
        executeJavaScript("if(typeof clearHighlight === 'function') { clearHighlight(); }");
    }

    /**
     * Adds a marker to the map with an index number.
     */
    public void addMarker(String name, double lat, double lng, String description, int index) {
        String script = String.format(Locale.US, "addMapMarker('%s', %f, %f, '%s', %d);",
                Utils.escapeJavaScriptString(name), lat, lng, Utils.escapeJavaScriptString(description), index);
        executeJavaScript(script);
    }

    /**
     * Draws a route on the map.
     */
    public void drawRoute(List<Route.Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            clearRoute();
            return;
        }

        StringBuilder jsRouteArray = new StringBuilder("[");
        for (int i = 0; i < coordinates.size(); i++) {
            Route.Coordinate coord = coordinates.get(i);
            if (coord != null) {
                jsRouteArray.append(String.format(Locale.US, "{lat: %f, lng: %f}",
                        coord.getLatitude(), coord.getLongitude()));
                if (i < coordinates.size() - 1) {
                    jsRouteArray.append(",");
                }
            }
        }
        jsRouteArray.append("]");
        executeJavaScript(String.format("drawRoute(%s);", jsRouteArray.toString()));
    }

    /**
     * Clears all markers from map.
     */
    public void clearAllMarkers() {
        executeJavaScript("clearAllMarkers();");
    }

    /**
     * Clears route from map.
     */
    public void clearRoute() {
        executeJavaScript("if(typeof clearRoute === 'function') { clearRoute(); }");
    }

    /**
     * Fits map view to show entire route.
     */
    public void fitToRoute() {
        executeJavaScript("if(typeof fitToRoute === 'function') { fitToRoute(); }");
    }

    /**
     * Refreshes markers for all places with their indices.
     */
    public void refreshMarkers(List<Place> places) {
        clearAllMarkers();
        for (int i = 0; i < places.size(); i++) {
            Place place = places.get(i);
            addMarker(place.getName(), place.getLatitude(), place.getLongitude(), place.getAddress(), i + 1);
        }
    }

    /**
     * Shows selected place on map with appropriate highlighting.
     */
    public void showPlace(Place place) {
        if (place == null)
            return;

        String geoJson = place.getGeoJson();
        double[] boundingBox = place.getBoundingBox();

        if (geoJson != null && !geoJson.trim().isEmpty()) {
            highlightGeoJson(geoJson);
            if (boundingBox != null && boundingBox.length == 4) {
                zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
            } else {
                panTo(place.getLatitude(), place.getLongitude(), 15);
            }
        } else if (boundingBox != null && boundingBox.length == 4) {
            highlightBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
            zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
        } else {
            panTo(place.getLatitude(), place.getLongitude(), 15);
            clearHighlight();
        }
    }

    /**
     * Shuts down JxBrowser engine.
     */
    public void shutdown() {
        if (engine != null) {
            engine.close();
            log.info("JxBrowser Engine closed successfully");
        }
    }

    /**
     * Checks if browser is ready.
     */
    public boolean isReady() {
        return browser != null && browser.mainFrame().isPresent();
    }
}
