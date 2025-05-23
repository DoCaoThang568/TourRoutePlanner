package tourrouteplanner;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import tourrouteplanner.util.Constants;

import java.io.File;
import java.io.IOException;
import javafx.scene.control.Alert;

/**
 * Lớp ứng dụng chính cho TourRoutePlanner.
 * Khởi tạo giao diện người dùng JavaFX, tải tệp FXML,
 * và quản lý vòng đời của ứng dụng, bao gồm cả việc khởi tạo và tắt JxBrowser.
 */
public class Main extends Application {

    private MainController mainController; // Lưu trữ instance của controller để gọi phương thức shutdown

    /**
     * Điểm vào chính cho tất cả các ứng dụng JavaFX.
     * Phương thức start được gọi sau khi phương thức init đã trả về,
     * và sau khi hệ thống sẵn sàng để ứng dụng bắt đầu chạy.
     * Phương thức này tải tệp FXML, thiết lập controller, và hiển thị cửa sổ chính.
     *
     * @param primaryStage sân khấu chính cho ứng dụng này, nơi mà
     * cảnh ứng dụng có thể được thiết lập.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            // Đảm bảo thư mục dữ liệu tồn tại trước khi ứng dụng khởi chạy
            File dataDir = new File(Constants.DATA_PATH);
            if (!dataDir.exists()) {
                if (dataDir.mkdirs()) {
                    System.out.println("Thư mục dữ liệu '" + Constants.DATA_PATH + "' đã được tạo.");
                } else {
                    System.err.println("Không thể tạo thư mục dữ liệu '" + Constants.DATA_PATH + "'. Vui lòng kiểm tra quyền ghi.");
                    // Cân nhắc hiển thị Alert cho người dùng ở đây nếu việc tạo thư mục là bắt buộc
                }
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("Main.fxml"));
            Parent root = loader.load();
            mainController = loader.getController(); // Lấy instance của controller

            primaryStage.setTitle(Constants.APP_NAME);
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            // Xử lý lỗi nghiêm trọng khi không thể tải FXML
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi Khởi Động Nghiêm Trọng");
            alert.setHeaderText("Không thể khởi động ứng dụng TourRoutePlanner");
            alert.setContentText("Đã xảy ra lỗi nghiêm trọng khi tải giao diện người dùng chính (Main.fxml).\\nỨng dụng không thể tiếp tục.\\nChi tiết lỗi: " + e.getMessage());
            alert.showAndWait();
            // Cân nhắc System.exit(1) ở đây nếu ứng dụng không thể hoạt động nếu không có FXML
        } catch (NullPointerException e) {
            e.printStackTrace();
            // Xử lý lỗi khi không tìm thấy tệp FXML
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi Khởi Động Nghiêm Trọng");
            alert.setHeaderText("Không thể tìm thấy tệp giao diện người dùng");
            alert.setContentText("Không tìm thấy tệp Main.fxml. Đảm bảo tệp này nằm đúng trong thư mục resources/tourrouteplanner và đã được build cùng ứng dụng.\\nỨng dụng không thể tiếp tục.");
            alert.showAndWait();
            // Cân nhắc System.exit(1) ở đây
        }
    }

    /**
     * Phương thức này được gọi khi ứng dụng JavaFX kết thúc.
     * Nó đảm bảo rằng các tài nguyên, đặc biệt là JxBrowser, được giải phóng đúng cách.
     *
     * @throws Exception nếu có lỗi xảy ra trong quá trình dừng.
     */
    @Override
    public void stop() throws Exception {
        if (mainController != null) {
            mainController.shutdownJxBrowser(); // Gọi phương thức shutdown của JxBrowser từ controller
        }
        super.stop(); // Gọi phương thức stop của lớp cha
    }

    /**
     * Phương thức main truyền thống của Java.
     * Bị bỏ qua trong ứng dụng JavaFX được triển khai chính xác qua cơ chế của JavaFX.
     * main() chỉ phục vụ như một phương án dự phòng trong trường hợp ứng dụng không thể được
     * khởi chạy thông qua các tạo phẩm triển khai (ví dụ: trong các IDE có hỗ trợ JavaFX hạn chế
     * hoặc khi chạy từ dòng lệnh mà không có JavaFX launcher).
     * NetBeans và các IDE hiện đại thường bỏ qua main() và sử dụng Application.launch().
     *
     * @param args các đối số dòng lệnh (thường không được sử dụng trong ứng dụng JavaFX GUI).
     */
    public static void main(String[] args) {
        launch(args); // Khởi chạy ứng dụng JavaFX
    }
}