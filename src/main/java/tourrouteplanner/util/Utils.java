package tourrouteplanner.util;

import javafx.scene.control.Alert;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.text.Normalizer; // Added import
import java.util.regex.Pattern; // Added import
import java.util.Locale;

/**
 * Lớp tiện ích chứa các hàm thường dùng.
 */
public class Utils {

    /**
     * Hiển thị một hộp thoại thông báo.
     * @param alertType Kiểu của thông báo.
     * @param title Tiêu đề của thông báo.
     * @param message Nội dung của thông báo.
     */
    public static void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Tải một thuộc tính cấu hình từ tệp config.properties.
     * @param propertyName Tên của thuộc tính cần tải.
     * @param defaultValue Giá trị mặc định sẽ trả về nếu không tìm thấy thuộc tính hoặc có lỗi.
     * @return Giá trị của thuộc tính, hoặc giá trị mặc định.
     */
    public static String loadConfigProperty(String propertyName, String defaultValue) {
        Properties prop = new Properties();
        String propFileName = Constants.CONFIG_FILE;

        // Thử lần 1: Tải từ classpath (phương pháp ưu tiên)
        try (InputStream inputStream = Utils.class.getClassLoader().getResourceAsStream(propFileName)) {
            if (inputStream != null) {
                prop.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String value = prop.getProperty(propertyName);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        } catch (IOException e) {
            // Ghi lại lỗi hoặc xử lý, nhưng hiện tại sẽ chuyển sang thử lần tiếp theo
            System.err.println("Lỗi khi tải cấu hình từ classpath: " + e.getMessage());
        }

        // Thử lần 2: Tải từ đường dẫn cụ thể target/classes (phương án dự phòng)
        prop.clear(); // Xóa các thuộc tính từ lần thử trước
        Path configPath = Paths.get("target", "classes", propFileName).toAbsolutePath();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                prop.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                String value = prop.getProperty(propertyName);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            } catch (IOException e) {
                // Ghi lại lỗi hoặc xử lý, nhưng hiện tại sẽ trả về defaultValue
                 System.err.println("Lỗi khi tải cấu hình từ đường dẫn tường minh: " + e.getMessage());
            }
        }

        // Nếu không tìm thấy thuộc tính ở bất kỳ vị trí nào, trả về giá trị mặc định
        return defaultValue;
    }

    /**
     * Tải một thuộc tính cấu hình từ tệp config.properties.
     * Trả về null nếu không tìm thấy thuộc tính hoặc có lỗi.
     * @param propertyName Tên của thuộc tính cần tải.
     * @return Giá trị của thuộc tính, hoặc null.
     */
    public static String loadConfigProperty(String propertyName) {
        return loadConfigProperty(propertyName, null);
    }

    /**
     * Tải API key từ tệp cấu hình cho dịch vụ bản đồ (ví dụ: MapTiler).
     * @return API key dưới dạng Chuỗi, hoặc null nếu không tìm thấy, có lỗi xảy ra, hoặc giá trị là placeholder.
     */
    public static String loadApiKey() {
        String apiKey = loadConfigProperty("maptiler.api.key"); // Sử dụng key cho MapTiler
        if (apiKey == null || apiKey.trim().isEmpty() || "YOUR_MAPTILER_API_KEY".equals(apiKey)) {
             showAlert(Alert.AlertType.WARNING, "Cảnh báo API Key", "MapTiler API Key chưa được cấu hình trong " + Constants.CONFIG_FILE + " hoặc là giá trị mặc định placeholder.");
            return null;
        }
        return apiKey.trim();
    }

    /**
     * Escapes special characters in a string for use in JavaScript.
     * @param value The string to escape.
     * @return The escaped string.
     */
    public static String escapeJavaScriptString(String value) {
        if (value == null) {
            return ""; // Return empty string for null to avoid JS errors
        }
        return value.replace("\\", "\\\\") // Escape backslashes first
                    .replace("'", "\\'")
                    .replace("\"", "\\\"") // Corrected line: ensure this is a character replacement or use string replacement
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f");
    }

    /**
     * Loại bỏ dấu khỏi một chuỗi ký tự.
     * Ví dụ: "Hà Nội" sẽ trở thành "Ha Noi".
     * @param input Chuỗi đầu vào.
     * @return Chuỗi đã được loại bỏ dấu.
     */
    public static String removeAccents(String input) {
        if (input == null) {
            return null;
        }
        String nfdNormalizedString = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    /**
     * Chuẩn hóa chuỗi cho mục đích tìm kiếm.
     * Chuyển thành chữ thường, loại bỏ dấu và loại bỏ tất cả khoảng trắng.
     * Ví dụ: "Hà Nội" -> "hanoi"
     * @param input Chuỗi đầu vào.
     * @return Chuỗi đã được chuẩn hóa cho tìm kiếm.
     */
    public static String normalizeForSearch(String input) {
        if (input == null) {
            return null;
        }
        // 1. Chuyển sang chữ thường
        String lowercased = input.toLowerCase(Locale.ROOT); // Sử dụng Locale.ROOT cho tính nhất quán
        // 2. Loại bỏ dấu
        String noAccents = removeAccents(lowercased);
        // 3. Loại bỏ tất cả khoảng trắng (kể cả ở giữa)
        // String noSpaces = noAccents.replaceAll("\\s+", ""); // Loại bỏ tất cả các ký tự khoảng trắng
        // return noSpaces;
        // Giữ lại số và chữ cái, loại bỏ các ký tự khác và khoảng trắng
        return noAccents.replaceAll("[^a-z0-9]", "");
    }
}