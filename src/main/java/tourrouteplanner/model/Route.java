package tourrouteplanner.model;

import java.util.List;

/**
 * Đại diện cho một tuyến đường đã được tính toán, bao gồm các điểm tham chiếu,
 * đường đi chi tiết và thông tin tổng hợp như khoảng cách và thời gian di chuyển.
 */
public class Route {
    /** Danh sách các địa điểm (waypoints) theo thứ tự trong tuyến đường. */
    private List<Place> waypoints;
    /** Danh sách các cặp tọa độ (vĩ độ, kinh độ) mô tả chi tiết đường đi của tuyến đường. */
    private List<Coordinate> coordinates;
    /** Tổng khoảng cách của tuyến đường, đơn vị tính bằng mét (m). */
    private double totalDistanceMeters;
    /** Tổng thời gian di chuyển ước tính của tuyến đường, đơn vị tính bằng giây (s). */
    private double totalDurationSeconds;
    /** Hướng dẫn chi tiết từng chặng của tuyến đường. */
    private String turnByTurnInstructions;

    /**
     * Khởi tạo một đối tượng Route mới.
     * @param waypoints Danh sách các {@link Place} (điểm tham chiếu) trong tuyến đường, theo thứ tự.
     * @param coordinates Danh sách các {@link Coordinate} xác định hình dạng đường đi của tuyến đường.
     * @param totalDistanceMeters Tổng khoảng cách của tuyến đường, tính bằng mét.
     * @param totalDurationSeconds Tổng thời gian di chuyển ước tính của tuyến đường, tính bằng giây.
     * @param turnByTurnInstructions Hướng dẫn chi tiết từng chặng của tuyến đường.
     */
    public Route(List<Place> waypoints, List<Coordinate> coordinates, double totalDistanceMeters, double totalDurationSeconds, String turnByTurnInstructions) {
        this.waypoints = waypoints;
        this.coordinates = coordinates;
        this.totalDistanceMeters = totalDistanceMeters;
        this.totalDurationSeconds = totalDurationSeconds;
        this.turnByTurnInstructions = turnByTurnInstructions;
    }

    /** Lấy danh sách các điểm tham chiếu (waypoints) của tuyến đường. */
    public List<Place> getWaypoints() { return waypoints; }
    /** Lấy danh sách các tọa độ mô tả đường đi của tuyến đường. */
    public List<Coordinate> getCoordinates() { return coordinates; }

    /** Lấy tổng khoảng cách của tuyến đường (km). */
    public double getTotalDistanceKm() {
        return this.totalDistanceMeters / 1000.0;
    }

    /** Lấy tổng thời gian di chuyển ước tính của tuyến đường (phút). */
    public double getTotalDurationMinutes() {
        return this.totalDurationSeconds / 60.0;
    }

    /** Lấy hướng dẫn chi tiết từng chặng của tuyến đường. */
    public String getTurnByTurnInstructions() { return turnByTurnInstructions; }

    /**
     * Lớp nội tĩnh (static inner class) đại diện cho một tọa độ địa lý (vĩ độ và kinh độ).
     * Được sử dụng để mô tả các điểm trên đường đi của một {@link Route}.
     */
    public static class Coordinate {
        /** Vĩ độ của điểm tọa độ. */
        private double latitude;
        /** Kinh độ của điểm tọa độ. */
        private double longitude;

        /**
         * Khởi tạo một đối tượng Coordinate mới.
         * @param latitude Vĩ độ của tọa độ.
         * @param longitude Kinh độ của tọa độ.
         */
        public Coordinate(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /** Lấy vĩ độ của tọa độ. */
        public double getLatitude() {
            return latitude;
        }

        /** Lấy kinh độ của tọa độ. */
        public double getLongitude() {
            return longitude;
        }
    }
}