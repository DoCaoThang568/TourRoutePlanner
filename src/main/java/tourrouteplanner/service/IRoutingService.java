package tourrouteplanner.service;

import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;

import java.io.IOException;
import java.util.List;

/**
 * Interface for routing operations.
 * Implementations handle communication with routing APIs (e.g., OSRM).
 */
public interface IRoutingService {

    /**
     * Calculates a route between the given waypoints.
     *
     * @param waypoints List of places to route through. Must contain at least 2
     *                  places.
     * @return A Route object containing path coordinates, distance, duration, and
     *         instructions.
     * @throws IOException              If there's an error communicating with the
     *                                  routing API.
     * @throws IllegalArgumentException If waypoints is null or contains fewer than
     *                                  2 places.
     */
    Route getRoute(List<Place> waypoints) throws IOException;

    /**
     * Returns the last successfully calculated route.
     *
     * @return The last Route object, or null if no route has been calculated yet.
     */
    Route getLastRoute();
}
