package tourrouteplanner.util;

/**
 * Lớp chứa các giá trị hằng số được sử dụng trong toàn bộ ứng dụng.
 */
public class Constants {
    public static final String APP_NAME = "Tour Route Planner";
    /** Thư mục lưu trữ dữ liệu ứng dụng như các tuyến đường và địa điểm đã lưu. */
    public static final String DATA_PATH = "data/";
    public static final String ROUTES_FILE = DATA_PATH + "routes.json";
    public static final String PLACES_FILE = DATA_PATH + "places.json";
    /** Tên của tệp cấu hình nằm trong src/main/resources. */
    public static final String CONFIG_FILE = "config.properties";

    // API Key được đọc từ config.properties, không hardcode ở đây.
}