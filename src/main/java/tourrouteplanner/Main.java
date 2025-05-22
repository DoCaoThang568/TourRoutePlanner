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
 * Lớp ứng dụng chính. Khởi tạo và bắt đầu ứng dụng JavaFX.
 */
public class Main extends Application {

    /**
     * Điểm vào chính cho tất cả các ứng dụng JavaFX.
     * Phương thức start được gọi sau khi phương thức init đã trả về,
     * và sau khi hệ thống sẵn sàng để ứng dụng bắt đầu chạy.
     *
     * @param primaryStage sân khấu chính cho ứng dụng này, nơi mà
     * cảnh ứng dụng có thể được thiết lập.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            // Đảm bảo thư mục dữ liệu tồn tại
            File dataDir = new File(Constants.DATA_PATH);
            if (!dataDir.exists()) {
                if (dataDir.mkdirs()) {
                    System.out.println("Thư mục " + Constants.DATA_PATH + " đã được tạo.");
                } else {
                    System.err.println("Không thể tạo thư mục " + Constants.DATA_PATH);
                }
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("Main.fxml"));
            Parent root = loader.load();
            primaryStage.setTitle(Constants.APP_NAME);
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            // Xử lý lỗi nghiêm trọng khi không thể tải FXML
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi Khởi Động");
            alert.setHeaderText("Không thể khởi động ứng dụng");
            alert.setContentText("Đã xảy ra lỗi khi tải giao diện người dùng (Main.fxml).\nChi tiết: " + e.getMessage());
            alert.showAndWait();
        } catch (NullPointerException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi Khởi Động");
            alert.setHeaderText("Không thể tìm thấy tệp FXML");
            alert.setContentText("Không tìm thấy tệp Main.fxml. Đảm bảo tệp nằm đúng trong thư mục resources/tourrouteplanner.");
            alert.showAndWait();
        }
    }

    /**
     * Phương thức main bị bỏ qua trong ứng dụng JavaFX được triển khai chính xác.
     * main() chỉ phục vụ như một phương án dự phòng trong trường hợp ứng dụng không thể được
     * khởi chạy thông qua các tạo phẩm triển khai, ví dụ: trong các IDE có hỗ trợ FX hạn chế.
     * NetBeans bỏ qua main().
     *
     * @param args các đối số dòng lệnh
     */
    public static void main(String[] args) {
        launch(args);
    }
}