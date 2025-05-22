package tourrouteplanner.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import tourrouteplanner.model.Place;
import tourrouteplanner.util.Constants;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp dịch vụ để lưu và tải dữ liệu ứng dụng (ví dụ: địa điểm, tuyến đường) vào/từ tệp.
 */
public class StorageService {
    private final Gson gson;

    /**
     * Khởi tạo một đối tượng StorageService, khởi tạo Gson để xử lý JSON.
     */
    public StorageService() {
        // Cấu hình Gson để in đẹp (pretty print) nếu muốn tệp JSON dễ đọc hơn.
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Lưu danh sách các địa điểm vào một tệp JSON.
     * @param places Danh sách các đối tượng Place cần lưu.
     * @param filePath Đường dẫn tuyệt đối đến tệp nơi các địa điểm sẽ được lưu.
     * @return true nếu lưu thành công, false nếu ngược lại.
     */
    public boolean savePlaces(List<Place> places, String filePath) {
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(places, writer);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Tải danh sách các địa điểm từ một tệp JSON.
     * @param filePath Đường dẫn tuyệt đối đến tệp từ đó các địa điểm sẽ được tải.
     * @return Một Danh sách các đối tượng Place, hoặc null nếu không tìm thấy tệp hoặc có lỗi xảy ra trong quá trình tải/phân tích cú pháp.
     */
    public List<Place> loadPlaces(String filePath) {
        try (Reader reader = new FileReader(filePath)) {
            Type placeListType = new TypeToken<ArrayList<Place>>() {}.getType();
            return gson.fromJson(reader, placeListType);
        } catch (FileNotFoundException e) {
            System.err.println("Không tìm thấy tệp: " + filePath);
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("Lỗi cú pháp JSON trong tệp: " + filePath);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Đảm bảo rằng thư mục dữ liệu được chỉ định bởi Constants.DATA_PATH tồn tại.
     * Nếu nó không tồn tại, phương thức này sẽ cố gắng tạo nó.
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