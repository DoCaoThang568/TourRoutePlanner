package tourrouteplanner.model;

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
     * Khởi tạo một đối tượng Place mới với đầy đủ thông tin.
     * @param placeId ID duy nhất của địa điểm.
     * @param name Tên của địa điểm.
     * @param latitude Vĩ độ của địa điểm.
     * @param longitude Kinh độ của địa điểm.
     * @param address Địa chỉ đầy đủ của địa điểm.
     */
    public Place(String placeId, String name, double latitude, double longitude, String address) {
        this.placeId = placeId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }

    /**
     * Khởi tạo một đối tượng Place mới với tên, vĩ độ và kinh độ.
     * ID địa điểm và địa chỉ sẽ được đặt là null ban đầu.
     * @param name Tên của địa điểm.
     * @param latitude Vĩ độ của địa điểm.
     * @param longitude Kinh độ của địa điểm.
     */
    public Place(String name, double latitude, double longitude) {
        this(null, name, latitude, longitude, null); // Gọi hàm khởi tạo chính với placeId và address là null
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

    @Override
    public String toString() {
        // Định dạng chuỗi biểu diễn đối tượng Place, hữu ích cho việc hiển thị trong ListView hoặc logs.
        return name + (address != null && !address.isEmpty() ? " (" + address + ")" : "") +
               " [" + String.format(Locale.US, "%.5f, %.5f", latitude, longitude) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Place place = (Place) o;
        // Ưu tiên so sánh bằng placeId nếu có, vì nó thường là duy nhất
        if (placeId != null && place.placeId != null) {
            return placeId.equals(place.placeId);
        }
        // Nếu một trong hai hoặc cả hai placeId là null, so sánh dựa trên tên, vĩ độ và kinh độ.
        return Double.compare(place.latitude, latitude) == 0 &&
               Double.compare(place.longitude, longitude) == 0 &&
               Objects.equals(name, place.name) && // Thêm so sánh địa chỉ nếu cần độ chính xác cao hơn
               Objects.equals(address, place.address); // Cân nhắc: việc so sánh địa chỉ có thể quá nghiêm ngặt
    }

    @Override
    public int hashCode() {
        // Ưu tiên sử dụng hashCode từ placeId nếu có, vì nó đảm bảo tính duy nhất tốt hơn.
        if (placeId != null) {
            return Objects.hash(placeId);
        }
        // Nếu không có placeId, tính hashCode dựa trên tên, vĩ độ, kinh độ và địa chỉ.
        // Việc thêm địa chỉ vào hashCode phụ thuộc vào logic của phương thức equals.
        return Objects.hash(name, latitude, longitude, address);
    }
}