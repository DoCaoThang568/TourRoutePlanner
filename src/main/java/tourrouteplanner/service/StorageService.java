package tourrouteplanner.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;
import tourrouteplanner.util.Constants;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp dịch vụ chịu trách nhiệm lưu trữ và tải dữ liệu của ứng dụng.
 * Dữ liệu này bao gồm danh sách các địa điểm ({@link Place}) và thông tin tuyến đường ({@link Route}).
 * Sử dụng định dạng JSON cho việc lưu trữ dữ liệu vào tệp.
 */
public class StorageService {
    /** Đối tượng Gson được sử dụng để chuyển đổi qua lại giữa đối tượng Java và chuỗi JSON. */
    private final Gson gson;

    /**
     * Lớp nội tĩnh (static inner class) đóng gói dữ liệu được tải từ một tệp tuyến đường.
     * Bao gồm danh sách các địa điểm và thông tin chi tiết của tuyến đường.
     */
    public static class LoadedRouteData {
        /** Danh sách các địa điểm (waypoints) thuộc tuyến đường đã tải. */
        private List<Place> places;
        /** Thông tin chi tiết của tuyến đường đã tải. */
        private Route route;

        /**
         * Khởi tạo một đối tượng {@code LoadedRouteData}.
         * @param places Danh sách các {@link Place} của tuyến đường.
         * @param route Đối tượng {@link Route} chứa thông tin tuyến đường.
         */
        public LoadedRouteData(List<Place> places, Route route) {
            this.places = places;
            this.route = route;
        }

        /** Lấy danh sách các địa điểm của tuyến đường đã tải. */
        public List<Place> getPlaces() {
            return places;
        }

        /** Lấy thông tin chi tiết của tuyến đường đã tải. */
        public Route getRoute() {
            return route;
        }
    }

