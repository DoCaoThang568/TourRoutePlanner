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
 * Lớp dịch vụ để xử lý tính toán tuyến đường và tìm kiếm vị trí bằng các API bên ngoài (OSRM, Nominatim).
 */
public class RouteService {
    private String osrmServerUrl;
    private String nominatimServerUrl;
    /** Lưu trữ truy vấn đã được chuẩn hóa cuối cùng để xử lý hậu kỳ kết quả tìm kiếm. */
    public static String lastNormalizedQuery = "";

    /**
     * Khởi tạo một RouteService và tải cấu hình cho các URL máy chủ OSRM và Nominatim.
     */
    public RouteService() {
        loadConfig();
    }

    private void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Lỗi: Không tìm thấy tệp config.properties");
                osrmServerUrl = "http://router.project-osrm.org";
                nominatimServerUrl = "http://localhost:8080";
                return;
            }
            prop.load(input);
            osrmServerUrl = prop.getProperty("osrm.server.url", "http://router.project-osrm.org");
            nominatimServerUrl = prop.getProperty("nominatim.server.url", "http://localhost:8080");
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Lỗi khi đọc config.properties, sử dụng URL mặc định.");
            osrmServerUrl = "http://router.project-osrm.org";
            nominatimServerUrl = "http://localhost:8080";
        }
    }

    /**
     * Lấy một tuyến đường từ máy chủ OSRM cho một danh sách các địa điểm đã cho.
     * @param places Một danh sách các đối tượng Place đại diện cho các điểm tham chiếu. Phải chứa ít nhất 2 địa điểm.
     * @return Một đối tượng Route chứa chi tiết tuyến đường, hoặc null nếu không thể tìm thấy tuyến đường hoặc có lỗi xảy ra.
     * @throws IOException nếu có sự cố với giao tiếp mạng hoặc phản hồi API.
     * @throws IllegalArgumentException nếu danh sách địa điểm có ít hơn 2 mục.
     * @throws IllegalStateException nếu URL máy chủ OSRM chưa được cấu hình.
     */
    public Route getRouteFromOSRM(List<Place> places) throws IOException {
        if (places == null || places.size() < 2) {
            throw new IllegalArgumentException("Cần ít nhất 2 điểm để tìm lộ trình với OSRM.");
        }
        if (osrmServerUrl == null || osrmServerUrl.trim().isEmpty()) {
            throw new IllegalStateException("URL của OSRM server chưa được cấu hình.");
        }

        // Định dạng tọa độ cho OSRM: lng,lat;lng,lat;...
        String coordinatesString = places.stream()
                .map(p -> String.format(Locale.US, "%.5f,%.5f", p.getLng(), p.getLat()))
                .collect(Collectors.joining(";"));

        // Xây dựng URL API OSRM
        // Ví dụ: http://router.project-osrm.org/route/v1/driving/105.80,21.02;105.81,21.03?overview=full&geometries=geojson
        String apiUrl = String.format("%s/route/v1/driving/%s?overview=full&geometries=geojson&alternatives=false",
                                    osrmServerUrl, coordinatesString);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection(); // Updated URL creation
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return parseOSRMResponse(response.toString(), places);
        } else {
            System.err.println("Lỗi OSRM API: " + responseCode + " - " + connection.getResponseMessage());
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                System.err.println("Error details: " + errorResponse.toString());
                throw new IOException("Lỗi khi gọi API tìm đường OSRM: " + responseCode + ". Chi tiết: " + errorResponse.toString());
            } catch (IOException e) { // Catching exception from reading error stream
                 throw new IOException("Lỗi khi gọi API tìm đường OSRM: " + responseCode + ". Không thể đọc chi tiết lỗi.");
            }
        }
    }

    // Phân tích phản hồi JSON từ OSRM
    private Route parseOSRMResponse(String jsonResponse, List<Place> originalWaypoints) {
        JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        String code = responseObject.get("code").getAsString();

        if (!"Ok".equalsIgnoreCase(code)) {
            System.err.println("OSRM API Code: " + code);
            if (responseObject.has("message")) {
                 System.err.println("Error Message: " + responseObject.get("message").getAsString());
            }
            return null; // Or throw exception
        }

        JsonArray routesArray = responseObject.getAsJsonArray("routes");
        if (routesArray.size() == 0) {
            System.err.println("Không tìm thấy lộ trình nào từ OSRM.");
            return null;
        }

        JsonObject routeObject = routesArray.get(0).getAsJsonObject(); // Get the first route

        double totalDistanceMeters = routeObject.get("distance").getAsDouble();
        double totalDurationSeconds = routeObject.get("duration").getAsDouble();

        List<Route.Coordinate> polyline = new ArrayList<>();
        if (routeObject.has("geometry") && routeObject.getAsJsonObject("geometry").has("coordinates")) {
            JsonArray coordinatesArray = routeObject.getAsJsonObject("geometry").getAsJsonArray("coordinates");
            for (int i = 0; i < coordinatesArray.size(); i++) {
                JsonArray coordPair = coordinatesArray.get(i).getAsJsonArray();
                // OSRM GeoJSON returns [longitude, latitude]
                double lng = coordPair.get(0).getAsDouble();
                double lat = coordPair.get(1).getAsDouble();
                polyline.add(new Route.Coordinate(lat, lng)); // Store as lat, lng
            }
        }

        return new Route(originalWaypoints, polyline, totalDistanceMeters / 1000.0, totalDurationSeconds / 60.0);
    }

    /**
     * Tìm kiếm các vị trí theo tên bằng API Nominatim.
     * @param query Chuỗi truy vấn tìm kiếm.
     * @return Một Danh sách các đối tượng Place khớp với truy vấn, hoặc một danh sách trống nếu không tìm thấy kết quả nào hoặc có lỗi xảy ra.
     * @throws IOException nếu có sự cố với giao tiếp mạng hoặc phản hồi API.
     * @throws IllegalArgumentException nếu truy vấn là null hoặc trống.
     */
    public List<Place> searchLocationByName(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Suchanfrage darf nicht leer sein.");
        }
        // Chuẩn hóa và lưu query cho hậu xử lý
        String normalizedQuery = normalizeString(query);
        RouteService.lastNormalizedQuery = normalizedQuery;

        // URL encode the query
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());

        // Sử dụng /search.php cho Nominatim self-hosted (Docker), tăng limit lên 10
        String apiUrl = String.format(Locale.US,
                "%s/search.php?q=%s&format=json&limit=10&addressdetails=1",
                nominatimServerUrl,
                encodedQuery);

        HttpURLConnection connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "TourRoutePlannerApp/1.0 (your-email@example.com)");

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
            System.err.println("Lỗi Nominatim API: " + responseCode + " - " + connection.getResponseMessage());
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                System.err.println("Error details: " + errorResponse.toString());
                throw new IOException("Lỗi khi gọi API tìm kiếm địa điểm Nominatim: " + responseCode + ". Chi tiết: " + errorResponse.toString());
            } catch (IOException e) {
                throw new IOException("Lỗi khi gọi API tìm kiếm địa điểm Nominatim: " + responseCode + ". Không thể đọc chi tiết lỗi.");
            }
        }
    }

    // Phân tích phản hồi JSON từ Nominatim
    private List<Place> parseNominatimResponse(String jsonResponse) {
        List<Place> foundPlaces = new ArrayList<>();
        JsonArray resultsArray = JsonParser.parseString(jsonResponse).getAsJsonArray();

        if (resultsArray.size() == 0) {
            System.out.println("Không tìm thấy địa điểm nào từ Nominatim.");
            return foundPlaces; // Return empty list
        }

        for (int i = 0; i < resultsArray.size(); i++) {
            JsonObject placeObject = resultsArray.get(i).getAsJsonObject();
            String displayName = placeObject.get("display_name").getAsString();
            double lat = placeObject.get("lat").getAsDouble();
            double lon = placeObject.get("lon").getAsDouble();
            // Try to get a more specific name if available (e.g., from addressdetails)
            String name = displayName; // Default to full display name
            if (placeObject.has("address")) {
                JsonObject addressObject = placeObject.getAsJsonObject("address");
                if (addressObject.has("road")) {
                    name = addressObject.get("road").getAsString();
                    if (addressObject.has("house_number")) {
                        name += " " + addressObject.get("house_number").getAsString();
                    }
                    if (addressObject.has("city")) {
                        name += ", " + addressObject.get("city").getAsString();
                    } else if (addressObject.has("town")) {
                        name += ", " + addressObject.get("town").getAsString();
                    } else if (addressObject.has("village")) {
                        name += ", " + addressObject.get("village").getAsString();
                    }
                } else if (addressObject.has("amenity")) {
                    name = addressObject.get("amenity").getAsString();
                } else if (addressObject.has("shop")) {
                    name = addressObject.get("shop").getAsString();
                }
            }
            foundPlaces.add(new Place(name, lat, lon));
        }
        // --- Hậu xử lý: Ưu tiên kết quả phù hợp ---
        String lastQuery = RouteService.lastNormalizedQuery;
        if (lastQuery != null && !lastQuery.isEmpty()) {
            foundPlaces = foundPlaces.stream()
                .sorted((p1, p2) -> {
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
                })
                .toList();
        }
        return foundPlaces;
    }

    /**
     * Chuẩn hóa một chuỗi bằng cách loại bỏ dấu phụ, chuyển đổi thành chữ thường,
     * loại bỏ các ký tự đặc biệt (ngoại trừ dấu cách) và cắt bỏ khoảng trắng.
     * @param input Chuỗi cần chuẩn hóa.
     * @return Chuỗi đã được chuẩn hóa.
     */
    public static String normalizeString(String input) {
        if (input == null) return "";
        String temp = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        temp = temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return temp.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
    }

}
