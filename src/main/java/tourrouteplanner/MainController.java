package tourrouteplanner;

import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.engine.RenderingMode;
import com.teamdev.jxbrowser.frame.Frame;
import com.teamdev.jxbrowser.js.JsAccessible;
import com.teamdev.jxbrowser.js.JsObject;
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived; // Correct import for JxBrowser 8.x
import com.teamdev.jxbrowser.navigation.event.LoadFinished;
import com.teamdev.jxbrowser.view.javafx.BrowserView;
import com.teamdev.jxbrowser.browser.callback.InjectJsCallback;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;
import tourrouteplanner.service.RouteService;
import tourrouteplanner.service.StorageService;
import tourrouteplanner.util.Utils;

import java.io.File;
import java.io.IOException; // Thêm import IOException
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList; // Thêm import ArrayList
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

import org.controlsfx.control.textfield.TextFields;

/**
 * Controller chính cho giao diện người dùng của ứng dụng Tour Route Planner.
 * Quản lý các tương tác của người dùng, hiển thị bản đồ, tìm kiếm địa điểm,
 * lập kế hoạch lộ trình và lưu/tải lộ trình.
 */
public class MainController {

    @FXML
    private TextField searchBox; // Trường nhập liệu để tìm kiếm địa điểm.
    @FXML
    private ListView<Place> placeListView; // Danh sách hiển thị kết quả tìm kiếm địa điểm.
    @FXML
    private TableView<Place> routeTableView; // Bảng hiển thị các địa điểm đã chọn trong lộ trình hiện tại.
    @FXML
    private TableColumn<Place, String> routePlaceNameColumn; // Cột tên địa điểm trong bảng lộ trình.
    @FXML
    private TableColumn<Place, String> routePlaceAddressColumn; // Cột địa chỉ trong bảng lộ trình.
    @FXML
    private Button addSelectedButton; // Nút để thêm địa điểm được chọn từ danh sách kết quả vào lộ trình.
    @FXML
    private Button removeSelectedButton; // Nút để xóa địa điểm được chọn từ bảng lộ trình.
    @FXML
    private Button findRouteButton; // Nút để tìm và hiển thị lộ trình giữa các địa điểm đã chọn.
    @FXML
    private Button saveRouteButton; // Nút để lưu lộ trình hiện tại ra tệp.
    @FXML
    private Button loadRouteButton; // Nút để tải lộ trình từ tệp.
    @FXML
    private Label totalDistanceLabel; // Nhãn hiển thị tổng quãng đường của lộ trình.
    @FXML
    private StackPane mapPane; // Container cho bản đồ JxBrowser.

    // Danh sách các địa điểm kết quả tìm kiếm, có thể được quan sát để cập nhật UI.
    private ObservableList<Place> searchResults = FXCollections.observableArrayList();
    // Danh sách các địa điểm trong lộ trình hiện tại, có thể được quan sát để cập nhật UI.
    private ObservableList<Place> currentRoutePlaces = FXCollections.observableArrayList();
    // Dịch vụ xử lý logic tìm kiếm địa điểm và lộ trình.
    private RouteService routeService;
    // Dịch vụ xử lý logic lưu và tải lộ trình.
    private StorageService storageService;

    // Các thành phần của JxBrowser để hiển thị bản đồ.
    private Engine engine;
    private Browser browser;
    private BrowserView browserView;

    @FXML
    private Label statusLabel; // Nhãn hiển thị thông báo trạng thái cho người dùng.

    /**
     * Constructor mặc định cho MainController.
     * Được gọi khi FXML được tải.
     */
    public MainController() {
        // Khởi tạo ban đầu có thể được thực hiện ở đây nếu cần,
        // nhưng hầu hết logic khởi tạo UI nên đặt trong phương thức initialize().
    }