    /**
     * Khởi tạo một đối tượng {@code StorageService}.
     * Cấu hình đối tượng {@link Gson} để tạo ra chuỗi JSON có định dạng "pretty printing" (dễ đọc).
     */
    public StorageService() {
        // Cấu hình Gson để in đẹp (pretty print) giúp tệp JSON dễ đọc hơn bởi người dùng.
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Hiển thị hộp thoại cho phép người dùng chọn vị trí và tên tệp để lưu tuyến đường.
     * @param ownerWindow Cửa sổ ({@link Window}) chủ sở hữu của hộp thoại này. 
     *                    Hộp thoại sẽ được hiển thị theo modal liên quan đến cửa sổ này.
     * @return Đối tượng {@link File} đại diện cho tệp đã được người dùng chọn để lưu.
     *         Trả về {@code null} nếu người dùng hủy bỏ thao tác chọn tệp.
     */
    public File showSaveFileDialog(Window ownerWindow) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Lưu tuyến đường vào tệp JSON");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tệp JSON (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("Tất cả các tệp (*.*)", "*.*")
        );
        // Đặt thư mục ban đầu cho hộp thoại là thư mục dữ liệu của ứng dụng.
        File initialDirectory = new File(Constants.DATA_PATH);
        if (initialDirectory.exists() && initialDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(initialDirectory);
        } else {
            // Nếu thư mục DATA_PATH không tồn tại, có thể đặt thư mục mặc định của người dùng
            // hoặc không đặt gì cả để hệ điều hành tự quyết định.
            System.out.println("Thư mục DATA_PATH không tồn tại, sử dụng thư mục mặc định của hệ thống cho FileChooser.");
        }
        return fileChooser.showSaveDialog(ownerWindow);
    }

    /**
     * Hiển thị hộp thoại cho phép người dùng chọn một tệp để tải (mở) tuyến đường.
     * @param ownerWindow Cửa sổ ({@link Window}) chủ sở hữu của hộp thoại này.
     * @return Đối tượng {@link File} đại diện cho tệp đã được người dùng chọn để mở.
     *         Trả về {@code null} nếu người dùng hủy bỏ thao tác chọn tệp.
     */
    public File showOpenFileDialog(Window ownerWindow) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Tải tuyến đường từ tệp JSON");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tệp JSON (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("Tất cả các tệp (*.*)", "*.*")
        );
        // Đặt thư mục ban đầu cho hộp thoại là thư mục dữ liệu của ứng dụng.
        File initialDirectory = new File(Constants.DATA_PATH);
        if (initialDirectory.exists() && initialDirectory.isDirectory()) {
            fileChooser.setInitialDirectory(initialDirectory);
        } else {
            System.out.println("Thư mục DATA_PATH không tồn tại, sử dụng thư mục mặc định của hệ thống cho FileChooser.");
        }
        return fileChooser.showOpenDialog(ownerWindow);
    }

    /**
     * Lưu trữ thông tin của một tuyến đường, bao gồm danh sách các địa điểm và chi tiết tuyến đường,
     * vào một tệp được chỉ định dưới định dạng JSON.
     * @param file Đối tượng {@link File} nơi dữ liệu tuyến đường sẽ được lưu.
     * @param places Danh sách các {@link Place} (điểm tham chiếu) của tuyến đường.
     * @param route Đối tượng {@link Route} chứa thông tin chi tiết của tuyến đường.
     * @return {@code true} nếu quá trình lưu trữ thành công; {@code false} nếu có lỗi xảy ra (ví dụ: {@link IOException}).
     */
    public boolean saveRoute(File file, List<Place> places, Route route) {
        LoadedRouteData dataToSave = new LoadedRouteData(places, route);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(dataToSave, writer);
            System.out.println("Tuyến đường đã được lưu thành công vào: " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("Lỗi khi lưu tuyến đường vào tệp " + file.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
            // Cân nhắc hiển thị thông báo lỗi cho người dùng qua UI.
            return false;
        }
    }

    /**
     * Tải dữ liệu tuyến đường (bao gồm danh sách địa điểm và thông tin tuyến đường) từ một tệp JSON.
     * @param file Đối tượng {@link File} từ đó dữ liệu sẽ được tải.
     * @return Một đối tượng {@link LoadedRouteData} chứa dữ liệu đã tải.
     *         Trả về {@code null} nếu tệp không tìm thấy, có lỗi đọc tệp, hoặc lỗi cú pháp JSON.
     */
    public LoadedRouteData loadRoute(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            System.err.println("Tệp không hợp lệ hoặc không thể đọc: " + (file != null ? file.getAbsolutePath() : "null"));
            return null;
        }
        try (Reader reader = new FileReader(file)) {
            Type dataType = new TypeToken<LoadedRouteData>() {}.getType();
            LoadedRouteData loadedData = gson.fromJson(reader, dataType);
            if (loadedData != null) {
                System.out.println("Tuyến đường đã được tải thành công từ: " + file.getAbsolutePath());
            } else {
                System.err.println("Không thể phân tích dữ liệu tuyến đường từ tệp (kết quả null): " + file.getAbsolutePath());
            }
            return loadedData;
        } catch (FileNotFoundException e) {
            System.err.println("Không tìm thấy tệp khi tải tuyến đường: " + file.getAbsolutePath());
            // Thông báo này có thể không cần thiết nếu đã kiểm tra file.exists() ở trên.
            return null;
        } catch (IOException e) {
            System.err.println("Lỗi IO khi tải tuyến đường từ tệp " + file.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("Lỗi cú pháp JSON trong tệp tuyến đường " + file.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Lưu danh sách các địa điểm ({@link Place}) vào một tệp JSON tại đường dẫn được chỉ định.
     * Phương thức này thường được sử dụng để lưu các địa điểm yêu thích hoặc đã tìm kiếm.
     * @param places Danh sách các đối tượng {@link Place} cần lưu.
     * @param filePath Đường dẫn tuyệt đối đến tệp nơi danh sách địa điểm sẽ được lưu.
     * @return {@code true} nếu quá trình lưu trữ thành công; {@code false} nếu có lỗi xảy ra.
     */
    public boolean savePlaces(List<Place> places, String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            System.err.println("Đường dẫn tệp để lưu địa điểm không được rỗng.");
            return false;
        }
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(places, writer);
            System.out.println("Danh sách địa điểm đã được lưu thành công vào: " + filePath);
            return true;
        } catch (IOException e) {
            System.err.println("Lỗi khi lưu danh sách địa điểm vào tệp " + filePath + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Tải danh sách các địa điểm ({@link Place}) từ một tệp JSON tại đường dẫn được chỉ định.
     * @param filePath Đường dẫn tuyệt đối đến tệp từ đó danh sách địa điểm sẽ được tải.
     * @return Một {@link List} các đối tượng {@link Place} đã được tải.
     *         Trả về một danh sách rỗng mới nếu tệp không tìm thấy, có lỗi đọc tệp, lỗi cú pháp JSON,
     *         hoặc nếu tệp JSON chứa giá trị {@code null}.
     */
    public List<Place> loadPlaces(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            System.err.println("Đường dẫn tệp để tải địa điểm không được rỗng.");
            return new ArrayList<>(); // Trả về danh sách rỗng thay vì null
        }
        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            System.err.println("Tệp địa điểm không tồn tại hoặc không thể đọc: " + filePath);
            return new ArrayList<>(); // Trả về danh sách rỗng thay vì null
        }

        try (Reader reader = new FileReader(filePath)) {
            Type placeListType = new TypeToken<ArrayList<Place>>() {}.getType();
            List<Place> loadedPlaces = gson.fromJson(reader, placeListType);
            if (loadedPlaces != null) {
                System.out.println("Danh sách địa điểm đã được tải thành công từ: " + filePath);
                return loadedPlaces;
            } else {
                System.err.println("Không thể phân tích dữ liệu địa điểm từ tệp (kết quả null): " + filePath + ". Trả về danh sách rỗng.");
                return new ArrayList<>(); // Đảm bảo không bao giờ trả về null
            }
        } catch (FileNotFoundException e) {
            // Trường hợp này ít khi xảy ra do đã kiểm tra file.exists() ở trên.
            System.err.println("Không tìm thấy tệp khi tải địa điểm: " + filePath);
            return new ArrayList<>();
        } catch (IOException e) {
            System.err.println("Lỗi IO khi tải địa điểm từ tệp " + filePath + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("Lỗi cú pháp JSON trong tệp địa điểm " + filePath + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Đảm bảo rằng thư mục dữ liệu của ứng dụng (được định nghĩa trong {@link Constants#DATA_PATH}) tồn tại.
     * Nếu thư mục này không tồn tại, phương thức sẽ cố gắng tạo nó (bao gồm cả các thư mục cha nếu cần).
     */
    public void ensureDataDirectoryExists() {
        File dataDir = new File(Constants.DATA_PATH);
        if (!dataDir.exists()) {
            if (dataDir.mkdirs()) {
                System.out.println("Thư mục " + Constants.DATA_PATH + " đã được tạo bởi StorageService.");
            } else {
                System.err.println("StorageService: Không thể tạo thư mục " + Constants.DATA_PATH);
            }
        }
    }
}