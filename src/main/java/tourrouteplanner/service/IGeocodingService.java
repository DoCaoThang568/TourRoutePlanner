package tourrouteplanner.service;

import tourrouteplanner.model.Place;

import java.io.IOException;
import java.util.List;

/**
 * Interface for geocoding operations.
 * Implementations handle communication with geocoding APIs (e.g., Nominatim).
 */
public interface IGeocodingService {

    /**
     * Searches for places matching the given query string.
     *
     * @param query The search query (e.g., place name, address).
     * @return List of matching places, sorted by relevance.
     * @throws IOException              If there's an error communicating with the
     *                                  geocoding API.
     * @throws IllegalArgumentException If query is null or empty.
     */
    List<Place> searchPlaces(String query) throws IOException;

    /**
     * Performs reverse geocoding to find address information for given coordinates.
     *
     * @param latitude  Latitude of the point.
     * @param longitude Longitude of the point.
     * @return A Place object with address information, or null if not found.
     * @throws IOException If there's an error communicating with the geocoding API.
     */
    Place reverseGeocode(double latitude, double longitude) throws IOException;

    /**
     * Returns the normalized query string from the last search operation.
     * Useful for result sorting and relevance scoring.
     *
     * @return The last normalized query, or empty string if no search performed.
     */
    String getLastNormalizedQuery();
}