    /**
     * Khởi tạo controller sau khi các trường FXML đã được inject.
     * Thiết lập các dịch vụ, danh sách, bảng và JxBrowser.
     */
    @FXML
    public void initialize() {
        // Khởi tạo các dịch vụ. RouteService không còn nhận API key qua constructor.
        routeService = new RouteService();
        storageService = new StorageService();

        // Thiết lập ListView cho kết quả tìm kiếm địa điểm.
        placeListView.setItems(searchResults);
        placeListView.setCellFactory(param -> new ListCell<Place>() {
            private Tooltip tooltip = new Tooltip(); // Tooltip for each cell

            @Override
            protected void updateItem(Place item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    // Hiển thị tên và địa chỉ đầy đủ hơn
                    String displayText = item.getName();
                    String address = item.getAddress();

                    if (address != null && !address.isEmpty() && !address.equalsIgnoreCase(item.getName())) {
                        // Nếu tên địa điểm không chứa phần đầu của địa chỉ (tránh lặp lại)
                        // Ví dụ: Tên: "Hồ Gươm", Địa chỉ: "Hồ Gươm, Quận Hoàn Kiếm, Hà Nội"
                        // thì không cần thêm "Hồ Gươm" vào nữa.
                        String lowerCaseName = item.getName().toLowerCase();
                        String lowerCaseAddress = address.toLowerCase();
                        
                        // Kiểm tra xem tên có phải là một phần của địa chỉ không
                        boolean nameIsPartOfAddress = lowerCaseAddress.contains(lowerCaseName);

                        if (nameIsPartOfAddress) {
                            // Nếu tên là một phần của địa chỉ, chỉ hiển thị địa chỉ
                            // (vì địa chỉ thường đầy đủ hơn tên)
                            // Tuy nhiên, nếu tên và địa chỉ gần giống nhau, ưu tiên tên cho ngắn gọn
                            if (address.length() > item.getName().length() + 5) { // +5 để cho phép một chút khác biệt nhỏ
                                displayText = address; // Hiển thị địa chỉ đầy đủ nếu nó dài hơn tên đáng kể
                            } else {
                                // Nếu không, giữ nguyên tên và có thể thêm một phần nhỏ của địa chỉ nếu khác biệt
                                String remainingAddress = address.replace(item.getName(), "").trim();
                                if (remainingAddress.startsWith(",")) {
                                    remainingAddress = remainingAddress.substring(1).trim();
                                }
                                if (!remainingAddress.isEmpty()) {
                                    displayText += ", " + remainingAddress;
                                }
                            }
                        } else {
                            // Nếu tên không phải là một phần của địa chỉ, ghép cả hai
                            displayText = item.getName() + " - " + address;
                        }
                    } 
                    // Giới hạn độ dài hiển thị tổng thể nếu cần, nhưng ưu tiên hiển thị nhiều thông tin hơn
                    int maxLength = 100; // Tăng giới hạn độ dài
                    if (displayText.length() > maxLength) {
                        displayText = displayText.substring(0, maxLength - 3) + "...";
                    }

                    setText(displayText);
                    tooltip.setText(item.getName() + "\n" + item.getAddress()); // Tooltip vẫn hiển thị đầy đủ
                    setTooltip(tooltip);
                }
            }
        });

