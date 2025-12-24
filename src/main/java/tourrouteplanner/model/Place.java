package tourrouteplanner.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.Locale;

/**
 * Represents a geographical location with name, latitude, longitude, and
 * related information.
 */
public class Place {
    /** Unique ID of the place, typically provided by an external API service. */
    private String placeId;
    /** Name of the place. */
    private String name;
    /** Geographic latitude of the place. */
    private double latitude;
    /** Geographic longitude of the place. */
    private double longitude;
    /** Full, human-readable address of the place. */
    private String address;
    /**
     * Bounding box of the place, if available.
     * Stored as [minLatitude, maxLatitude, minLongitude, maxLongitude]
     * or [southLat, northLat, westLon, eastLon].
     */
    private double[] boundingBox; // Example: [southLat, northLat, westLon, eastLon]

    /**
     * JSON string representing the geographic geometry of the place.
     * Can be Point, LineString, Polygon, etc.
     * Used to draw the detailed shape of the place on the map.
     */
    private String geoJson;
    private double importance;

    /**
     * Creates a new Place object with full information, including bounding box,
     * GeoJSON, and importance.
     * 
     * @param placeId     Unique ID of the place.
     * @param name        Name of the place.
     * @param latitude    Latitude of the place.
     * @param longitude   Longitude of the place.
     * @param address     Full address of the place.
     * @param boundingBox Bounding box of the place [minLat, maxLat, minLon,
     *                    maxLon]. Can be null.
     * @param geoJson     GeoJSON string describing the shape of the place. Can be
     *                    null.
     * @param importance  Importance level of the place provided by Nominatim.
     */
    public Place(String placeId, String name, double latitude, double longitude, String address, double[] boundingBox,
            String geoJson, double importance) {
        this.placeId = placeId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.boundingBox = boundingBox;
        this.geoJson = geoJson;
        this.importance = importance;
    }

    /**
     * Creates a new Place object with full information, including bounding box and
     * GeoJSON.
     * Importance will be set to default value (e.g., 0.0).
     * 
     * @param placeId     Unique ID of the place.
     * @param name        Name of the place.
     * @param latitude    Latitude of the place.
     * @param longitude   Longitude of the place.
     * @param address     Full address of the place.
     * @param boundingBox Bounding box of the place [minLat, maxLat, minLon,
     *                    maxLon]. Can be null.
     * @param geoJson     GeoJSON string describing the shape of the place. Can be
     *                    null.
     */
    public Place(String placeId, String name, double latitude, double longitude, String address, double[] boundingBox,
            String geoJson) {
        this(placeId, name, latitude, longitude, address, boundingBox, geoJson, 0.0); // Call main constructor with
                                                                                      // default importance
    }

    /**
     * Creates a new Place object with name, latitude, and longitude.
     * Place ID, address, and boundingBox will be set to null initially.
     * 
     * @param name      Name of the place.
     * @param latitude  Latitude of the place.
     * @param longitude Longitude of the place.
     */
    public Place(String name, double latitude, double longitude) {
        this(null, name, latitude, longitude, null, null, null, 0.0); // Call main constructor with default values
    }

    /**
     * Gets the place ID.
     * 
     * @return The place ID.
     */
    public String getPlaceId() {
        return placeId;
    }

    /**
     * Sets the place ID.
     * 
     * @param placeId The new place ID.
     */
    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    /**
     * Gets the name of the place.
     * 
     * @return The name of the place.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the place.
     * 
     * @param name The new name for the place.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the latitude of the place.
     * 
     * @return The latitude of the place.
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Sets the latitude of the place.
     * 
     * @param latitude The new latitude for the place.
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * Gets the longitude of the place.
     * 
     * @return The longitude of the place.
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Sets the longitude of the place.
     * 
     * @param longitude The new longitude for the place.
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * Gets the address of the place.
     * 
     * @return The address of the place.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the address of the place.
     * 
     * @param address The new address for the place.
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Gets the bounding box of the place.
     * 
     * @return Array containing [minLat, maxLat, minLon, maxLon], or null if not
     *         available.
     */
    public double[] getBoundingBox() {
        return boundingBox;
    }

    /**
     * Sets the bounding box for the place.
     * 
     * @param boundingBox Array containing [minLat, maxLat, minLon, maxLon].
     */
    public void setBoundingBox(double[] boundingBox) {
        this.boundingBox = boundingBox;
    }

    /**
     * Gets the GeoJSON string of the place.
     * 
     * @return The GeoJSON string, or null if not available.
     */
    public String getGeoJson() {
        return geoJson;
    }

    /**
     * Sets the GeoJSON string for the place.
     * 
     * @param geoJson The GeoJSON string.
     */
    public void setGeoJson(String geoJson) {
        this.geoJson = geoJson;
    }

    /**
     * Gets the importance level of the place.
     * 
     * @return The importance level.
     */
    public double getImportance() {
        return importance;
    }

    /**
     * Sets the importance level for the place.
     * 
     * @param importance The new importance level.
     */
    public void setImportance(double importance) {
        this.importance = importance;
    }

    /**
     * Returns a string representation of the Place object.
     * Primarily used for debugging and display in ListView/TableView (if no custom
     * cell factory is set).
     * 
     * @return String representation of the Place object.
     */
    @Override
    public String toString() {
        // Format coordinates with fixed decimal places for better readability
        String latStr = String.format(Locale.US, "%.5f", latitude);
        String lonStr = String.format(Locale.US, "%.5f", longitude);
        String bboxStr = boundingBox != null ? Arrays.toString(boundingBox) : "N/A";
        String geoJsonStr = geoJson != null && !geoJson.isEmpty() ? "Present" : "N/A"; // Just indicate whether GeoJSON
                                                                                       // exists
        return String.format("%s (ID: %s, Lat: %s, Lon: %s, Address: %s, BBox: %s, GeoJSON: %s)",
                name, placeId, latStr, lonStr, address, bboxStr, geoJsonStr);
    }

    /**
     * Compares this object to another object to check if they are equal.
     * Two Place objects are considered equal if they have the same placeId (if both
     * are not null),
     * or if placeId is null, comparison is based on name, latitude, and longitude.
     * 
     * @param o The object to compare.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Place place = (Place) o;
        // If both placeIds exist and are equal, consider them equal
        if (placeId != null && place.placeId != null) {
            return placeId.equals(place.placeId);
        }
        // If one or both placeIds are null, compare based on other properties
        // This is important to avoid adding identical places to the list when ID is not
        // available
        return Double.compare(place.latitude, latitude) == 0 &&
                Double.compare(place.longitude, longitude) == 0 &&
                Objects.equals(name, place.name) &&
                Objects.equals(address, place.address); // Add address to comparison for better accuracy
    }

    /**
     * Returns the hash code for this object.
     * Calculated based on placeId if available, otherwise based on name, latitude,
     * and longitude.
     * 
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        // Use placeId for hashCode if it's not null to ensure consistency with equals
        if (placeId != null) {
            return Objects.hash(placeId);
        }
        // If placeId is null, use other properties
        return Objects.hash(name, latitude, longitude, address); // Add address
    }
}