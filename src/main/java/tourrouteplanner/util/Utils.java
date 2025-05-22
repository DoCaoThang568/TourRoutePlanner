package tourrouteplanner.util;

import javafx.scene.control.Alert;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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
     * Tải API key từ tệp cấu hình.
     * @return API key dưới dạng Chuỗi, hoặc null nếu không tìm thấy hoặc có lỗi xảy ra.
     */
    public static String loadApiKey() {
        Properties prop = new Properties();
        try (InputStream input = Utils.class.getClassLoader().getResourceAsStream(Constants.CONFIG_FILE)) {
            if (input == null) {
                System.out.println("Rất tiếc, không thể tìm thấy " + Constants.CONFIG_FILE);
                showAlert(Alert.AlertType.ERROR, "Lỗi cấu hình", "Không tìm thấy tệp config.properties.");
                return null;
            }
            prop.load(input);
            String apiKey = prop.getProperty("google.maps.apikey");
            if (apiKey == null || apiKey.trim().isEmpty() || "YOUR_API_KEY".equals(apiKey)) {
                 showAlert(Alert.AlertType.WARNING, "Cảnh báo API Key", "API Key chưa được cấu hình trong " + Constants.CONFIG_FILE + " hoặc là giá trị mặc định.");
                return null;
            }
            return apiKey;
        } catch (IOException ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi đọc cấu hình", "Không thể đọc API key từ " + Constants.CONFIG_FILE);
            return null;
        }
    }

}