        // Thêm listener để pan bản đồ khi một địa điểm được chọn trong placeListView.
        placeListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                double[] boundingBox = newSelection.getBoundingBox();
                if (boundingBox != null && boundingBox.length == 4) {
                    // Ưu tiên zoom tới bounding box nếu có
                    // Bounding box từ Nominatim là [southLat, northLat, westLon, eastLon]
                    // Hàm JS zoomToBoundingBox cũng nhận theo thứ tự này
                    zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                    highlightBoundingBoxOnMap(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                } else {
                    // Nếu không có bounding box, pan tới điểm trung tâm với mức zoom mặc định
                    int defaultZoomLevelForPlace = 15;
                    // Gọi panTo với Integer cho zoomLevel
                    panTo(newSelection.getLatitude(), newSelection.getLongitude(), Integer.valueOf(defaultZoomLevelForPlace));
                    clearMapHighlight(); // Xóa highlight cũ nếu địa điểm mới không có bounding box
                }
            } else {
                clearMapHighlight(); // Xóa highlight nếu không có địa điểm nào được chọn
            }
        });

        // Cấu hình cho TableView các địa điểm đã chọn trong lộ trình.
        routePlaceNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        routePlaceAddressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        routeTableView.setItems(currentRoutePlaces);

        // Khởi tạo JxBrowser để hiển thị bản đồ.
        initializeJxBrowser();
    }

    /**
     * Khởi tạo và cấu hình JxBrowser Engine, Browser và BrowserView.
     * Tải tệp HTML bản đồ và thiết lập giao tiếp Java-JavaScript.
     */
    private void initializeJxBrowser() {
        // Tải JxBrowser license key từ tệp config.properties.
        String licenseKey = Utils.loadConfigProperty("jxbrowser.license.key");
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            Utils.showAlert(Alert.AlertType.ERROR, "Lỗi License Key", "Không tìm thấy JxBrowser License Key trong config.properties.");
            System.err.println("JxBrowser License Key is missing in config.properties. JxBrowser will not be initialized.");
            // Cân nhắc vô hiệu hóa các tính năng liên quan đến bản đồ nếu không có license key.
            return;
        }

        try {
            // Cấu hình và khởi tạo JxBrowser Engine.
            EngineOptions options = EngineOptions.newBuilder(RenderingMode.HARDWARE_ACCELERATED)
                    .licenseKey(licenseKey) // Sử dụng license key đã tải.
                    .build();
            engine = Engine.newInstance(options);
            browser = engine.newBrowser();

            // Cho phép JavaScript gọi các phương thức Java được đánh dấu @JsAccessible.
            // Đặt đối tượng 'javaConnector' (là instance của MainController này) vào global window object của JavaScript.
            browser.set(InjectJsCallback.class, params -> {
                Frame frame = params.frame();
                JsObject window = frame.executeJavaScript("window");
                if (window != null) {
                    window.putProperty("javaConnector", MainController.this);
                    System.out.println("Java object 'javaConnector' exposed to JavaScript.");
                } else {
                    System.err.println("Could not get window object from frame.");
                }
                return InjectJsCallback.Response.proceed();
            });

            // Lắng nghe và ghi lại các thông điệp từ console của JavaScript.
            browser.on(ConsoleMessageReceived.class, event -> {
                String message = "[JS " + event.consoleMessage().level() + "] " + event.consoleMessage().message();
                System.out.println(message);
            });

            // Xử lý sự kiện khi trang web (map.html) đã tải xong.
            browser.navigation().on(LoadFinished.class, event -> {
                String loadedUrl = "unknown";
                try {
                    if (event.navigation() != null && event.navigation().browser() != null) {
                        loadedUrl = event.navigation().browser().url();
                    }
                } catch (Exception e) {
                    System.err.println("Error retrieving URL in LoadFinished: " + e.getMessage());
                }
                System.out.println("JxBrowser LoadFinished event. URL: \"" + loadedUrl + "\""); // Diagnostic log

                if (loadedUrl.endsWith("map.html")) {
                    System.out.println("map.html loaded. Proceeding with API key injection."); // Diagnostic log
                    String maptilerApiKey = Utils.loadConfigProperty("maptiler.api.key");
                    if (maptilerApiKey != null && !maptilerApiKey.trim().isEmpty()) {
                        browser.mainFrame().ifPresent(frame -> {
                            // Tạo script để gán API key và gọi hàm khởi tạo bản đồ trong JavaScript.
                            String script = String.format("window.MAPTILER_API_KEY = \'%s\'; if(typeof initializeMapWithApiKey === \'function\') { console.log(\'Calling initializeMapWithApiKey from Java\'); initializeMapWithApiKey(); } else { console.error(\'initializeMapWithApiKey function not found in map.html\'); }", Utils.escapeJavaScriptString(maptilerApiKey));
                            frame.executeJavaScript(script);
                            System.out.println("MapTiler API Key injected and initializeMapWithApiKey called.");
                        });
                    } else {
                        Utils.showAlert(Alert.AlertType.ERROR, "Lỗi API Key", "Không thể tải MapTiler API Key từ config.properties.");
                        System.err.println("MapTiler API Key is missing or empty in config.properties.");
                        // Nếu không có API key, thử khởi tạo bản đồ ở chế độ fallback (ví dụ: OSM mặc định).
                        browser.mainFrame().ifPresent(frame -> {
                            frame.executeJavaScript("if(typeof initializeMapWithApiKey === \'function\') { initializeMapWithApiKey(true); } else { console.error(\'initializeMapWithApiKey function not found for fallback.\'); }"); // Gọi với cờ fallback.
                        });
                    }
                } else {
                    // Added more verbose logging here
                    System.err.println("LoadFinished: Loaded URL does NOT end with map.html. URL: \"" + loadedUrl + "\". Map specific initialization will be skipped.");
                }
            });

            // Tạo BrowserView và thêm vào mapPane.
            browserView = BrowserView.newInstance(browser);
            mapPane.getChildren().add(browserView);

            // Tải tệp map.html.
            Path mapHtmlPath = Paths.get("src/main/resources/tourrouteplanner/map.html").toAbsolutePath();
            if (Files.exists(mapHtmlPath)) {
                browser.navigation().loadUrl(mapHtmlPath.toUri().toString());
            } else {
                String errorMessage = "map.html not found at: " + mapHtmlPath.toString();
                System.err.println(errorMessage);
                Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tải bản đồ", errorMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Lỗi khởi tạo JxBrowser", "Không thể khởi tạo JxBrowser: " + e.getMessage());
        }
    }

    /**
     * Xử lý sự kiện tìm kiếm địa điểm khi người dùng nhập vào searchBox.
     * Gọi RouteService để tìm kiếm và cập nhật placeListView.
     * Di chuyển bản đồ đến vị trí của kết quả đầu tiên (nếu có).
     */
    @FXML
    private void handleSearch() {
        String query = searchBox.getText();
        clearMapHighlight(); // Xóa highlight cũ khi bắt đầu tìm kiếm mới
        if (query == null || query.trim().isEmpty()) {
            searchResults.clear(); // Xóa kết quả cũ nếu query rỗng.
            return;
        }
        try {
            List<Place> places = routeService.searchPlaces(query);
            searchResults.setAll(places); // Cập nhật danh sách kết quả.
            if (!places.isEmpty()) {
                placeListView.getSelectionModel().selectFirst(); // Chọn kết quả đầu tiên.
                // Place firstPlace = places.get(0);
                // Việc pan bản đồ sẽ được xử lý bởi listener của placeListView
                // panTo(firstPlace.getLatitude(), firstPlace.getLongitude());
            }

            // Diagnostic logging for map visibility
            Platform.runLater(() -> {
                System.out.println("--- After handleSearch ---");
                if (mapPane != null) {
                    System.out.println("MapPane visible: " + mapPane.isVisible() + ", managed: " + mapPane.isManaged() +
                                       ", width: " + mapPane.getWidth() + ", height: " + mapPane.getHeight());
                    if (mapPane.getChildren().isEmpty()) {
                        System.err.println("MapPane has NO children.");
                    } else {
                         System.out.println("MapPane children count: " + mapPane.getChildren().size());
                    }
                } else {
                     System.err.println("MapPane is NULL.");
                }

                if (browserView != null) {
                    System.out.println("BrowserView visible: " + browserView.isVisible() + ", managed: " + browserView.isManaged() +
                                       ", width: " + browserView.getBoundsInLocal().getWidth() + 
                                       ", height: " + browserView.getBoundsInLocal().getHeight());
                    if (mapPane != null && !mapPane.getChildren().contains(browserView)) {
                        System.err.println("CRITICAL: BrowserView is NOT a child of mapPane!");
                    }
                     // Check if browser is still valid
                    if (browser != null && browser.isClosed()) {
                        System.err.println("CRITICAL: Browser instance is CLOSED after search!");
                    } else if (browser == null) {
                        System.err.println("CRITICAL: Browser instance is NULL after search!");
                    }

                } else {
                    System.err.println("CRITICAL: browserView is NULL after search.");
                }
                System.out.println("--- End of handleSearch diagnostics ---");
            });

        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tìm kiếm", "Không thể thực hiện tìm kiếm: " + e.getMessage());
        }
    }

    /**
     * Xử lý sự kiện thêm địa điểm được chọn từ placeListView vào routeTableView.
     * Thêm marker cho địa điểm mới trên bản đồ.
     */
    @FXML
    private void handleAddSelected() {
        Place selectedPlace = placeListView.getSelectionModel().getSelectedItem();
        if (selectedPlace != null && !currentRoutePlaces.contains(selectedPlace)) {
            currentRoutePlaces.add(selectedPlace); // Thêm vào danh sách lộ trình.
            // Thêm marker trên bản đồ cho địa điểm vừa thêm.
            addMapMarker(selectedPlace.getName(), selectedPlace.getLatitude(), selectedPlace.getLongitude(), selectedPlace.getAddress());
        }
    }

    /**
     * Xử lý sự kiện xóa địa điểm được chọn từ routeTableView.
     * Xóa marker tương ứng và cập nhật lại lộ trình trên bản đồ.
     */
    @FXML
    private void handleRemoveSelected() {
        // Lấy địa điểm được chọn từ bảng các điểm trong lộ trình.
        Place selectedRoutePlace = routeTableView.getSelectionModel().getSelectedItem();
        if (selectedRoutePlace != null) {
            // Kiểm tra xem địa điểm bị xóa có phải là địa điểm đang được highlight không
            Place currentlySelectedInList = placeListView.getSelectionModel().getSelectedItem();
            boolean wasHighlightTarget = selectedRoutePlace.equals(currentlySelectedInList);

            currentRoutePlaces.remove(selectedRoutePlace); // Xóa khỏi danh sách lộ trình.
            clearAllMarkers();
            currentRoutePlaces.forEach(p -> addMapMarker(p.getName(), p.getLatitude(), p.getLongitude(), p.getAddress()));

            if (wasHighlightTarget) {
                clearMapHighlight(); // Xóa highlight nếu địa điểm bị xóa đang được chọn trong placeListView
            }

            if (currentRoutePlaces.size() > 1) {
                handleFindRoute(); // Tính toán lại lộ trình nếu còn ít nhất 2 điểm.
            } else {
                clearRoute(); // Xóa lộ trình trên bản đồ nếu không còn đủ điểm.
                // Cập nhật Label tổng quãng đường.
                if (totalDistanceLabel != null) {
                    totalDistanceLabel.setText("Tổng quãng đường: 0 km");
                }
            }
        } else {
            // Thông báo nếu không có điểm nào được chọn trong TableView.
            Utils.showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Vui lòng chọn một địa điểm từ bảng 'Các điểm đã chọn trong lộ trình' để xóa.");
        }
    }

    /**
     * Xử lý sự kiện tìm đường đi giữa các địa điểm trong currentRoutePlaces.
     * Gọi RouteService để lấy thông tin lộ trình và vẽ lên bản đồ.
     * Cập nhật tổng quãng đường.
     */
    @FXML
    private void handleFindRoute() {
        if (currentRoutePlaces.size() < 2) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Cần ít nhất 2 địa điểm để tìm đường đi.");
            clearRoute(); // Xóa lộ trình cũ (nếu có).
            totalDistanceLabel.setText("Tổng quãng đường: 0 km");
            return;
        }
        try {
            // RouteService.getRoute() nhận List<Place> làm tham số.
            // Chuyển ObservableList<Place> thành ArrayList<Place> để truyền vào service.
            Route route = routeService.getRoute(new ArrayList<>(currentRoutePlaces));
            if (route != null && route.getCoordinates() != null && !route.getCoordinates().isEmpty()) {
                drawRoute(route.getCoordinates()); // Vẽ lộ trình lên bản đồ.
                // Cập nhật nhãn tổng quãng đường.
                totalDistanceLabel.setText(String.format(Locale.US, "Tổng quãng đường: %.2f km", route.getTotalDistanceKm()));
            } else {
                Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tìm đường", "Không thể tìm thấy đường đi cho các địa điểm đã chọn.");
                clearRoute();
                totalDistanceLabel.setText("Tổng quãng đường: 0 km");
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tìm đường", "Lỗi khi kết nối dịch vụ tìm đường: " + e.getMessage());
            clearRoute();
            totalDistanceLabel.setText("Tổng quãng đường: 0 km");
        }
    }

    /**
     * Xử lý sự kiện lưu lộ trình hiện tại (danh sách địa điểm và thông tin lộ trình) ra tệp.
     * Sử dụng StorageService để hiển thị dialog lưu tệp và thực hiện lưu.
     */
    @FXML
    private void handleSaveRoute() {
        if (currentRoutePlaces.isEmpty()) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Không có tuyến đường nào để lưu.");
            return;
        }
        File file = storageService.showSaveFileDialog(mapPane.getScene().getWindow());
        if (file != null) {
            // Truyền danh sách địa điểm và thông tin lộ trình cuối cùng (nếu có) để lưu.
            storageService.saveRoute(file, new ArrayList<>(currentRoutePlaces), routeService.getLastRoute());
        }
    }

    /**
     * Xử lý sự kiện tải lộ trình từ một tệp đã lưu.
     * Sử dụng StorageService để hiển thị dialog mở tệp và tải dữ liệu.
     * Cập nhật UI (danh sách địa điểm, bản đồ, tổng quãng đường) với dữ liệu đã tải.
     */
    @FXML
    private void handleLoadRoute() {
        File file = storageService.showOpenFileDialog(mapPane.getScene().getWindow());
        if (file != null) {
            clearMapHighlight(); // Xóa highlight cũ trước khi tải lộ trình mới
            StorageService.LoadedRouteData loadedData = storageService.loadRoute(file);
            // Kiểm tra dữ liệu tải về và danh sách địa điểm không null.
            if (loadedData != null && loadedData.getPlaces() != null) {
                currentRoutePlaces.setAll(loadedData.getPlaces()); // Cập nhật danh sách địa điểm.
                clearAllMarkers(); // Xóa các marker cũ.
                // Thêm marker cho các địa điểm vừa tải.
                currentRoutePlaces.forEach(p -> {
                    if (p != null) { // Kiểm tra p không null trước khi truy cập.
                        addMapMarker(p.getName(), p.getLatitude(), p.getLongitude(), p.getAddress());
                    }
                });
                // Nếu có thông tin lộ trình đã lưu, vẽ lại lộ trình và cập nhật quãng đường.
                if (loadedData.getRoute() != null && loadedData.getRoute().getCoordinates() != null && !loadedData.getRoute().getCoordinates().isEmpty()) {
                    drawRoute(loadedData.getRoute().getCoordinates());
                    totalDistanceLabel.setText(String.format(Locale.US, "Tổng quãng đường: %.2f km", loadedData.getRoute().getTotalDistanceKm()));
                    // Di chuyển bản đồ đến địa điểm đầu tiên trong lộ trình.
                    if (!currentRoutePlaces.isEmpty() && currentRoutePlaces.get(0) != null) {
                        Place firstPlace = currentRoutePlaces.get(0);
                        int defaultZoomLevelForRoute = 12; // Mức zoom phù hợp hơn khi hiển thị lộ trình
                        // Gọi panTo với Integer cho zoomLevel
                        panTo(firstPlace.getLatitude(), firstPlace.getLongitude(), Integer.valueOf(defaultZoomLevelForRoute));
                    }
                } else if (currentRoutePlaces.size() > 1) {
                    // Nếu không có thông tin lộ trình được lưu nhưng có đủ điểm, thử tính lại lộ trình.
                    handleFindRoute();
                } else {
                    // Nếu không đủ điểm, xóa lộ trình và quãng đường.
                    clearRoute();
                    totalDistanceLabel.setText("Tổng quãng đường: 0 km");
                }
            } else if (loadedData == null) {
                 Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tải tuyến đường", "Không thể tải dữ liệu từ tệp đã chọn.");
            }
        }
    }

    /**
     * Thực thi một đoạn mã JavaScript trong khung chính của trình duyệt JxBrowser.
     * @param script Đoạn mã JavaScript cần thực thi.
     */
    private void executeJavaScript(String script) {
        // Đảm bảo browser và mainFrame khả dụng trước khi thực thi.
        if (browser != null && browser.mainFrame().isPresent()) {
            browser.mainFrame().get().executeJavaScript(script);
        } else {
             System.err.println("Không thể thực thi JavaScript, browser hoặc khung chính chưa sẵn sàng. Script: " + script);
        }
    }

    /**
     * Gọi hàm JavaScript panTo trên bản đồ để di chuyển đến tọa độ và mức zoom cụ thể.
     * @param latitude Vĩ độ.
     * @param longitude Kinh độ.
     * @param zoomLevel Mức zoom (tùy chọn, nếu null thì giữ nguyên zoom hiện tại).
     */
    private void panTo(double latitude, double longitude, Integer zoomLevel) {
        // Không cần kiểm tra browser và mainFrame ở đây nữa vì executeJavaScript đã làm.
        String script;
        if (zoomLevel != null) {
            script = String.format(Locale.US, "if(typeof panTo === 'function') { panTo(%.8f, %.8f, %d); } else { console.error('panTo function not found in map.html'); }", latitude, longitude, zoomLevel.intValue());
        } else {
            // JavaScript panTo function expects zoomLevel to be either a number or undefined.
            // Passing null from Java might be interpreted differently by JavaScript depending on JxBrowser marshalling.
            // It's safer to call a version of panTo in JS that doesn't expect a zoom level, or explicitly pass undefined.
            // For now, assuming the JS panTo handles `null` for zoomLevel gracefully or we ensure it does.
            // Let's call it without the zoomLevel argument if it's null, to match the JS function signature if it has an optional param.
            // However, the current JS panTo(lat, lng, zoomLevel) expects zoomLevel or it will be undefined.
            // So, if zoomLevel is null, we should call a JS variant or ensure current panTo handles it.
            // The JS `panTo` is defined as `function panTo(latitude, longitude, zoomLevel)`
            // If `zoomLevel` is `undefined` in JS, it works as intended (keeps current zoom).
            // If `zoomLevel` is a number, it logs: `Animating to center [...] and zoom ...`
            // So, if Java `zoomLevel` is null, we want JS `zoomLevel` to be `undefined`.
            // One way is to format the script differently.
            script = String.format(Locale.US, "if(typeof panTo === 'function') { panTo(%.8f, %.8f, undefined); } else { console.error('panTo function not found in map.html'); }", latitude, longitude);
        }
        executeJavaScript(script);
    }

    /**
     * Gọi hàm JavaScript zoomToBoundingBox trên bản đồ.
     * @param southLat Vĩ độ Nam của bounding box.
     * @param northLat Vĩ độ Bắc của bounding box.
     * @param westLon Kinh độ Tây của bounding box.
     * @param eastLon Kinh độ Đông của bounding box.
     */
    private void zoomToBoundingBox(double southLat, double northLat, double westLon, double eastLon) {
        if (browser != null && browser.mainFrame().isPresent()) {
            String script = String.format(Locale.US, 
                "if(typeof zoomToBoundingBox === 'function') { zoomToBoundingBox(%.8f, %.8f, %.8f, %.8f); } else { console.error('zoomToBoundingBox function not found in map.html'); }",
                southLat, northLat, westLon, eastLon);
            executeJavaScript(script);
        } else {
            System.err.println("Không thể gọi zoomToBoundingBox: browser hoặc mainFrame không khả dụng.");
        }
    }

    /**
     * Gọi hàm JavaScript để highlight một bounding box trên bản đồ.
     * @param southLat Vĩ độ Nam của bounding box.
     * @param northLat Vĩ độ Bắc của bounding box.
     * @param westLon Kinh độ Tây của bounding box.
     * @param eastLon Kinh độ Đông của bounding box.
     */
    private void highlightBoundingBoxOnMap(double southLat, double northLat, double westLon, double eastLon) {
        if (browser != null && browser.mainFrame().isPresent()) {
            String script = String.format(Locale.US,
                "if(typeof highlightBoundingBox === 'function') { highlightBoundingBox(%.8f, %.8f, %.8f, %.8f); } else { console.error('highlightBoundingBox function not found in map.html'); }",
                southLat, northLat, westLon, eastLon);
            executeJavaScript(script);
        } else {
            System.err.println("Không thể gọi highlightBoundingBoxOnMap: browser hoặc mainFrame không khả dụng.");
        }
    }

    /**
     * Gọi hàm JavaScript để xóa highlight trên bản đồ.
     */
    private void clearMapHighlight() {
        if (browser != null && browser.mainFrame().isPresent()) {
            executeJavaScript("if(typeof clearHighlight === 'function') { clearHighlight(); } else { console.error('clearHighlight function not found in map.html'); }");
        } else {
            System.err.println("Không thể gọi clearMapHighlight: browser hoặc mainFrame không khả dụng.");
        }
    }

    /**
     * Thêm một marker vào bản đồ tại vị trí (lat, lng) với tên và mô tả cho trước.
     * Gọi hàm JavaScript 'addMapMarker' trong map.html.
     * @param name Tên của địa điểm (hiển thị trong popup của marker).
     * @param lat Vĩ độ của địa điểm.
     * @param lng Kinh độ của địa điểm.
     * @param description Mô tả chi tiết của địa điểm.
     */
    public void addMapMarker(String name, double lat, double lng, String description) {
        String script = String.format(Locale.US, "addMapMarker('%s', %f, %f, '%s');",
                Utils.escapeJavaScriptString(name), lat, lng, Utils.escapeJavaScriptString(description));
        executeJavaScript(script);
    }

    /**
     * Vẽ một lộ trình trên bản đồ dựa trên danh sách các tọa độ.
     * Gọi hàm JavaScript 'drawRoute' trong map.html.
     * @param coordinates Danh sách các đối tượng Route.Coordinate đại diện cho lộ trình.
     */
    public void drawRoute(List<Route.Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            clearRoute(); // Xóa lộ trình cũ nếu không có tọa độ mới.
            return;
        }
        // Xây dựng một mảng JavaScript từ danh sách tọa độ.
        StringBuilder jsRouteArray = new StringBuilder("[");
        for (int i = 0; i < coordinates.size(); i++) {
            Route.Coordinate coord = coordinates.get(i);
            if (coord != null) { // Kiểm tra coord không null.
                jsRouteArray.append(String.format(Locale.US, "{lat: %f, lng: %f}", coord.getLatitude(), coord.getLongitude()));
                if (i < coordinates.size() - 1) {
                    jsRouteArray.append(",");
                }
            }
        }
        jsRouteArray.append("]");
        String script = String.format("drawRoute(%s);", jsRouteArray.toString());
        executeJavaScript(script);
    }

    /**
     * Xóa tất cả các marker khỏi bản đồ.
     * Gọi hàm JavaScript 'clearAllMarkers' trong map.html.
     */
    public void clearAllMarkers() {
        executeJavaScript("clearAllMarkers();");
    }

    /**
     * Xóa lộ trình hiện tại khỏi bản đồ.
     * Gọi hàm JavaScript 'clearRoute' trong map.html.
     */
    public void clearRoute() {
        if (browser != null && browser.mainFrame().isPresent()) {
            executeJavaScript("if(typeof clearRoute === 'function') { clearRoute(); } else { console.error('clearRoute function not found in map.html'); }");
        } else {
            System.err.println("Không thể gọi clearRoute: browser hoặc mainFrame không khả dụng.");
        }
    }

    /**
     * Di chuyển (pan) bản đồ đến tọa độ (lat, lng) cho trước với một mức zoom cụ thể.
     * Gọi hàm JavaScript 'panTo' trong map.html.
     * @param lat Vĩ độ của địa điểm.
     * @param lng Kinh độ của địa điểm.
     * @param zoomLevel Mức zoom mong muốn (ví dụ: 15 cho chi tiết, 10 cho ευρύτερη περιοχή).
     */
    private void panTo(double lat, double lng, int zoomLevel) {
        String script = String.format(Locale.US, "panTo(%f, %f, %d);", lat, lng, zoomLevel);
        executeJavaScript(script);
    }

    /**
     * Xử lý sự kiện khi người dùng nhấp chuột vào bản đồ.
     * Phương thức này được gọi từ JavaScript thông qua 'javaConnector'.
     * Thực hiện reverse geocoding để lấy thông tin địa điểm tại vị trí nhấp chuột
     * và hỏi người dùng có muốn thêm địa điểm đó vào lộ trình không.
     * @param lat Vĩ độ của điểm nhấp chuột.
     * @param lng Kinh độ của điểm nhấp chuột.
     */
    @JsAccessible
    public void handleMapClick(double lat, double lng) {
        Platform.runLater(() -> { // Đảm bảo các thay đổi UI được thực hiện trên JavaFX Application Thread.
            System.out.println("Map clicked at Lat: " + lat + ", Lng: " + lng);
            try {
                Place clickedPlace = routeService.reverseGeocode(lat, lng); // Lấy thông tin địa điểm.
                if (clickedPlace != null) {
                    // Hiển thị dialog xác nhận thêm địa điểm.
                    Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmDialog.setTitle("Thêm địa điểm");
                    confirmDialog.setHeaderText("Thêm địa điểm từ bản đồ?");
                    confirmDialog.setContentText("Bạn có muốn thêm \"" + clickedPlace.getName() + "\" vào tuyến đường không?");
                    Optional<ButtonType> result = confirmDialog.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        if (!currentRoutePlaces.contains(clickedPlace)) { // Kiểm tra địa điểm chưa có trong lộ trình.
                            currentRoutePlaces.add(clickedPlace);
                            addMapMarker(clickedPlace.getName(), clickedPlace.getLatitude(), clickedPlace.getLongitude(), clickedPlace.getAddress());
                        } else {
                            Utils.showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Địa điểm đã có trong tuyến đường.");
                        }
                    }
                } else {
                    Utils.showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Không thể tìm thấy thông tin cho vị trí đã nhấp.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                Utils.showAlert(Alert.AlertType.ERROR, "Lỗi Geocoding ngược", "Không thể lấy thông tin địa điểm: " + e.getMessage());
            }
        });
    }

    /**
     * Ghi một thông điệp từ JavaScript ra console của Java.
     * Phương thức này có thể được gọi từ JavaScript thông qua 'javaConnector.logJs("message")'.
     * @param message Thông điệp cần ghi.
     */
    @JsAccessible
    public void logJs(String message) {
        System.out.println("JS (direct call via javaConnector): " + message);
    }

    /**
     * Đóng JxBrowser engine khi ứng dụng kết thúc.
     * Quan trọng để giải phóng tài nguyên.
     */
    public void shutdownJxBrowser() {
        if (engine != null && !engine.isClosed()) {
            System.out.println("Shutting down JxBrowser engine...");
            engine.close();
            System.out.println("JxBrowser engine closed.");
        }
    }

    /**
     * Xử lý sự kiện thoát ứng dụng từ menu.
     * Đóng JavaFX platform và kết thúc tiến trình.
     */
    @FXML
    private void onExit() {
        Platform.exit(); // Yêu cầu JavaFX platform thoát.
        System.exit(0); // Đảm bảo tiến trình JVM kết thúc.
    }

    /**
     * Hiển thị dialog "Thông tin" (About) của ứng dụng.
     */
    @FXML
    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông tin");
        alert.setHeaderText("Tour Route Planner");
        alert.setContentText("Ứng dụng lập kế hoạch lộ trình du lịch.\\nPhiên bản 1.0\\nPhát triển bởi Nhóm Phát Triển."); // Thay thế bằng tên của bạn/nhóm.
        alert.showAndWait();
    }

    /**
     * Xử lý sự kiện xóa tất cả các địa điểm khỏi lộ trình hiện tại.
     * Hiển thị dialog xác nhận trước khi xóa.
     * Cập nhật UI (danh sách, bản đồ, tổng quãng đường).
     */
    @FXML
    private void onClearAllPlaces() {
        if (currentRoutePlaces.isEmpty()) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Không có địa điểm nào để xóa.");
            return;
        }

        // Hiển thị dialog xác nhận.
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Xác nhận xóa");
        confirmDialog.setHeaderText("Xóa tất cả các địa điểm đã chọn?");
        confirmDialog.setContentText("Bạn có chắc chắn muốn xóa tất cả các địa điểm khỏi lộ trình hiện tại không?");
        Optional<ButtonType> result = confirmDialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            currentRoutePlaces.clear(); // Xóa tất cả địa điểm khỏi danh sách.
            clearAllMarkers(); // Xóa tất cả marker trên bản đồ.
            clearRoute(); // Xóa lộ trình trên bản đồ.
            totalDistanceLabel.setText("Tổng quãng đường: 0 km"); // Reset nhãn quãng đường.
            if (statusLabel != null) {
                statusLabel.setText("Đã xóa tất cả địa điểm."); // Cập nhật nhãn trạng thái.
            }
            System.out.println("All places cleared.");
        }
    }
}