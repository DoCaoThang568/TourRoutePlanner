package tourrouteplanner.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.Locale;

/**
 * Đại diện cho một địa điểm địa lý với tên, vĩ độ, kinh độ và các thông tin liên quan.
 */
public class Place {
    /** ID duy nhất của địa điểm, thường được cung cấp bởi một dịch vụ API bên ngoài. */
    private String placeId;
    /** Tên của địa điểm. */
    private String name;
    /** Vĩ độ địa lý của địa điểm. */
    private double latitude;
    /** Kinh độ địa lý của địa điểm. */
    private double longitude;
    /** Địa chỉ đầy đủ, dễ đọc của địa điểm. */
    private String address;
    /** 
     * Khung giới hạn của địa điểm, nếu có. 
     * Lưu dưới dạng [minLatitude, maxLatitude, minLongitude, maxLongitude]
     * hoặc [southLat, northLat, westLon, eastLon].
     */
    private double[] boundingBox; // Ví dụ: [southLat, northLat, westLon, eastLon]

    /**
     * Chuỗi JSON đại diện cho đối tượng địa lý (geometry) của địa điểm.
     * Có thể là Point, LineString, Polygon, v.v.
     * Được sử dụng để vẽ hình dạng chi tiết của địa điểm trên bản đồ.
     */
    private String geoJson; // Thêm trường geoJson
    private double importance; // Thêm trường importance

