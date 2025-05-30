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
import javafx.scene.layout.BorderPane; // Added import
import javafx.scene.control.ScrollPane; // Added import
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Controller chính cho giao diện người dùng của ứng dụng Tour Route Planner.
 * Quản lý các tương tác của người dùng, hiển thị bản đồ, tìm kiếm địa điểm,
 * lập kế hoạch lộ trình và lưu/tải lộ trình.
 */
public class MainController {

    @FXML
    private TextField searchBox; // Trường nhập liệu để tìm kiếm địa điểm.
    @FXML
    private ListView<Place> placeListView; // Danh sách hiển thị kết quả tìm kiếm.
    @FXML
    private ListView<String> suggestionsListView; // Danh sách hiển thị gợi ý tìm kiếm.
    @FXML
    private TableView<Place> routeTableView; // Bảng hiển thị các địa điểm đã chọn trong lộ trình hiện tại.
    @FXML
    private TableColumn<Place, String> routePlaceNameColumn; // Cột tên địa điểm trong bảng lộ trình.
    @FXML
    private TableColumn<Place, String> routePlaceAddressColumn; // Cột địa chỉ trong bảng lộ trình.
    @FXML
    private Button removeSelectedButton; // Nút để xóa địa điểm được chọn từ bảng lộ trình.
    @FXML
    private Button findRouteButton; // Nút để tìm và hiển thị lộ trình giữa các địa điểm đã chọn.
    @FXML
    private Button saveRouteButton; // Nút để lưu lộ trình hiện tại ra tệp.
    @FXML
    private Button loadRouteButton; // Nút để tải lộ trình từ tệp.    @FXML
    private Button moveUpButton; // Nút mũi tên lên để di chuyển địa điểm lên trên    @FXML
    private Button moveDownButton; // Nút mũi tên xuống để di chuyển địa điểm xuống dưới
    @FXML
    private Button clearAllButton; // Nút xóa tất cả địa điểm
    @FXML
    private Button darkModeToggle; // Nút chuyển đổi dark mode
    @FXML
    private Label searchPlaceholder; // Nhãn placeholder cho kết quả tìm kiếm.
    @FXML
    private Label routePlaceholder; // Nhãn placeholder cho danh sách lộ trình.
    @FXML
    private ProgressIndicator loadingSpinner; // Loading spinner
    @FXML
    private javafx.scene.layout.HBox loadingContainer; // Container cho loading animation
    // @FXML private Label totalDistanceLabel; // Removed field

    @FXML
    private StackPane mapPane; // Container cho bản đồ JxBrowser.

    @FXML
    private BorderPane mapAndControlsPane; // Added field
    @FXML
    private ScrollPane dynamicRouteInfoScrollPane; // Added field
    @FXML
    private TextArea dynamicRouteInfoTextArea; // Added field

    // Danh sách các địa điểm kết quả tìm kiếm, có thể được quan sát để cập nhật UI.
    private ObservableList<Place> searchResults = FXCollections.observableArrayList();
    // Danh sách các địa điểm trong lộ trình hiện tại, có thể được quan sát để cập nhật UI.
    private ObservableList<Place> currentRoutePlaces = FXCollections.observableArrayList();
    // Dịch vụ xử lý logic tìm kiếm địa điểm và lộ trình.
    private RouteService routeService;
    // Dịch vụ xử lý logic lưu và tải lộ trình.
    private StorageService storageService;    // Timer for debouncing search suggestions
    private ScheduledExecutorService suggestionsScheduler;
    private final ObservableList<String> searchSuggestions = FXCollections.observableArrayList();

    // Dark mode state
    private boolean isDarkMode = false;

    // Các thành phần của JxBrowser để hiển thị bản đồ.
    private Engine engine;
    private Browser browser;
    private BrowserView browserView;

    @FXML
    private Label statusLabel; // Nhãn hiển thị thông báo trạng thái cho người dùng.

