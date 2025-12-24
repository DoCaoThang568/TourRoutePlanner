package tourrouteplanner.model;

import java.util.List;

/**
 * Represents a calculated route, including waypoints,
 * detailed path, and summary information such as distance and travel time.
 */
public class Route {
    /** List of places (waypoints) in order within the route. */
    private List<Place> waypoints;
    /**
     * List of coordinate pairs (latitude, longitude) describing the detailed path
     * of the route.
     */
    private List<Coordinate> coordinates;
    /** Total distance of the route, in meters (m). */
    private double totalDistanceMeters;
    /** Estimated total travel time of the route, in seconds (s). */
    private double totalDurationSeconds;
    /** Turn-by-turn navigation instructions for the route. */
    private String turnByTurnInstructions;

    /**
     * Creates a new Route object.
     * 
     * @param waypoints              List of {@link Place} (waypoints) in the route,
     *                               in order.
     * @param coordinates            List of {@link Coordinate} defining the route
     *                               path shape.
     * @param totalDistanceMeters    Total distance of the route, in meters.
     * @param totalDurationSeconds   Estimated total travel time of the route, in
     *                               seconds.
     * @param turnByTurnInstructions Turn-by-turn navigation instructions for the
     *                               route.
     */
    public Route(List<Place> waypoints, List<Coordinate> coordinates, double totalDistanceMeters,
            double totalDurationSeconds, String turnByTurnInstructions) {
        this.waypoints = waypoints;
        this.coordinates = coordinates;
        this.totalDistanceMeters = totalDistanceMeters;
        this.totalDurationSeconds = totalDurationSeconds;
        this.turnByTurnInstructions = turnByTurnInstructions;
    }

    /** Gets the list of waypoints of the route. */
    public List<Place> getWaypoints() {
        return waypoints;
    }

    /** Gets the list of coordinates describing the route path. */
    public List<Coordinate> getCoordinates() {
        return coordinates;
    }

    /** Gets the total distance of the route (km). */
    public double getTotalDistanceKm() {
        return this.totalDistanceMeters / 1000.0;
    }

    /** Gets the estimated total travel time of the route (minutes). */
    public double getTotalDurationMinutes() {
        return this.totalDurationSeconds / 60.0;
    }

    /** Gets the turn-by-turn navigation instructions for the route. */
    public String getTurnByTurnInstructions() {
        return turnByTurnInstructions;
    }

    /**
     * Static inner class representing a geographic coordinate (latitude and
     * longitude).
     * Used to describe points along the path of a {@link Route}.
     */
    public static class Coordinate {
        /** Latitude of the coordinate point. */
        private double latitude;
        /** Longitude of the coordinate point. */
        private double longitude;

        /**
         * Creates a new Coordinate object.
         * 
         * @param latitude  Latitude of the coordinate.
         * @param longitude Longitude of the coordinate.
         */
        public Coordinate(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /** Gets the latitude of the coordinate. */
        public double getLatitude() {
            return latitude;
        }

        /** Gets the longitude of the coordinate. */
        public double getLongitude() {
            return longitude;
        }
    }
}