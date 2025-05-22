package tourrouteplanner.model;

/**
 * Đại diện cho một địa điểm địa lý với tên, vĩ độ và kinh độ.
 */
public class Place {
    private String name;
    private double lat;
    private double lng;

    /**
     * Khởi tạo một đối tượng Place mới.
     * @param name Tên của địa điểm.
     * @param lat Vĩ độ của địa điểm.
     * @param lng Kinh độ của địa điểm.
     */
    public Place(String name, double lat, double lng) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    /**
     * Lấy tên của địa điểm.
     * @return Tên của địa điểm.
     */
    public String getName() {
        return name;
    }

    /**
     * Đặt tên của địa điểm.
     * @param name Tên mới cho địa điểm.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Lấy vĩ độ của địa điểm.
     * @return Vĩ độ của địa điểm.
     */
    public double getLat() {
        return lat;
    }

    /**
     * Đặt vĩ độ của địa điểm.
     * @param lat Vĩ độ mới cho địa điểm.
     */
    public void setLat(double lat) {
        this.lat = lat;
    }

    /**
     * Lấy kinh độ của địa điểm.
     * @return Kinh độ của địa điểm.
     */
    public double getLng() {
        return lng;
    }

    /**
     * Đặt kinh độ của địa điểm.
     * @param lng Kinh độ mới cho địa điểm.
     */
    public void setLng(double lng) {
        this.lng = lng;
    }

    @Override
    public String toString() {
        // Định dạng để hiển thị trong ListView
        return name + " (" + String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Place place = (Place) o;
        return Double.compare(place.lat, lat) == 0 &&
               Double.compare(place.lng, lng) == 0 &&
               java.util.Objects.equals(name, place.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, lat, lng);
    }
}