    // Theo dõi xem đã tính lộ trình chưa
    private boolean routeCalculated = false;

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
        suggestionsScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true); // Allow application to exit even if this thread is running
            return thread;
        });        // Thiết lập ListView cho kết quả tìm kiếm địa điểm.
        placeListView.setItems(searchResults);
        placeListView.setCellFactory(param -> new ListCell<Place>() {
            private Button addButton;
            private HBox hbox;
            private VBox vbox;
            private Label nameLabel;
            private Label addressLabel;
            private final Tooltip tooltip = new Tooltip();

            { // Instance initializer block for the ListCell
                // Tạo các UI components
                addButton = new Button("Thêm");
                addButton.getStyleClass().add("place-add-button");                addButton.setOnAction(event -> {
                    Place place = getItem();
                    if (place != null && !currentRoutePlaces.contains(place)) {
                        currentRoutePlaces.add(place);
                        addMapMarker(place.getName(), place.getLatitude(), place.getLongitude(), place.getAddress());
                        statusLabel.setText("Đã thêm: " + place.getName());
                        // Refresh the ListView to update button states
                        placeListView.refresh();
                    }
                });

                nameLabel = new Label();
                nameLabel.getStyleClass().add("place-name");
                nameLabel.setWrapText(true);
                
                addressLabel = new Label();
                addressLabel.getStyleClass().add("place-address");
                addressLabel.setWrapText(true);

                vbox = new VBox(2);
                vbox.getChildren().addAll(nameLabel, addressLabel);
                vbox.setMaxWidth(Double.MAX_VALUE);

                hbox = new HBox(10);
                hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                hbox.getChildren().addAll(vbox, addButton);
                
                // Make vbox grow to fill available space
                javafx.scene.layout.HBox.setHgrow(vbox, javafx.scene.layout.Priority.ALWAYS);
            }

            @Override
            protected void updateItem(Place item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.getName());
                    String address = item.getAddress();
                    addressLabel.setText(address != null ? address : "");
                    
                    // Disable button if place is already in route
                    addButton.setDisable(currentRoutePlaces.contains(item));
                    
                    setGraphic(hbox);
                    setText(null);
                    
                    tooltip.setText(item.getName() + "\n" + (address != null ? address : ""));
                    setTooltip(tooltip);
                }
            }
        });        // Thêm listener để pan bản đồ khi một địa điểm được chọn trong placeListView.
        placeListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String geoJson = newSelection.getGeoJson();
                double[] boundingBox = newSelection.getBoundingBox();

                if (geoJson != null && !geoJson.trim().isEmpty()) {
                    // Ưu tiên highlight GeoJSON nếu có
                    highlightGeoJsonOnMap(geoJson);
                    // Vẫn zoom tới bounding box nếu có, vì GeoJSON có thể là điểm hoặc vùng nhỏ
                    if (boundingBox != null && boundingBox.length == 4) {
                        zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                    } else {
                        // Nếu không có bounding box, pan tới điểm trung tâm với mức zoom mặc định
                        panTo(newSelection.getLatitude(), newSelection.getLongitude(), 15); // Sử dụng int trực tiếp
                    }
                } else if (boundingBox != null && boundingBox.length == 4) {
                    // Nếu không có GeoJSON nhưng có bounding box, highlight bounding box
                    highlightBoundingBoxOnMap(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                    zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                } else {
                    // Nếu không có cả GeoJSON và bounding box, pan tới điểm trung tâm
                    panTo(newSelection.getLatitude(), newSelection.getLongitude(), 15); // Sử dụng int trực tiếp
                    clearMapHighlight(); // Xóa highlight cũ
                }
            } else {
                clearMapHighlight(); // Xóa highlight nếu không có địa điểm nào được chọn
            }
        });// Cấu hình cho TableView các địa điểm đã chọn trong lộ trình.
        routePlaceNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        routePlaceAddressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        routeTableView.setItems(currentRoutePlaces);
          // Thêm listener để bật/tắt các nút điều khiển dựa trên việc có địa điểm được chọn
        routeTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            // Bật/tắt nút xóa và các nút di chuyển tùy thuộc vào việc có địa điểm được chọn hay không
            boolean hasSelection = newSelection != null;
            removeSelectedButton.setDisable(!hasSelection);
            
            // Cập nhật placeholder visibility
            updateRoutePlaceholderVisibility();
            
            if (hasSelection) {
                int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
                moveUpButton.setDisable(selectedIndex <= 0); // Không thể di chuyển lên nếu là phần tử đầu tiên
                moveDownButton.setDisable(selectedIndex >= currentRoutePlaces.size() - 1); // Không thể di chuyển xuống nếu là phần tử cuối
            } else {
                moveUpButton.setDisable(true);
                moveDownButton.setDisable(true);
            }
            
            if (newSelection != null) {
                String geoJson = newSelection.getGeoJson();
                double[] boundingBox = newSelection.getBoundingBox();

                // Hiển thị địa điểm được chọn trên bản đồ
                if (geoJson != null && !geoJson.trim().isEmpty()) {
                    // Ưu tiên highlight GeoJSON nếu có
                    highlightGeoJsonOnMap(geoJson);
                    // Vẫn zoom tới bounding box nếu có, vì GeoJSON có thể là điểm hoặc vùng nhỏ
                    if (boundingBox != null && boundingBox.length == 4) {
                        zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                    } else {
                        // Nếu không có bounding box, pan tới điểm trung tâm với mức zoom mặc định
                        panTo(newSelection.getLatitude(), newSelection.getLongitude(), 15); 
                    }
                } else if (boundingBox != null && boundingBox.length == 4) {
                    // Nếu không có GeoJSON nhưng có bounding box, highlight bounding box
                    highlightBoundingBoxOnMap(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                    zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                } else {
                    // Nếu không có cả GeoJSON và bounding box, pan tới điểm trung tâm
                    panTo(newSelection.getLatitude(), newSelection.getLongitude(), 15);
                    clearMapHighlight(); // Xóa highlight cũ
                }
                
                // Hiển thị thông báo cho người dùng
                statusLabel.setText("Đã di chuyển đến địa điểm: " + newSelection.getName());
            }
        });
            // Thiết lập cấu hình chiều rộng cột để đảm bảo cột tên không chiếm quá nhiều không gian
        routePlaceNameColumn.setMinWidth(100);
        routePlaceNameColumn.setPrefWidth(180);  // Giá trị mặc định, sẽ được điều chỉnh dựa trên nội dung
        routePlaceNameColumn.setMaxWidth(250);    // Giới hạn kích thước tối đa
        
        // Cột địa chỉ sẽ linh hoạt hơn và giãn ra để lấp đầy phần còn lại
        routePlaceAddressColumn.setMinWidth(200);
          // Sử dụng chính sách điều chỉnh chiều rộng của TableView (chỉ đặt một lần)
        // Thay thế CONSTRAINED_RESIZE_POLICY với cách hiện đại hơn
        routeTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
          // Bắt sự kiện khi dữ liệu thay đổi để tự động điều chỉnh chiều rộng cột tên
        currentRoutePlaces.addListener((javafx.collections.ListChangeListener.Change<? extends Place> c) -> {
            boolean hasPlaces = !currentRoutePlaces.isEmpty();
            boolean hasEnoughPlaces = currentRoutePlaces.size() >= 2;
            
            // Bật/tắt các nút dựa trên số lượng địa điểm
            clearAllButton.setDisable(!hasPlaces);
            findRouteButton.setDisable(!hasEnoughPlaces);
              // Cập nhật trạng thái nút di chuyển nếu có selection
            Place selectedPlace = routeTableView.getSelectionModel().getSelectedItem();
            if (selectedPlace != null) {
                int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
                moveUpButton.setDisable(selectedIndex <= 0);
                moveDownButton.setDisable(selectedIndex >= currentRoutePlaces.size() - 1);
            }
            
            // Cập nhật placeholder visibility khi danh sách thay đổi
            updateRoutePlaceholderVisibility();
            
            if (!currentRoutePlaces.isEmpty()) {
                Platform.runLater(() -> {
                    // Tính toán chiều rộng tối ưu cho cột tên dựa trên nội dung hiện tại
                    double prefWidth = computePrefColumnWidth(routePlaceNameColumn, currentRoutePlaces);
                    if (prefWidth > 0) {
                        // Đặt chiều rộng cho cột tên trong giới hạn đã định
                        routePlaceNameColumn.setPrefWidth(Math.max(100, Math.min(prefWidth + 10, 250)));
                    }
                });
            }
        });
        
        // Thêm listener cho kích thước TableView để điều chỉnh tương đối khi thay đổi kích thước cửa sổ
        routeTableView.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) {
                double tableWidth = newVal.doubleValue();
                routePlaceNameColumn.setMaxWidth(tableWidth * 0.4); // Chiếm tối đa 40% chiều rộng bảng
            }
        });
        
        // CellFactory đơn giản chỉ để hiển thị text với wrap
        routePlaceNameColumn.setCellFactory(tc -> {
            TableCell<Place, String> cell = new TableCell<Place, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                    }
                    setWrapText(true);
                }
            };
            return cell;
        });
          routePlaceAddressColumn.setCellFactory(tc -> {
            TableCell<Place, String> cell = new TableCell<Place, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                    }
                    setWrapText(true);
                }            };
            return cell;
        });

        // Initialize suggestions ListView
        suggestionsListView.setItems(searchSuggestions);        // Xử lý khi người dùng click vào một gợi ý
        suggestionsListView.setOnMouseClicked(event -> {
            String selectedSuggestion = suggestionsListView.getSelectionModel().getSelectedItem();
            if (selectedSuggestion != null && !selectedSuggestion.isEmpty()) {
                // Ẩn danh sách gợi ý ngay lập tức
                suggestionsListView.setVisible(false);
                suggestionsListView.setManaged(false);
                
                // Điền vào ô tìm kiếm
                searchBox.setText(selectedSuggestion);
                
                // Tự động kích hoạt tìm kiếm để hiển thị kết quả chi tiết
                handleSearch();
            }
        });

        // Add listener to searchBox text property for suggestions
        searchBox.textProperty().addListener((observable, oldValue, newValue) -> {
            if (suggestionsScheduler != null && !suggestionsScheduler.isShutdown()) {
                suggestionsScheduler.shutdownNow(); // Cancel previous tasks
            }
            suggestionsScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
            });            if (newValue == null || newValue.trim().length() < 2) {
                searchSuggestions.clear();
                suggestionsListView.setVisible(false);
                suggestionsListView.setManaged(false);
                return;
            }

            suggestionsScheduler.schedule(() -> {
                fetchSearchSuggestions(newValue.trim());
            }, 200, TimeUnit.MILLISECONDS); // Giảm thời gian debounce xuống 200ms để phản hồi nhanh hơn
        });        // Ẩn suggestions khi searchBox mất focus, nhưng cho phép click vào suggestions
        searchBox.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                // Thêm delay để cho phép click vào suggestions hoạt động
                Platform.runLater(() -> {
                    if (!suggestionsListView.isFocused() && !searchBox.isFocused()) { 
                        suggestionsListView.setVisible(false);
                        suggestionsListView.setManaged(false);
                    }
                });
            } else {
                // Khi focus vào searchBox, hiển thị lại suggestions nếu có text phù hợp
                String currentText = searchBox.getText();
                if (currentText != null && currentText.length() >= 2 && !searchSuggestions.isEmpty()) {
                    suggestionsListView.setVisible(true);
                    suggestionsListView.setManaged(true);
                }
            }
        });

        // Khởi tạo JxBrowser để hiển thị bản đồ.
        initializeJxBrowser();

        // Initially hide the dynamic route info scroll pane and set its size
        if (dynamicRouteInfoScrollPane != null) {
            dynamicRouteInfoScrollPane.setVisible(false);
            dynamicRouteInfoScrollPane.setManaged(false); // So it doesn't take up space when invisible
            // Set preferred and minimum height. Adjust these values as needed.
            dynamicRouteInfoScrollPane.setPrefHeight(150);
            dynamicRouteInfoScrollPane.setMinHeight(100);
        }        if (dynamicRouteInfoTextArea != null) {
            dynamicRouteInfoTextArea.setEditable(false);
            dynamicRouteInfoTextArea.setWrapText(true);          }        // Thêm listener để tự động cập nhật placeholder khi danh sách tìm kiếm thay đổi
        searchResults.addListener((javafx.collections.ListChangeListener.Change<? extends Place> c) -> {
            updateSearchPlaceholderVisibility();
            if (searchResults.isEmpty()) {
                // Đảm bảo không có gì được chọn trong placeListView
                placeListView.getSelectionModel().clearSelection();
            }
        });
          // Khởi tạo trạng thái placeholder ban đầu
        updateRoutePlaceholderVisibility();
        updateSearchPlaceholderVisibility();
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
                    // System.out.println("Java object \'javaConnector\' exposed to JavaScript."); // Removed log
                } else {
                    System.err.println("Could not get window object from frame.");
                }
                return InjectJsCallback.Response.proceed();
            });

            // Lắng nghe và ghi lại các thông điệp từ console của JavaScript.
            browser.on(ConsoleMessageReceived.class, event -> {
                String message = "[JS " + event.consoleMessage().level() + "] " + event.consoleMessage().message();
                System.out.println(message); // Keep this for JS console messages
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
                // System.out.println("JxBrowser LoadFinished event. URL: \\"" + loadedUrl + "\\""); // Removed diagnostic log

                if (loadedUrl.endsWith("map.html")) {
                    // System.out.println("map.html loaded. Proceeding with API key injection."); // Removed diagnostic log
                    String maptilerApiKey = Utils.loadConfigProperty("maptiler.api.key");
                    if (maptilerApiKey != null && !maptilerApiKey.trim().isEmpty()) {
                        browser.mainFrame().ifPresent(frame -> {
                            // Tạo script để gán API key và gọi hàm khởi tạo bản đồ trong JavaScript.
                            String script = String.format("window.MAPTILER_API_KEY = '%s'; if(typeof initializeMapWithApiKey === \'function\') { console.log(\'Calling initializeMapWithApiKey from Java\'); initializeMapWithApiKey(); } else { console.error(\'initializeMapWithApiKey function not found in map.html\'); }", Utils.escapeJavaScriptString(maptilerApiKey));
                            // System.out.println("Executing script: " + script); // Removed log
                            frame.executeJavaScript(script);
                            // System.out.println("MapTiler API Key injected and initializeMapWithApiKey called."); // Removed log
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
     * Khởi tạo lại tất cả các marker trên bản đồ theo thứ tự hiện tại của các địa điểm trong lộ trình.
     */
    private void refreshMapMarkers() {
        clearAllMarkers();
        
        // Thêm lại marker cho tất cả các địa điểm theo thứ tự mới
        for (Place place : currentRoutePlaces) {
            addMapMarker(place.getName(), place.getLatitude(), 
                         place.getLongitude(), place.getAddress());
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

        // Hide suggestions when a search is explicitly triggered
        if (suggestionsListView != null) {
            suggestionsListView.setVisible(false);
            suggestionsListView.setManaged(false);
        }        if (query == null || query.trim().isEmpty()) {
            searchResults.clear(); // Xóa kết quả cũ nếu query rỗng.
            // Khôi phục suggestions nếu có text trong searchBox
            String currentText = searchBox.getText();
            if (currentText != null && !currentText.trim().isEmpty() && currentText.length() >= 2) {
                // Không ẩn suggestions nếu vẫn có text
            } else {
                suggestionsListView.setVisible(false);
                suggestionsListView.setManaged(false);
            }
            return;
        }try {
            List<Place> places = routeService.searchPlaces(query);
            searchResults.setAll(places); // Cập nhật danh sách kết quả.
            
            // Cập nhật placeholder (sẽ được xử lý bởi listener của searchResults)
            
            if (!places.isEmpty()) {
                placeListView.getSelectionModel().selectFirst(); // Chọn kết quả đầu tiên.
                // Place firstPlace = places.get(0);
                // Việc pan bản đồ sẽ được xử lý bởi listener của placeListView
                // panTo(firstPlace.getLatitude(), firstPlace.getLongitude());
            }

            // Diagnostic logging for map visibility - REMOVE THIS BLOCK
            /*
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
            */

        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tìm kiếm", "Không thể thực hiện tìm kiếm: " + e.getMessage());
        }
    }

    private void fetchSearchSuggestions(String query) {
        if (query.isEmpty()) {
            suggestionsListView.setVisible(false);
            suggestionsListView.setManaged(false);
            return;
        }

        // Cancel any previous suggestion tasks
        if (suggestionsScheduler != null && !suggestionsScheduler.isShutdown()) {
            suggestionsScheduler.shutdownNow();
        }
        suggestionsScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        suggestionsScheduler.schedule(() -> {
            try {
                List<Place> suggestedPlaces = routeService.searchPlaces(query); // Vẫn dùng query gốc cho API

                String normalizedUserQuery = Utils.normalizeForSearch(query);
                if (normalizedUserQuery == null || normalizedUserQuery.isEmpty()) {
                    Platform.runLater(() -> {
                        suggestionsListView.setVisible(false);
                        suggestionsListView.setManaged(false);
                    });
                    return;
                }                // Cải thiện logic lọc gợi ý để tránh trùng lặp và hiển thị tốt hơn
                List<String> suggestionNames = suggestedPlaces.stream()
                        .filter(place -> {
                            String placeName = place.getName();
                            String placeAddress = place.getAddress();
                            String combinedInfo = (placeName != null ? placeName : "") + (placeAddress != null ? " " + placeAddress : "");
                            String normalizedPlaceInfo = Utils.normalizeForSearch(combinedInfo);
                            return normalizedPlaceInfo != null && normalizedPlaceInfo.contains(normalizedUserQuery);
                        })
                        .sorted((p1, p2) -> {
                            // Ưu tiên những địa điểm có tên bắt đầu giống với query
                            String name1 = p1.getName() != null ? p1.getName().toLowerCase() : "";
                            String name2 = p2.getName() != null ? p2.getName().toLowerCase() : "";
                            String lowerQuery = query.toLowerCase();
                            
                            boolean starts1 = name1.startsWith(lowerQuery);
                            boolean starts2 = name2.startsWith(lowerQuery);
                            
                            if (starts1 && !starts2) return -1;
                            if (!starts1 && starts2) return 1;
                            
                            // Nếu cả hai đều bắt đầu hoặc không bắt đầu, sắp xếp theo độ dài tên
                            return name1.length() - name2.length();
                        })
                        .map(place -> {
                            String name = place.getName() != null ? place.getName() : "N/A";
                            String address = place.getAddress();
                            
                            // Chỉ hiển thị địa chỉ nếu nó cung cấp thông tin bổ sung có ý nghĩa
                            if (address != null && !address.isEmpty() && 
                                !address.equalsIgnoreCase(name) && 
                                address.length() > 10 && // Chỉ hiển thị địa chỉ đủ dài
                                !address.toLowerCase().equals(name.toLowerCase())) {
                                return name + ", " + address;
                            }
                            return name;
                        })
                        .distinct()
                        .limit(5) // Giảm số lượng gợi ý để tránh quá nhiều
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    if (!suggestionNames.isEmpty()) {
                        searchSuggestions.setAll(suggestionNames);
                        suggestionsListView.setVisible(true);
                        suggestionsListView.setManaged(true);
                    } else {
                        suggestionsListView.setVisible(false);
                        suggestionsListView.setManaged(false);
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    System.err.println("Lỗi khi tìm kiếm gợi ý: " + e.getMessage());
                    suggestionsListView.setVisible(false);
                    suggestionsListView.setManaged(false);
                });
            }
        }, 200, TimeUnit.MILLISECONDS); // Giảm thời gian debounce cho suggestions
    }

    /**
     * Tính toán chiều rộng phù hợp cho một cột dựa trên nội dung của nó
     * @param column Cột cần tính toán chiều rộng
     * @param items Danh sách các mục dữ liệu
     * @return Độ rộng phù hợp cho cột
     */
    private double computePrefColumnWidth(TableColumn<Place, String> column, ObservableList<Place> items) {
        double maxWidth = 50; // Chiều rộng tối thiểu

        // Tạo một Text tạm thời để đo chiều rộng
        javafx.scene.text.Text text = new javafx.scene.text.Text();
        
        // Lặp qua từng mục để đo chiều rộng
        for (Place item : items) {
            String value = "";
            if (column == routePlaceNameColumn && item.getName() != null) {
                value = item.getName();
            } else if (column == routePlaceAddressColumn && item.getAddress() != null) {
                value = item.getAddress();
            }
            
            // Đo chiều rộng của văn bản
            text.setText(value);
            double width = text.getBoundsInLocal().getWidth();
            
            // Cập nhật chiều rộng tối đa
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
          // Thêm một khoảng trống cho thanh cuộn
        return maxWidth + 20;
    }

    /**
     * Xử lý sự kiện xóa địa điểm được chọn từ routeTableView.
     * Xóa marker tương ứng và cập nhật lại lộ trình trên bản đồ.
     */@FXML
    private void handleRemoveSelected() {
        // Lấy địa điểm được chọn từ bảng các điểm trong lộ trình.
        Place selectedRoutePlace = routeTableView.getSelectionModel().getSelectedItem();
        if (selectedRoutePlace != null) {
            // Lưu lại vị trí của item được chọn để có thể chọn lại item khác sau khi xóa
            int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
            
            // Kiểm tra xem địa điểm bị xóa có phải là địa điểm đang được highlight không
            Place currentlySelectedInList = placeListView.getSelectionModel().getSelectedItem();
            boolean wasHighlightTarget = selectedRoutePlace.equals(currentlySelectedInList);

            currentRoutePlaces.remove(selectedRoutePlace); // Xóa khỏi danh sách lộ trình.
            clearAllMarkers();
            currentRoutePlaces.forEach(p -> addMapMarker(p.getName(), p.getLatitude(), p.getLongitude(), p.getAddress()));

            // Chọn một địa điểm mới trong bảng sau khi xóa, nếu còn địa điểm nào
            if (!currentRoutePlaces.isEmpty()) {
                // Nếu có item ở vị trí cũ, chọn item đó; nếu không thì chọn item trước đó
                if (selectedIndex < currentRoutePlaces.size()) {
                    routeTableView.getSelectionModel().select(selectedIndex);
                } else {
                    routeTableView.getSelectionModel().select(currentRoutePlaces.size() - 1);
                }
            } else {
                // Nếu không còn địa điểm nào, xóa highlight
                if (wasHighlightTarget) {
                    clearMapHighlight();
                }
            }
            
            if (routeCalculated && currentRoutePlaces.size() > 1) {
                // Chỉ tính toán lại lộ trình nếu đã tính trước đó và còn ít nhất 2 điểm
                handleFindRoute();
            } else if (currentRoutePlaces.size() <= 1) {
                clearRoute(); // Xóa lộ trình trên bản đồ nếu không còn đủ điểm.
                updateDynamicRouteInfo(String.format(Locale.US, "Tổng quãng đường: %.2f km", 0.0), null);
                routeCalculated = false;
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
            updateDynamicRouteInfo(String.format(Locale.US, "Tổng quãng đường: %.2f km", 0.0), ""); // Sửa ở đây, truyền chuỗi rỗng thay vì null
            return;
        }
        try {            Route route = routeService.getRoute(new ArrayList<>(currentRoutePlaces));
            if (route != null && route.getCoordinates() != null && !route.getCoordinates().isEmpty()) {
                drawRoute(route.getCoordinates()); // Vẽ lộ trình lên bản đồ.
                
                // Tự động zoom để hiển thị toàn bộ lộ trình
                fitToRoute();
                
                updateDynamicRouteInfo(
                    String.format(Locale.US, "Tổng quãng đường: %.2f km", route.getTotalDistanceKm()),
                    route.getTurnByTurnInstructions()
                );
                routeCalculated = true;
            } else {
                Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tìm đường", "Không thể tìm thấy đường đi cho các địa điểm đã chọn.");
                clearRoute();
                updateDynamicRouteInfo(String.format(Locale.US, "Tổng quãng đường: %.2f km", 0.0), ""); // Sửa ở đây
            }
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tìm đường", "Lỗi khi kết nối dịch vụ tìm đường: " + e.getMessage());
            clearRoute();
            updateDynamicRouteInfo(String.format(Locale.US, "Tổng quãng đường: %.2f km", 0.0), ""); // Sửa ở đây
        }
    }    /**
     * Cập nhật vùng hiển thị thông tin lộ trình động.
     * @param totalDistanceText Chuỗi hiển thị tổng quãng đường.
     * @param turnByTurnInstructions Chuỗi hướng dẫn chi tiết từng chặng.
     */
    private void updateDynamicRouteInfo(String totalDistanceText, String turnByTurnInstructions) {
        // Đặt giá trị biến routeCalculated
        if (totalDistanceText != null && !totalDistanceText.equals(String.format(Locale.US, "Tổng quãng đường: %.2f km", 0.0))) {
            // Nếu có thông tin quãng đường thì đã tính lộ trình
            routeCalculated = true;
        } else {
            // Nếu không có thông tin quãng đường hoặc quãng đường = 0.0
            routeCalculated = false;
        }
        
        if (dynamicRouteInfoScrollPane != null && dynamicRouteInfoTextArea != null) {
            StringBuilder infoBuilder = new StringBuilder();
            infoBuilder.append(totalDistanceText);

            if (turnByTurnInstructions != null && !turnByTurnInstructions.trim().isEmpty()) {
                infoBuilder.append("\n\nHướng dẫn chi tiết:\n");
                infoBuilder.append(turnByTurnInstructions);
            } else {
                // Optionally, add a message if there are no turn-by-turn instructions
                // infoBuilder.append("\n\n(Không có hướng dẫn chi tiết)");
            }

            dynamicRouteInfoTextArea.setText(infoBuilder.toString());

            boolean hasContent = totalDistanceText != null && !totalDistanceText.trim().isEmpty();
            dynamicRouteInfoScrollPane.setVisible(hasContent);
            dynamicRouteInfoScrollPane.setManaged(hasContent);
        } else {
            System.err.println("dynamicRouteInfoScrollPane or dynamicRouteInfoTextArea is null. Cannot update route info.");
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
                currentRoutePlaces.setAll(loadedData.getPlaces());
                clearAllMarkers();
                currentRoutePlaces.forEach(p -> addMapMarker(p.getName(), p.getLatitude(), p.getLongitude(), p.getAddress()));

                Route loadedRouteInfo = loadedData.getRoute();
                if (loadedRouteInfo != null && loadedRouteInfo.getCoordinates() != null && !loadedRouteInfo.getCoordinates().isEmpty()) {
                    drawRoute(loadedRouteInfo.getCoordinates());
                    updateDynamicRouteInfo(
                        String.format(Locale.US, "Tổng quãng đường: %.2f km", loadedRouteInfo.getTotalDistanceKm()),
                        loadedRouteInfo.getTurnByTurnInstructions()
                    );
                } else {
                    // Nếu không có thông tin lộ trình trong tệp (ví dụ: tệp cũ chỉ lưu địa điểm)
                    // hoặc thông tin lộ trình không hợp lệ, thì tính toán lại nếu có thể.
                    if (currentRoutePlaces.size() >= 2) {
                        handleFindRoute(); // Thử tìm lại lộ trình dựa trên các điểm đã tải.
                    } else {
                        clearRoute();
                        updateDynamicRouteInfo(String.format(Locale.US, "Tổng quãng đường: %.2f km", 0.0), null); // Sửa ở đây
                    }
                }
            } else {
                Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tải lộ trình", "Không thể tải dữ liệu lộ trình từ tệp đã chọn hoặc tệp không hợp lệ.");
                clearRoute();
                updateDynamicRouteInfo(String.format(Locale.US, "Tổng quãng đường: %.2f km", 0.0), null); // Sửa ở đây
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

    // --- Các phương thức giao tiếp với JavaScript trên bản đồ ---

    /**
     * Di chuyển và zoom bản đồ đến một tọa độ và mức zoom cụ thể.
     * Được gọi từ Java để điều khiển bản đồ JavaScript.
     *
     * @param latitude Vĩ độ của điểm đến.
     * @param longitude Kinh độ của điểm đến.
     * @param zoomLevel Mức zoom (số nguyên).
     */
    @JsAccessible
    public void panTo(double latitude, double longitude, int zoomLevel) { 
        if (browser != null && browser.mainFrame().isPresent()) {
            // Đảm bảo rằng zoomLevel là một số nguyên khi truyền vào JavaScript.
            String script = String.format(Locale.US, "if(typeof panTo === 'function') { panTo(%f, %f, %d); } else { console.error('JavaScript function panTo not found.'); }", latitude, longitude, zoomLevel);
            browser.mainFrame().get().executeJavaScript(script);
        }
    }

    /**
     * Yêu cầu JavaScript zoom bản đồ tới một bounding box.
     * @param southLat Vĩ độ Nam
     * @param northLat Vĩ độ Bắc
     * @param westLon Kinh độ Tây
     * @param eastLon Kinh độ Đông
     */
    private void zoomToBoundingBox(double southLat, double northLat, double westLon, double eastLon) {
        if (browser != null && browser.mainFrame().isPresent()) {
            String script = String.format(Locale.US, "if(typeof zoomToBoundingBox === 'function') { zoomToBoundingBox(%f, %f, %f, %f); } else { console.error('JavaScript function zoomToBoundingBox not found.'); }",
                                          southLat, northLat, westLon, eastLon);
            browser.mainFrame().get().executeJavaScript(script);
        }
    }

    /**
     * Yêu cầu JavaScript highlight một bounding box trên bản đồ.
     * @param southLat Vĩ độ Nam
     * @param northLat Vĩ độ Bắc
     * @param westLon Kinh độ Tây
     * @param eastLon Kinh độ Đông
     */
    private void highlightBoundingBoxOnMap(double southLat, double northLat, double westLon, double eastLon) {
        if (browser != null && browser.mainFrame().isPresent()) {
            String script = String.format(Locale.US, "if(typeof highlightBoundingBox === 'function') { highlightBoundingBox(%f, %f, %f, %f); } else { console.error('JavaScript function highlightBoundingBox not found.'); }",
                                          southLat, northLat, westLon, eastLon);
            browser.mainFrame().get().executeJavaScript(script);
        }
    }

    /**
     * Yêu cầu JavaScript highlight một đối tượng địa lý từ chuỗi GeoJSON.
     * @param geoJsonString Chuỗi GeoJSON của đối tượng.
     */
    private void highlightGeoJsonOnMap(String geoJsonString) {
        if (browser != null && browser.mainFrame().isPresent()) {
            // Cần escape chuỗi GeoJSON để nó hợp lệ trong một chuỗi JavaScript
            String escapedGeoJson = Utils.escapeJavaScriptString(geoJsonString);
            String script = String.format("if(typeof highlightGeoJsonFeature === 'function') { highlightGeoJsonFeature('%s'); } else { console.error('JavaScript function highlightGeoJsonFeature not found.'); }",
                                          escapedGeoJson);
            browser.mainFrame().get().executeJavaScript(script);
        }
    }

    /**
     * Yêu cầu JavaScript xóa mọi highlight hiện tại trên bản đồ.
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
     * Tự động zoom bản đồ để hiển thị toàn bộ lộ trình.
     * Gọi hàm JavaScript 'fitToRoute' trong map.html.
     */
    public void fitToRoute() {
        executeJavaScript("if(typeof fitToRoute === 'function') { fitToRoute(); } else { console.error('JavaScript function fitToRoute not found.'); }");
    }

    /**
     * Hiển thị và highlight địa điểm được chọn trên bản đồ.
     * @param place Địa điểm cần hiển thị và highlight
     */
    private void showSelectedPlaceOnMap(Place place) {
        if (place != null) {
            String geoJson = place.getGeoJson();
            double[] boundingBox = place.getBoundingBox();

            // Hiển thị địa điểm được chọn trên bản đồ
            if (geoJson != null && !geoJson.trim().isEmpty()) {
                // Ưu tiên highlight GeoJSON nếu có
                highlightGeoJsonOnMap(geoJson);
                // Vẫn zoom tới bounding box nếu có, vì GeoJSON có thể là điểm hoặc vùng nhỏ
                if (boundingBox != null && boundingBox.length == 4) {
                    zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                } else {
                    // Nếu không có bounding box, pan tới điểm trung tâm với mức zoom mặc định
                    panTo(place.getLatitude(), place.getLongitude(), 15); 
                }
            } else if (boundingBox != null && boundingBox.length == 4) {
                // Nếu không có GeoJSON nhưng có bounding box, highlight bounding box
                highlightBoundingBoxOnMap(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
                zoomToBoundingBox(boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3]);
            } else {
                // Nếu không có cả GeoJSON và bounding box, pan tới điểm trung tâm
                panTo(place.getLatitude(), place.getLongitude(), 15);
                clearMapHighlight(); // Xóa highlight cũ
            }
            
            // Hiển thị thông báo cho người dùng
            statusLabel.setText("Đã di chuyển đến địa điểm: " + place.getName());
        }
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
            // System.out.println("Map clicked at Lat: " + lat + ", Lng: " + lng); // Removed log
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

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Xác nhận xóa");
        confirmDialog.setHeaderText("Xóa tất cả các địa điểm đã chọn?");
        confirmDialog.setContentText("Bạn có chắc chắn muốn xóa tất cả các địa điểm khỏi lộ trình hiện tại không?");
        Optional<ButtonType> result = confirmDialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            currentRoutePlaces.clear(); 
            clearAllMarkers(); 
            clearRoute(); 
            clearMapHighlight(); 
            updateDynamicRouteInfo(String.format(Locale.US, "Tổng quãng đường: %.2f km", 0.0), null);
            if (statusLabel != null) {
                statusLabel.setText("Đã xóa tất cả các điểm. Lộ trình trống.");
            }
        }
    }    /**
     * Xử lý sự kiện khi người dùng nhấn nút mũi tên lên.
     * Di chuyển địa điểm được chọn lên trên một vị trí trong danh sách lộ trình.
     */    @FXML
    private void handleMoveUp() {
        int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
        if (selectedIndex > 0) { // Đảm bảo không phải địa điểm đầu tiên
            // Lưu lại địa điểm được chọn
            Place selectedPlace = currentRoutePlaces.get(selectedIndex);
            
            // Thực hiện hoán đổi vị trí
            currentRoutePlaces.remove(selectedIndex);
            currentRoutePlaces.add(selectedIndex - 1, selectedPlace);
            
            // Cập nhật lại hiển thị các marker trên bản đồ
            refreshMapMarkers();
            
            // Cập nhật lại bảng và chọn lại địa điểm vừa di chuyển
            routeTableView.getSelectionModel().select(selectedIndex - 1);
              // Đảm bảo địa điểm được chọn được highlight trên bản đồ
            showSelectedPlaceOnMap(selectedPlace);
            
            // Nếu đã tính lộ trình trước đó, thì tính toán lại lộ trình
            if (routeCalculated && currentRoutePlaces.size() > 1) {
                handleFindRoute();
            }
        } else {
            // Thông báo nếu không thể di chuyển lên (đã ở đầu danh sách)
            statusLabel.setText("Địa điểm này đã ở vị trí đầu tiên trong lộ trình.");
        }
    }    /**
     * Xử lý sự kiện khi người dùng nhấn nút mũi tên xuống.
     * Di chuyển địa điểm được chọn xuống dưới một vị trí trong danh sách lộ trình.
     */    @FXML
    private void handleMoveDown() {
        int selectedIndex = routeTableView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < currentRoutePlaces.size() - 1) { // Đảm bảo không phải địa điểm cuối cùng
            // Lưu lại địa điểm được chọn
            Place selectedPlace = currentRoutePlaces.get(selectedIndex);
            
            // Thực hiện hoán đổi vị trí
            currentRoutePlaces.remove(selectedIndex);
            currentRoutePlaces.add(selectedIndex + 1, selectedPlace);
            
            // Cập nhật lại hiển thị các marker trên bản đồ
            refreshMapMarkers();
            
            // Cập nhật lại bảng và chọn lại địa điểm vừa di chuyển
            routeTableView.getSelectionModel().select(selectedIndex + 1);
            
            // Đảm bảo địa điểm được chọn được highlight trên bản đồ
            showSelectedPlaceOnMap(selectedPlace);
            
            // Nếu đã tính lộ trình trước đó, thì tính toán lại lộ trình
            if (routeCalculated && currentRoutePlaces.size() > 1) {
                handleFindRoute();
            }
        } else if (selectedIndex == currentRoutePlaces.size() - 1) {
            // Thông báo nếu không thể di chuyển xuống (đã ở cuối danh sách)
            statusLabel.setText("Địa điểm này đã ở vị trí cuối cùng trong lộ trình.");
        }
    }

    /**
     * Xử lý sự kiện khi người dùng nhấn nút toggle dark mode.
     * Chuyển đổi giữa chế độ sáng và chế độ tối.
     */
    @FXML
    private void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        
        // Lấy root node của scene
        if (placeListView.getScene() != null && placeListView.getScene().getRoot() != null) {
            // Thêm hoặc xóa class "dark-mode" từ root node
            if (isDarkMode) {
                placeListView.getScene().getRoot().getStyleClass().add("dark-mode");
                statusLabel.setText("🌙 Đã chuyển sang chế độ tối");
            } else {
                placeListView.getScene().getRoot().getStyleClass().remove("dark-mode");
                statusLabel.setText("☀️ Đã chuyển sang chế độ sáng");
            }
        }
    }
      /**
     * Cập nhật trạng thái hiển thị của placeholder text cho bảng route.
     * Placeholder sẽ hiển thị khi:
     * - Bảng không có dữ liệu (trống)
     * Placeholder sẽ ẩn khi:
     * - Bảng có ít nhất một địa điểm
     */
    private void updateRoutePlaceholderVisibility() {
        if (routePlaceholder != null) {
            boolean shouldShowPlaceholder = currentRoutePlaces.isEmpty();
            routePlaceholder.setVisible(shouldShowPlaceholder);
        }
    }
    
    /**
     * Cập nhật trạng thái hiển thị của placeholder cho kết quả tìm kiếm
     */
    private void updateSearchPlaceholderVisibility() {
        if (searchPlaceholder != null) {
            boolean hasResults = !searchResults.isEmpty();            searchPlaceholder.setVisible(!hasResults);
            searchPlaceholder.setManaged(!hasResults);
        }
    }
}
