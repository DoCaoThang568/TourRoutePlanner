package tourrouteplanner.model;

import java.util.List;

/**
 * Đại diện cho một tuyến đường đã được tính toán giữa các điểm tham chiếu.
 */
public class Route {
    private List<Place> waypoints;
    /** Danh sách các tọa độ (vĩ độ, kinh độ) xác định đường đi của tuyến đường. */
    private List<Coordinate> polyline;
    /** Tổng khoảng cách của tuyến đường tính bằng km. */
    private double totalDistance; // km
    /** Tổng thời gian ước tính của tuyến đường tính bằng phút. */
    private double totalDuration; // minutes

    /**
     * Khởi tạo một đối tượng Route mới.
     * @param waypoints Danh sách các điểm tham chiếu (địa điểm) trong tuyến đường.
     * @param polyline Danh sách các tọa độ xác định đường đi của tuyến đường.
     * @param totalDistance Tổng khoảng cách của tuyến đường tính bằng km.
     * @param totalDuration Tổng thời gian của tuyến đường tính bằng phút.
     */
    public Route(List<Place> waypoints, List<Coordinate> polyline, double totalDistance, double totalDuration) {
        this.waypoints = waypoints;
        this.polyline = polyline;
        this.totalDistance = totalDistance;
        this.totalDuration = totalDuration;
    }

    public List<Place> getWaypoints() { return waypoints; }
    public List<Coordinate> getPolyline() { return polyline; }
    public double getTotalDistance() { return totalDistance; }
    public double getTotalDuration() { return totalDuration; }

    /**
     * Lớp nội (inner class) đại diện cho một tọa độ địa lý (vĩ độ và kinh độ).
     */
    public static class Coordinate {
        public double lat, lng;
        public Coordinate(double lat, double lng) { this.lat = lat; this.lng = lng; }
    }
}