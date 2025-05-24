package tourrouteplanner.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Lớp dịch vụ chịu trách nhiệm xử lý các yêu cầu liên quan đến tuyến đường và địa điểm.
 * Tương tác với các API bên ngoài như OSRM (Open Source Routing Machine) để tính toán tuyến đường
 * và Nominatim để tìm kiếm địa điểm (geocoding) và tìm địa chỉ từ tọa độ (reverse geocoding).
 */
public class RouteService {
    /** URL của máy chủ OSRM được sử dụng để yêu cầu tính toán tuyến đường. */
    private String osrmServerUrl;
    /** URL của máy chủ Nominatim được sử dụng cho các dịch vụ geocoding. */
    private String nominatimServerUrl;
    /** Lưu trữ đối tượng {@link Route} được tính toán gần đây nhất. */
    private Route lastCalculatedRoute;

    /** 
     * Lưu trữ chuỗi truy vấn đã được chuẩn hóa từ lần tìm kiếm địa điểm cuối cùng.
     * Được sử dụng để hậu xử lý và sắp xếp kết quả tìm kiếm cho phù hợp hơn.
     */
    public static String lastNormalizedQuery = "";

    /**
     * Khởi tạo một đối tượng {@code RouteService}.
     * Tải cấu hình URL cho máy chủ OSRM và Nominatim từ tệp {@code config.properties}.
     */
    public RouteService() { // Xóa tham số apiKey
        loadConfig();
    }