    /**
     * Khởi tạo một đối tượng Place mới với đầy đủ thông tin, bao gồm cả bounding box, GeoJSON và importance.
     * @param placeId ID duy nhất của địa điểm.
     * @param name Tên của địa điểm.
     * @param latitude Vĩ độ của địa điểm.
     * @param longitude Kinh độ của địa điểm.
     * @param address Địa chỉ đầy đủ của địa điểm.
     * @param boundingBox Khung giới hạn của địa điểm [minLat, maxLat, minLon, maxLon]. Có thể là null.
     * @param geoJson Chuỗi GeoJSON mô tả hình dạng của địa điểm. Có thể là null.
     * @param importance Mức độ quan trọng của địa điểm do Nominatim cung cấp.
     */
    public Place(String placeId, String name, double latitude, double longitude, String address, double[] boundingBox, String geoJson, double importance) {
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
     * Khởi tạo một đối tượng Place mới với đầy đủ thông tin, bao gồm cả bounding box và GeoJSON.
     * Importance sẽ được đặt giá trị mặc định (ví dụ: 0.0).
     * @param placeId ID duy nhất của địa điểm.
     * @param name Tên của địa điểm.
     * @param latitude Vĩ độ của địa điểm.
     * @param longitude Kinh độ của địa điểm.
     * @param address Địa chỉ đầy đủ của địa điểm.
     * @param boundingBox Khung giới hạn của địa điểm [minLat, maxLat, minLon, maxLon]. Có thể là null.
     * @param geoJson Chuỗi GeoJSON mô tả hình dạng của địa điểm. Có thể là null.
     */
    public Place(String placeId, String name, double latitude, double longitude, String address, double[] boundingBox, String geoJson) {
        this(placeId, name, latitude, longitude, address, boundingBox, geoJson, 0.0); // Gọi constructor chính với importance mặc định
    }
    
    /**
     * Khởi tạo một đối tượng Place mới với tên, vĩ độ và kinh độ.
     * ID địa điểm, địa chỉ và boundingBox sẽ được đặt là null ban đầu.
     * @param name Tên của địa điểm.
     * @param latitude Vĩ độ của địa điểm.
     * @param longitude Kinh độ của địa điểm.
     */
    public Place(String name, double latitude, double longitude) {
        this(null, name, latitude, longitude, null, null, null, 0.0); // Gọi hàm khởi tạo chính với các giá trị mặc định
    }

    /**
     * Lấy ID của địa điểm.
     * @return ID của địa điểm.
     */
    public String getPlaceId() {
        return placeId;
    }

    /**
     * Đặt ID của địa điểm.
     * @param placeId ID mới cho địa điểm.
     */
    public void setPlaceId(String placeId) {
        this.placeId = placeId;
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
    public double getLatitude() {
        return latitude;
    }

    /**
     * Đặt vĩ độ của địa điểm.
     * @param latitude Vĩ độ mới cho địa điểm.
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * Lấy kinh độ của địa điểm.
     * @return Kinh độ của địa điểm.
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Đặt kinh độ của địa điểm.
     * @param longitude Kinh độ mới cho địa điểm.
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * Lấy địa chỉ của địa điểm.
     * @return Địa chỉ của địa điểm.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Đặt địa chỉ của địa điểm.
     * @param address Địa chỉ mới cho địa điểm.
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Lấy khung giới hạn (bounding box) của địa điểm.
     * @return Mảng double chứa [minLat, maxLat, minLon, maxLon], hoặc null nếu không có.
     */
    public double[] getBoundingBox() {
        return boundingBox;
    }

    /**
     * Đặt khung giới hạn (bounding box) cho địa điểm.
     * @param boundingBox Mảng double chứa [minLat, maxLat, minLon, maxLon].
     */
    public void setBoundingBox(double[] boundingBox) {
        this.boundingBox = boundingBox;
    }

    /**
     * Lấy chuỗi GeoJSON của địa điểm.
     * @return Chuỗi GeoJSON, hoặc null nếu không có.
     */
    public String getGeoJson() {
        return geoJson;
    }

    /**
     * Đặt chuỗi GeoJSON cho địa điểm.
     * @param geoJson Chuỗi GeoJSON.
     */
    public void setGeoJson(String geoJson) {
        this.geoJson = geoJson;
    }

    /**
     * Lấy mức độ quan trọng của địa điểm.
     * @return Mức độ quan trọng.
     */
    public double getImportance() {
        return importance;
    }

    /**
     * Đặt mức độ quan trọng cho địa điểm.
     * @param importance Mức độ quan trọng mới.
     */
    public void setImportance(double importance) {
        this.importance = importance;
    }

    /**
     * Trả về một chuỗi đại diện cho đối tượng Place.
     * Chủ yếu dùng cho mục đích gỡ lỗi và hiển thị trong ListView/TableView (nếu không có cell factory tùy chỉnh).
     * @return Chuỗi đại diện cho đối tượng Place.
     */
    @Override
    public String toString() {
        // Định dạng tọa độ với số chữ số thập phân cố định để dễ đọc hơn
        String latStr = String.format(Locale.US, "%.5f", latitude);
        String lonStr = String.format(Locale.US, "%.5f", longitude);
        String bboxStr = boundingBox != null ? Arrays.toString(boundingBox) : "N/A";
        String geoJsonStr = geoJson != null && !geoJson.isEmpty() ? "Present" : "N/A"; // Chỉ báo có GeoJSON hay không
        return String.format("%s (ID: %s, Lat: %s, Lon: %s, Address: %s, BBox: %s, GeoJSON: %s)", 
                             name, placeId, latStr, lonStr, address, bboxStr, geoJsonStr);
    }

    /**
     * So sánh đối tượng này với một đối tượng khác để xem chúng có bằng nhau không.
     * Hai đối tượng Place được coi là bằng nhau nếu chúng có cùng placeId (nếu cả hai đều không null),
     * hoặc nếu placeId là null thì so sánh dựa trên tên, vĩ độ và kinh độ.
     * @param o Đối tượng cần so sánh.
     * @return true nếu các đối tượng bằng nhau, false nếu ngược lại.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Place place = (Place) o;
        // Nếu cả hai placeId đều có và bằng nhau thì coi là bằng nhau
        if (placeId != null && place.placeId != null) {
            return placeId.equals(place.placeId);
        }
        // Nếu một trong hai hoặc cả hai placeId là null, so sánh dựa trên các thuộc tính khác
        // Điều này quan trọng để tránh việc thêm các địa điểm giống hệt nhau vào danh sách nếu ID không có sẵn
        return Double.compare(place.latitude, latitude) == 0 &&
               Double.compare(place.longitude, longitude) == 0 &&
               Objects.equals(name, place.name) &&
               Objects.equals(address, place.address); // Thêm address vào so sánh để tăng độ chính xác
    }

    /**
     * Trả về mã hash cho đối tượng này.
     * Tính toán dựa trên placeId nếu có, nếu không thì dựa trên tên, vĩ độ và kinh độ.
     * @return Mã hash.
     */
    @Override
    public int hashCode() {
        // Sử dụng placeId cho hashCode nếu nó không null để đảm bảo tính nhất quán với equals
        if (placeId != null) {
            return Objects.hash(placeId);
        }
        // Nếu placeId là null, sử dụng các thuộc tính khác
        return Objects.hash(name, latitude, longitude, address); // Thêm address
    }
}