    /**
     * Tải cấu hình URL máy chủ từ tệp {@code config.properties}.
     * Nếu tệp không tìm thấy hoặc có lỗi, sử dụng các URL mặc định.
     */
    private void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Lỗi: Không tìm thấy tệp config.properties. Sử dụng URL mặc định.");
                // Gán giá trị mặc định nếu không tìm thấy tệp config
                osrmServerUrl = "http://router.project-osrm.org"; // URL OSRM công cộng
                nominatimServerUrl = "https://nominatim.openstreetmap.org"; // URL Nominatim công cộng
                return;
            }
            prop.load(input);
            osrmServerUrl = prop.getProperty("osrm.server.url", "http://router.project-osrm.org");
            nominatimServerUrl = prop.getProperty("nominatim.server.url", "https://nominatim.openstreetmap.org"); // Sửa URL mặc định cho Nominatim
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Lỗi khi đọc config.properties, sử dụng URL mặc định.");
            osrmServerUrl = "http://router.project-osrm.org";
            nominatimServerUrl = "https://nominatim.openstreetmap.org"; // Sửa URL mặc định cho Nominatim
        }
    }

    /**
     * Lấy thông tin tuyến đường từ máy chủ OSRM cho một danh sách các địa điểm (waypoints) đã cho.
     * @param places Danh sách các đối tượng {@link Place} đại diện cho các điểm tham chiếu. Phải chứa ít nhất 2 địa điểm.
     * @return Một đối tượng {@link Route} chứa chi tiết tuyến đường (đường đi, khoảng cách, thời gian).
     *         Trả về {@code null} nếu không thể tìm thấy tuyến đường hoặc có lỗi xảy ra trong quá trình xử lý.
     * @throws IOException Nếu có lỗi kết nối mạng hoặc lỗi khi giao tiếp với API OSRM.
     * @throws IllegalArgumentException Nếu danh sách {@code places} là {@code null} hoặc chứa ít hơn 2 địa điểm.
     * @throws IllegalStateException Nếu URL của máy chủ OSRM chưa được cấu hình đúng cách.
     */
    public Route getRoute(List<Place> places) throws IOException { // Thay đổi signature thành List<Place>
        if (places == null || places.size() < 2) {
            throw new IllegalArgumentException("Cần ít nhất 2 địa điểm để tìm lộ trình.");
        }
        if (osrmServerUrl == null || osrmServerUrl.trim().isEmpty()) {
            // Thông báo lỗi rõ ràng hơn cho người dùng hoặc ghi log
            System.err.println("URL của OSRM server chưa được cấu hình trong config.properties hoặc không hợp lệ.");
            throw new IllegalStateException("URL của OSRM server chưa được cấu hình.");
        }

        // Định dạng chuỗi tọa độ cho OSRM: longitude,latitude;longitude,latitude;...
        String coordinatesString = places.stream()
                .map(p -> String.format(Locale.US, "%.5f,%.5f", p.getLongitude(), p.getLatitude()))
                .collect(Collectors.joining(";"));

        // Xây dựng URL cho API OSRM
        String apiUrl = String.format("%s/route/v1/driving/%s?overview=full&geometries=geojson&alternatives=false",
                                    osrmServerUrl, coordinatesString);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        // Thêm User-Agent để tuân thủ quy định của một số dịch vụ OSRM công cộng
        connection.setRequestProperty("User-Agent", "TourRoutePlannerApp/1.0 (your-contact-email@example.com)");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            // Truyền danh sách 'places' (các điểm tham chiếu gốc) vào parseOSRMResponse
            Route calculatedRoute = parseOSRMResponse(response.toString(), places);
            this.lastCalculatedRoute = calculatedRoute; // Lưu lại tuyến đường vừa tính toán
            return calculatedRoute;
        } else {
            System.err.println("Lỗi từ OSRM API: " + responseCode + " - " + connection.getResponseMessage());
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                System.err.println("Chi tiết lỗi: " + errorResponse.toString());
                throw new IOException("Lỗi khi gọi API tìm đường OSRM: " + responseCode + ". Chi tiết: " + errorResponse.toString());
            } catch (IOException e) {
                 throw new IOException("Lỗi khi gọi API tìm đường OSRM: " + responseCode + ". Không thể đọc chi tiết lỗi.");
            }
        }
    }

    /**
     * Phân tích chuỗi JSON phản hồi từ API OSRM để tạo đối tượng {@link Route}.
     * @param jsonResponse Chuỗi JSON nhận được từ OSRM.
     * @param originalWaypoints Danh sách các {@link Place} ban đầu đã được sử dụng để yêu cầu tuyến đường.
     *                          Thông tin này được sử dụng để khởi tạo đối tượng {@code Route}.
     * @return Một đối tượng {@link Route} đã được phân tích từ JSON, 
     *         hoặc {@code null} nếu JSON không hợp lệ, không tìm thấy tuyến đường, hoặc có lỗi khác.
     */
    private Route parseOSRMResponse(String jsonResponse, List<Place> originalWaypoints) {
        JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        String code = responseObject.get("code").getAsString();

        if (!"Ok".equalsIgnoreCase(code)) {
            System.err.print("OSRM API trả về mã lỗi: " + code);
            if (responseObject.has("message")) {
                 System.err.println(". Thông báo: " + responseObject.get("message").getAsString());
            } else {
                System.err.println(".");
            }
            return null; // Hoặc có thể ném một exception cụ thể
        }

        JsonArray routesArray = responseObject.getAsJsonArray("routes");
        if (routesArray.isEmpty()) { // Sử dụng isEmpty() cho rõ ràng
            System.err.println("Không tìm thấy tuyến đường nào trong phản hồi từ OSRM.");
            return null;
        }

        JsonObject routeObject = routesArray.get(0).getAsJsonObject(); // Lấy tuyến đường đầu tiên (thường là duy nhất nếu alternatives=false)

        double totalDistanceMeters = routeObject.get("distance").getAsDouble();
        double totalDurationSeconds = routeObject.get("duration").getAsDouble();

        List<Route.Coordinate> polyline = new ArrayList<>();
        if (routeObject.has("geometry") && routeObject.getAsJsonObject("geometry").has("coordinates")) {
            JsonArray coordinatesArray = routeObject.getAsJsonObject("geometry").getAsJsonArray("coordinates");
            for (int i = 0; i < coordinatesArray.size(); i++) {
                JsonArray coordPair = coordinatesArray.get(i).getAsJsonArray();
                // OSRM GeoJSON trả về tọa độ theo thứ tự: [kinh độ, vĩ độ]
                double lng = coordPair.get(0).getAsDouble();
                double lat = coordPair.get(1).getAsDouble();
                polyline.add(new Route.Coordinate(lat, lng)); // Tạo đối tượng Coordinate
            }
        }
        // Tạo đối tượng Route với các thông tin đã phân tích và danh sách waypoints gốc
        return new Route(originalWaypoints, polyline, totalDistanceMeters / 1000.0, totalDurationSeconds / 60.0);
    }

    /**
     * Tìm kiếm các địa điểm dựa trên một chuỗi truy vấn bằng cách sử dụng API Nominatim.
     * @param query Chuỗi truy vấn tìm kiếm (ví dụ: "Tháp Eiffel", "Hồ Gươm Hà Nội").
     * @return Một danh sách các đối tượng {@link Place} khớp với truy vấn.
     *         Trả về danh sách rỗng nếu không tìm thấy kết quả nào hoặc có lỗi xảy ra.
     * @throws IOException Nếu có lỗi kết nối mạng hoặc lỗi khi giao tiếp với API Nominatim.
     * @throws IllegalArgumentException Nếu chuỗi {@code query} là {@code null} hoặc rỗng.
     */
    public List<Place> searchPlaces(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Chuỗi truy vấn tìm kiếm không được để trống.");
        }
        // Chuẩn hóa và lưu trữ query để sử dụng trong việc sắp xếp kết quả sau này
        String normalizedQuery = normalizeString(query);
        RouteService.lastNormalizedQuery = normalizedQuery;

        // Mã hóa URL query để đảm bảo tính hợp lệ
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());

        // Xác định đường dẫn API dựa trên URL máy chủ Nominatim
        String searchPath = "/search";
        if (nominatimServerUrl != null && nominatimServerUrl.startsWith("http://localhost")) {
            searchPath = "/search.php"; // Sử dụng .php cho localhost
        }

        String apiUrl = String.format(Locale.US,
                "%s%s?q=%s&format=json&limit=10&addressdetails=1&accept-language=vi,en&polygon_geojson=1", // Thêm polygon_geojson=1
                nominatimServerUrl,
                searchPath, // Sử dụng đường dẫn đã xác định
                encodedQuery);
        
        // Đối với Nominatim công cộng, cần User-Agent
        // Đối với Docker instance tự host, có thể không bắt buộc nhưng nên có.
        // URL cho Docker instance thường là http://localhost:8080/search hoặc tương tự

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        // User-Agent rất quan trọng khi sử dụng Nominatim công cộng để tránh bị chặn.
        connection.setRequestProperty("User-Agent", "TourRoutePlannerApp/1.0 (your-contact-email@example.com)");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return parseNominatimResponse(response.toString());
        } else {
            System.err.println("Lỗi từ Nominatim API: " + responseCode + " - " + connection.getResponseMessage());
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                System.err.println("Chi tiết lỗi: " + errorResponse.toString());
                throw new IOException("Lỗi khi gọi API tìm kiếm địa điểm Nominatim: " + responseCode + ". Chi tiết: " + errorResponse.toString());
            } catch (IOException e) {
                throw new IOException("Lỗi khi gọi API tìm kiếm địa điểm Nominatim: " + responseCode + ". Không thể đọc chi tiết lỗi.");
            }
        }
    }

    /**
     * Phân tích chuỗi JSON phản hồi từ API Nominatim (cho tìm kiếm địa điểm) để tạo danh sách các đối tượng {@link Place}.
     * @param jsonResponse Chuỗi JSON nhận được từ Nominatim.
     * @return Một danh sách các {@link Place} đã được phân tích. 
     *         Trả về danh sách rỗng nếu JSON không hợp lệ hoặc không chứa kết quả nào.
     */
    private List<Place> parseNominatimResponse(String jsonResponse) {
        List<Place> foundPlaces = new ArrayList<>();
        JsonArray resultsArray = JsonParser.parseString(jsonResponse).getAsJsonArray();

        if (resultsArray.isEmpty()) { // Sử dụng isEmpty()
            return foundPlaces; // Trả về danh sách rỗng
        }

        for (int i = 0; i < resultsArray.size(); i++) {
            JsonObject placeObject = resultsArray.get(i).getAsJsonObject();
            
            String displayName = placeObject.has("display_name") ? placeObject.get("display_name").getAsString() : "Không có tên hiển thị";
            double lat = placeObject.get("lat").getAsDouble();
            double lon = placeObject.get("lon").getAsDouble();
            
            double[] boundingBox = null;
            if (placeObject.has("boundingbox") && placeObject.get("boundingbox").isJsonArray()) {
                JsonArray bboxArray = placeObject.getAsJsonArray("boundingbox");
                if (bboxArray.size() == 4) {
                    boundingBox = new double[4];
                    boundingBox[0] = bboxArray.get(0).getAsDouble(); 
                    boundingBox[1] = bboxArray.get(1).getAsDouble(); 
                    boundingBox[2] = bboxArray.get(2).getAsDouble(); 
                    boundingBox[3] = bboxArray.get(3).getAsDouble(); 
                }
            }

            String geoJsonString = null;
            if (placeObject.has("geojson") && placeObject.get("geojson").isJsonObject()) {
                JsonObject geoJsonObject = placeObject.getAsJsonObject("geojson");
                geoJsonString = geoJsonObject.toString(); 
            }

            String placeId = "unknown_id";
            if (placeObject.has("osm_type") && placeObject.has("osm_id")) {
                placeId = placeObject.get("osm_type").getAsString().substring(0,1).toUpperCase() + placeObject.get("osm_id").getAsString(); // Ví dụ: N123, W456, R789
            } else if (placeObject.has("place_id")) {
                 placeId = "nominatim_" + placeObject.get("place_id").getAsString();
            }

            String name = displayName; // Tên mặc định là display_name
            String address = displayName; // Địa chỉ mặc định cũng là display_name

            // Cố gắng trích xuất tên ngắn gọn hơn từ addressdetails nếu có
            if (placeObject.has("addressdetails") && placeObject.get("addressdetails").isJsonObject()) {
                JsonObject addressDetails = placeObject.getAsJsonObject("addressdetails");
                // Ưu tiên lấy tên từ các trường cụ thể như name, road, amenity, shop, v.v.
                // Thứ tự ưu tiên có thể cần điều chỉnh tùy theo loại địa điểm mong muốn
                if (addressDetails.has("name")) {
                    name = addressDetails.get("name").getAsString();
                } else if (addressDetails.has("road") && addressDetails.has("house_number")) {
                    name = addressDetails.get("road").getAsString() + " " + addressDetails.get("house_number").getAsString();
                } else if (addressDetails.has("road")) {
                    name = addressDetails.get("road").getAsString();
                } else if (addressDetails.has("amenity")) {
                    name = addressDetails.get("amenity").getAsString();
                } else if (addressDetails.has("shop")) {
                    name = addressDetails.get("shop").getAsString();
                } else if (addressDetails.has("tourism")) {
                    name = addressDetails.get("tourism").getAsString();
                } // Có thể thêm các trường khác như "historic", "office", "leisure", v.v.
            }
            // Nếu tên vẫn giống display_name và display_name chứa dấu phẩy, thử lấy phần đầu tiên làm tên
            if (name.equals(displayName) && displayName.contains(",")) {
                name = displayName.substring(0, displayName.indexOf(',')).trim();
            }

            // Tạo đối tượng Place với đầy đủ thông tin, bao gồm cả boundingBox và geoJsonString
            foundPlaces.add(new Place(placeId, name, lat, lon, address, boundingBox, geoJsonString));
        }

        // Hậu xử lý: Sắp xếp kết quả để ưu tiên các địa điểm có tên bắt đầu bằng hoặc chứa chuỗi truy vấn đã chuẩn hóa.
        String lastQuery = RouteService.lastNormalizedQuery;
        if (lastQuery != null && !lastQuery.isEmpty()) {
            foundPlaces.sort((p1, p2) -> { // Sử dụng sort thay vì stream().toList() để sửa đổi trực tiếp danh sách
                String n1 = normalizeString(p1.getName());
                String n2 = normalizeString(p2.getName());
                boolean p1Starts = n1.startsWith(lastQuery);
                boolean p2Starts = n2.startsWith(lastQuery);
                if (p1Starts && !p2Starts) return -1;
                if (!p1Starts && p2Starts) return 1;
                boolean p1Contains = n1.contains(lastQuery);
                boolean p2Contains = n2.contains(lastQuery);
                if (p1Contains && !p2Contains) return -1;
                if (!p1Contains && p2Contains) return 1;
                return 0;
            });
        }
        return foundPlaces;
    }
    
    /**
     * Hàm tiện ích để nối một thành phần địa chỉ vào StringBuilder nếu nó tồn tại trong JsonObject.
     * @param builder StringBuilder để nối vào.
     * @param addressObject JsonObject chứa các thành phần địa chỉ.
     * @param key Khóa của thành phần địa chỉ cần lấy (ví dụ: "road", "city").
     */
    private void appendAddressComponent(StringBuilder builder, JsonObject addressObject, String key) {
        if (addressObject.has(key)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(addressObject.get(key).getAsString());
        }
    }

    /**
     * Chuẩn hóa một chuỗi đầu vào bằng cách:
     * 1. Chuyển đổi về dạng không dấu (loại bỏ dấu phụ).
     * 2. Chuyển thành chữ thường.
     * 3. Loại bỏ các ký tự đặc biệt không phải là chữ cái, số, hoặc khoảng trắng.
     * 4. Cắt bỏ khoảng trắng thừa ở đầu và cuối chuỗi.
     * Mục đích là để so sánh chuỗi một cách linh hoạt hơn.
     * @param input Chuỗi cần chuẩn hóa.
     * @return Chuỗi đã được chuẩn hóa. Trả về chuỗi rỗng nếu đầu vào là {@code null}.
     */
    public static String normalizeString(String input) {
        if (input == null) return "";
        String temp = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        temp = temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return temp.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
    }

    /**
     * Tìm địa chỉ (reverse geocode) từ một cặp tọa độ (vĩ độ, kinh độ) sử dụng API Nominatim.
     * @param lat Vĩ độ của điểm cần tìm địa chỉ.
     * @param lng Kinh độ của điểm cần tìm địa chỉ.
     * @return Một đối tượng {@link Place} chứa thông tin địa chỉ tương ứng với tọa độ đã cho.
     *         Trả về {@code null} nếu không tìm thấy địa chỉ, có lỗi xảy ra, hoặc URL Nominatim chưa được cấu hình.
     * @throws IOException Nếu có lỗi kết nối mạng hoặc lỗi khi giao tiếp với API Nominatim.
     * @throws IllegalStateException Nếu URL của máy chủ Nominatim chưa được cấu hình đúng cách.
     */
    public Place reverseGeocode(double lat, double lng) throws IOException {
        if (nominatimServerUrl == null || nominatimServerUrl.trim().isEmpty()) {
            System.err.println("URL của Nominatim server chưa được cấu hình trong config.properties hoặc không hợp lệ.");
            throw new IllegalStateException("URL của Nominatim server chưa được cấu hình.");
        }

        // Xác định đường dẫn API dựa trên URL máy chủ Nominatim
        String reversePath = "/reverse";
        if (nominatimServerUrl != null && nominatimServerUrl.startsWith("http://localhost")) {
            reversePath = "/reverse.php"; // Sử dụng .php cho localhost
        }

        // Sử dụng endpoint /reverse cho Nominatim (thường là /reverse.php hoặc /reverse)
        // zoom=18 để yêu cầu kết quả chi tiết ở mức độ đường phố/tòa nhà
        String apiUrl = String.format(Locale.US,
                "%s%s?lat=%f&lon=%f&format=json&addressdetails=1&zoom=18&accept-language=vi,en", // Thêm accept-language
                nominatimServerUrl,
                reversePath, // Sử dụng đường dẫn đã xác định
                lat, lng);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        // User-Agent rất quan trọng khi sử dụng Nominatim công cộng.
        connection.setRequestProperty("User-Agent", "TourRoutePlannerApp/1.0 (your-contact-email@example.com)");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return parseNominatimReverseResponse(response.toString(), lat, lng);
        } else {
            System.err.println("Lỗi từ Nominatim Reverse Geocoding API: " + responseCode + " - " + connection.getResponseMessage());
            // Xử lý lỗi tương tự như các phương thức gọi API khác (ví dụ: đọc errorStream)
            // Hiện tại trả về null để đơn giản
            return null;
        }
    }

    /**
     * Phân tích chuỗi JSON phản hồi từ API Nominatim (cho reverse geocoding) để tạo đối tượng {@link Place}.
     * @param jsonResponse Chuỗi JSON nhận được từ Nominatim.
     * @param originalLat Vĩ độ gốc đã được sử dụng để yêu cầu reverse geocoding.
     * @param originalLng Kinh độ gốc đã được sử dụng để yêu cầu reverse geocoding.
     * @return Một đối tượng {@link Place} đã được phân tích.
     *         Trả về {@code null} nếu JSON không hợp lệ, có lỗi từ API, hoặc không tìm thấy thông tin địa chỉ.
     */
    private Place parseNominatimReverseResponse(String jsonResponse, double originalLat, double originalLng) {
        JsonObject resultObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

        if (resultObject.has("error")) {
            System.err.println("Lỗi từ Nominatim khi reverse geocoding: " + resultObject.get("error").getAsString());
            return null;
        }

        // Lấy các thông tin cơ bản
        // Nominatim có thể trả về tọa độ hơi khác so với tọa độ gốc, nên ta có thể dùng tọa độ gốc hoặc tọa độ trả về.
        // Ở đây ưu tiên tọa độ trả về từ API nếu có, nếu không thì dùng tọa độ gốc.
        double lat = resultObject.has("lat") ? resultObject.get("lat").getAsDouble() : originalLat;
        double lon = resultObject.has("lon") ? resultObject.get("lon").getAsDouble() : originalLng;
        String displayName = resultObject.has("display_name") ? resultObject.get("display_name").getAsString() : "Không có tên hiển thị";
        
        // Lấy boundingbox nếu có từ reverse geocoding (ít phổ biến hơn nhưng có thể có)
        double[] boundingBox = null;
        if (resultObject.has("boundingbox") && resultObject.get("boundingbox").isJsonArray()) {
            JsonArray bboxArray = resultObject.getAsJsonArray("boundingbox");
            if (bboxArray.size() == 4) {
                boundingBox = new double[4];
                boundingBox[0] = bboxArray.get(0).getAsDouble();
                boundingBox[1] = bboxArray.get(1).getAsDouble();
                boundingBox[2] = bboxArray.get(2).getAsDouble();
                boundingBox[3] = bboxArray.get(3).getAsDouble();
            }
        }

        // Lấy GeoJSON nếu có
        String geoJsonString = null;
        if (resultObject.has("geojson") && resultObject.get("geojson").isJsonObject()) {
            JsonObject geoJsonObject = resultObject.getAsJsonObject("geojson");
            geoJsonString = geoJsonObject.toString(); // Chuyển toàn bộ object geojson thành chuỗi
        }

        String placeId = "unknown_id";
         if (resultObject.has("osm_type") && resultObject.has("osm_id")) {
            placeId = resultObject.get("osm_type").getAsString().substring(0,1).toUpperCase() + resultObject.get("osm_id").getAsString();
        } else if (resultObject.has("place_id")) {
            placeId = "nominatim_" + resultObject.get("place_id").getAsString();
        }


        String name = displayName; // Tên mặc định
        String address = displayName; // Địa chỉ mặc định

        // Cố gắng trích xuất tên và địa chỉ chi tiết hơn từ trường 'address'
        if (resultObject.has("address") && resultObject.get("address").isJsonObject()) {
            JsonObject addressObject = resultObject.getAsJsonObject("address");
            // Logic trích xuất tên tương tự như trong parseNominatimResponse
            // Ưu tiên các thành phần cụ thể như tên đường, POI, v.v.
            if (addressObject.has("road")) { // Tên đường thường là thông tin tốt nhất cho reverse geocoding
                name = addressObject.get("road").getAsString();
                if (addressObject.has("house_number")) {
                    name = addressObject.get("house_number").getAsString() + " " + name;
                }
            } else if (addressObject.has("amenity")) name = addressObject.get("amenity").getAsString();
            else if (addressObject.has("shop")) name = addressObject.get("shop").getAsString();
            else if (addressObject.has("tourism")) name = addressObject.get("tourism").getAsString();
            // ... (thêm các trường khác nếu cần, tương tự parseNominatimResponse) ...
            else if (addressObject.has("city")) name = addressObject.get("city").getAsString(); // Fallback cuối cùng có thể là tên thành phố
            else if (addressObject.has("county")) name = addressObject.get("county").getAsString();
            else if (addressObject.has("state")) name = addressObject.get("state").getAsString();
            else if (addressObject.has("country")) name = addressObject.get("country").getAsString();
            else { // Nếu không có tên cụ thể, dùng phần đầu của display_name
                if (displayName.contains(",")) {
                    name = displayName.substring(0, displayName.indexOf(",")).trim();
                } else {
                    name = displayName;
                }
            }

            // Xây dựng lại địa chỉ chi tiết từ các thành phần có sẵn
            StringBuilder detailedAddressBuilder = new StringBuilder();
            appendAddressComponent(detailedAddressBuilder, addressObject, "road");
            appendAddressComponent(detailedAddressBuilder, addressObject, "house_number");
            appendAddressComponent(detailedAddressBuilder, addressObject, "suburb");
            appendAddressComponent(detailedAddressBuilder, addressObject, "city_district");
            appendAddressComponent(detailedAddressBuilder, addressObject, "city");
            appendAddressComponent(detailedAddressBuilder, addressObject, "county");
            appendAddressComponent(detailedAddressBuilder, addressObject, "state");
            appendAddressComponent(detailedAddressBuilder, addressObject, "postcode");
            appendAddressComponent(detailedAddressBuilder, addressObject, "country");

            String tempAddress = detailedAddressBuilder.toString().trim();
            if (tempAddress.endsWith(",")) {
                tempAddress = tempAddress.substring(0, tempAddress.length() - 1).trim();
            }
            if (!tempAddress.isEmpty()) {
                address = tempAddress;
            }
        }

        return new Place(placeId, name, lat, lon, address, boundingBox, geoJsonString); // Thêm boundingBox vào constructor
    }

    /**
     * Trả về đối tượng {@link Route} cuối cùng đã được tính toán bởi dịch vụ này.
     * @return Đối tượng {@code Route} cuối cùng, hoặc {@code null} nếu chưa có tuyến đường nào được tính toán thành công.
     */
    public Route getLastRoute() {
        return lastCalculatedRoute;
    }
}
