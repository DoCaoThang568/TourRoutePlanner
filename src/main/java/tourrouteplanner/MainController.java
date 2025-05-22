package tourrouteplanner;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import tourrouteplanner.model.Place;
import tourrouteplanner.model.Route;
import tourrouteplanner.service.RouteService;
import tourrouteplanner.service.StorageService;
import tourrouteplanner.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.stage.FileChooser;

/**
 * Lớp Controller cho cửa sổ ứng dụng chính (Main.fxml).
 * Xử lý các tương tác của người dùng, hiển thị bản đồ, tìm kiếm tuyến đường và quản lý địa điểm.
 */
public class MainController {

    private ContextMenu suggestionMenu = new ContextMenu();

    @FXML private WebView mapWebView;
    @FXML private ListView<Place> placesListView;
    @FXML private TextField searchTextField;
    @FXML private TextArea routeInfoTextArea;
    @FXML private Label statusLabel;

    private WebEngine webEngine;
    private JSObject jsObject;
    private final ObservableList<Place> selectedPlaces = FXCollections.observableArrayList();
    private RouteService routeService;
    private StorageService storageService;

    /**
     * Khởi tạo lớp controller. Phương thức này được tự động gọi
     * sau khi tệp FXML đã được tải.
     */
    @FXML
    public void initialize() {
        routeService = new RouteService();
        storageService = new StorageService();

        placesListView.setItems(selectedPlaces);
        placesListView.setCellFactory(param -> new ListCell<Place>() {
            @Override
            protected void updateItem(Place item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.getName() == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        searchTextField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.trim().isEmpty()) {
                suggestionMenu.hide();
                return;
            }
            if (newText.trim().length() < 2) {
                suggestionMenu.hide();
                return;
            }
            new Thread(() -> {
                try {
                    List<Place> suggestions = routeService.searchLocationByName(newText.trim());
                    Platform.runLater(() -> showSuggestions(suggestions));
                } catch (Exception e) {
                    Platform.runLater(() -> suggestionMenu.hide());
                }
            }).start();
        });
        searchTextField.focusedProperty().addListener((obs, was, isNow) -> {
            if (!isNow) suggestionMenu.hide();
        });

        webEngine = mapWebView.getEngine();
        webEngine.setJavaScriptEnabled(true);

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                jsObject = (JSObject) webEngine.executeScript("window");
                try {
                    jsObject.setMember("javaConnector", this);
                } catch (netscape.javascript.JSException e) {
                    System.err.println("MainController: Error setting javaConnector on jsObject: " + e.getMessage());
                    e.printStackTrace();
                }

                String script = "console.log = function(message) { javaConnector.logJs('[JS LOG] ' + message); };" +
                              "console.error = function(message) { javaConnector.logJs('[JS ERROR] ' + message); };";
                try {
                    webEngine.executeScript(script);
                } catch (netscape.javascript.JSException e) {
                    System.err.println("MainController: Error setting up JS console redirection: " + e.getMessage());
                    e.printStackTrace();
                }

                try {
                    webEngine.executeScript("if (typeof setJavaConnector === 'function') { setJavaConnector(window.javaConnector); } else { console.error('[MAP.HTML direct call] setJavaConnector is not a function or not found.'); }");
                } catch (netscape.javascript.JSException e) {
                    System.err.println("MainController: Error explicitly calling JS functions: " + e.getMessage());
                }

                mapWebView.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWindow, oldWindow, newWindow) -> {
                            if (newWindow != null) {
                                newWindow.setOnShown(event -> {
                                    Platform.runLater(() -> {
                                        try {
                                            String invalidateScript = "if (typeof map !== 'undefined' && map) {" +
                                                                  "  setTimeout(function() { map.invalidateSize(true); console.log(\\'[MAP.HTML from Stage.onShown] map.invalidateSize(true) called.\\'); }, 250);" +
                                                                  "} else {" +
                                                                  "  console.log(\\'[MAP.HTML from Stage.onShown] map object not ready for invalidateSize.\\');" +
                                                                  "}";
                                            webEngine.executeScript(invalidateScript);
                                        } catch (Exception e) {
                                            System.err.println("MainController: Error calling invalidateSize from Stage.onShown: " + e.getMessage());
                                        }
                                    });
                                });
                            }
                        });
                    }
                });

                statusLabel.setText("Bản đồ đã tải. Sẵn sàng sử dụng OpenStreetMap.");
                
            } else if (newState == javafx.concurrent.Worker.State.FAILED) {
                statusLabel.setText("Lỗi tải bản đồ (map.html).");
                Utils.showAlert(Alert.AlertType.ERROR, "Lỗi WebView", "Không thể tải map.html. Kiểm tra đường dẫn và nội dung tệp.");
                if (webEngine.getLoadWorker().getException() != null) {
                    System.err.println("MainController: WebEngine exception: " + webEngine.getLoadWorker().getException().getMessage());
                    webEngine.getLoadWorker().getException().printStackTrace();
                }
            } else if (newState == javafx.concurrent.Worker.State.CANCELLED) {
                 System.out.println("MainController: WebEngine LoadWorker CANCELLED.");
            }
        });
        
        try {
            String mapUrl = getClass().getResource("/tourrouteplanner/map.html").toExternalForm();
            if (mapUrl == null) {
                System.err.println("MainController: map.html not found, mapUrl is null!");
                statusLabel.setText("Lỗi: Không tìm thấy map.html trong resources.");
                Utils.showAlert(Alert.AlertType.ERROR, "Lỗi Tải Bản Đồ", "Không tìm thấy tệp map.html trong resources/tourrouteplanner. Hãy chắc chắn tệp ở đúng vị trí.");
                return;
            }
            webEngine.load(mapUrl);
        } catch (NullPointerException e) {
            statusLabel.setText("Lỗi: Không tìm thấy map.html");
            Utils.showAlert(Alert.AlertType.ERROR, "Lỗi Tải Bản Đồ", "Không tìm thấy tệp map.html trong resources/tourrouteplanner. Hãy chắc chắn tệp ở đúng vị trí.");
            e.printStackTrace();
        }
    }

    private void showSuggestions(List<Place> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            suggestionMenu.hide();
            return;
        }
        suggestionMenu.getItems().clear();
        for (Place place : suggestions) {
            MenuItem item = new MenuItem(place.toString());
            item.setOnAction(e -> {
                searchTextField.setText(place.getName());
                addPlaceToSelectedList(place);
                suggestionMenu.hide();
            });
            suggestionMenu.getItems().add(item);
        }
        if (!suggestionMenu.isShowing()) {
            suggestionMenu.show(searchTextField, javafx.geometry.Side.BOTTOM, 0, 0);
        }
    }

    /**
     * Xử lý các sự kiện nhấp chuột trên bản đồ được chuyển tiếp từ JavaScript.
     * Nhắc người dùng đặt tên cho một địa điểm mới tại tọa độ đã nhấp.
     * @param lat Vĩ độ của điểm đã nhấp.
     * @param lng Kinh độ của điểm đã nhấp.
     */
    public void handleMapClick(double lat, double lng) {
        Platform.runLater(() -> {
            String placeName = "Điểm " + (selectedPlaces.size() + 1);
            TextInputDialog dialog = new TextInputDialog(placeName);
            dialog.setTitle("Thêm địa điểm mới");
            dialog.setHeaderText("Nhập tên cho địa điểm tại (" + String.format(Locale.US, "%.5f, %.5f", lat, lng) + ")");
            dialog.setContentText("Tên địa điểm:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                if (name.trim().isEmpty()) {
                    Utils.showAlert(Alert.AlertType.WARNING, "Tên không hợp lệ", "Tên địa điểm không được để trống.");
                    return;
                }
                Place newPlace = new Place(name, lat, lng);
                if (!selectedPlaces.stream().anyMatch(p -> p.getLat() == lat && p.getLng() == lng)) {
                    selectedPlaces.add(newPlace);
                    if (jsObject != null) {
                        jsObject.call("addMapMarker", lat, lng, name);
                    }
                    statusLabel.setText("Đã thêm: " + name);
                } else {
                    Utils.showAlert(Alert.AlertType.INFORMATION, "Địa điểm đã tồn tại", "Địa điểm với tọa độ này đã có trong danh sách.");
                }
            });
        });
    }

    /**
     * Ghi lại các thông báo nhận được từ JavaScript vào console của Java.
     * Phương thức này được gọi bởi các hàm JavaScript (ví dụ: console.log).
     * @param message Chuỗi thông báo từ JavaScript.
     */
    public void logJs(String message) {
        System.out.println(message);
    }

    @FXML
    private void onSearchLocation() {
        String searchText = searchTextField.getText();
        if (searchText == null || searchText.trim().isEmpty()) {
            Utils.showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập tên địa điểm để tìm kiếm.");
            return;
        }
        statusLabel.setText("Đang tìm kiếm: " + searchText + "...");

        new Thread(() -> {
            try {
                List<Place> searchResults = routeService.searchLocationByName(searchText);
                Platform.runLater(() -> {
                    if (searchResults == null || searchResults.isEmpty()) {
                        statusLabel.setText("Không tìm thấy kết quả cho: " + searchText);
                        Utils.showAlert(Alert.AlertType.INFORMATION, "Không tìm thấy", "Không tìm thấy địa điểm nào phù hợp với tìm kiếm của bạn.");
                    } else {
                        statusLabel.setText("Tìm thấy " + searchResults.size() + " kết quả cho: " + searchText);
                        // Display results in a dialog for user to choose
                        ChoiceDialog<Place> dialog = new ChoiceDialog<>(searchResults.get(0), searchResults);
                        dialog.setTitle("Kết quả tìm kiếm");
                        dialog.setHeaderText("Chọn một địa điểm từ kết quả tìm kiếm cho '" + searchText + "'");
                        dialog.setContentText("Địa điểm:");

                        Optional<Place> result = dialog.showAndWait();
                        result.ifPresent(this::addPlaceToSelectedList);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("Lỗi khi tìm kiếm địa điểm.");
                    Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tìm kiếm", "Đã xảy ra lỗi khi tìm kiếm địa điểm: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void onAddPlaceFromSearch() {
        Utils.showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Vui lòng sử dụng ô tìm kiếm và nút \"Tìm kiếm\". Địa điểm sẽ được thêm sau khi bạn chọn từ kết quả.");
        statusLabel.setText("Sử dụng tìm kiếm để thêm địa điểm.");
    }

    private void addPlaceToSelectedList(Place place) {
        if (place == null) return;

        if (!selectedPlaces.stream().anyMatch(p -> p.getLat() == place.getLat() && p.getLng() == place.getLng())) {
            selectedPlaces.add(place);
            if (jsObject != null) {
                jsObject.call("addMapMarker", place.getLat(), place.getLng(), place.getName());
                jsObject.call("panTo", place.getLat(), place.getLng()); 
            }
            statusLabel.setText("Đã thêm: " + place.getName());
        } else {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Địa điểm đã tồn tại", "Địa điểm '" + place.getName() + "' đã có trong danh sách.");
        }
    }

    @FXML
    private void onRemovePlace() {
        Place selectedPlace = placesListView.getSelectionModel().getSelectedItem();
        if (selectedPlace != null) {
            selectedPlaces.remove(selectedPlace);
            if (jsObject != null) {
                jsObject.call("removeMapMarker", selectedPlace.getLat(), selectedPlace.getLng());
            }
            routeInfoTextArea.clear();
            if (jsObject != null) jsObject.call("clearRoute");
            statusLabel.setText("Đã xóa: " + selectedPlace.getName());
            if (selectedPlaces.size() >= 2) {
                drawSimpleRouteOnMap();
            }
        } else {
            Utils.showAlert(Alert.AlertType.WARNING, "Chưa chọn điểm", "Vui lòng chọn một điểm trong danh sách để xóa.");
        }
    }
    
    @FXML
    private void onClearAllPlaces() {
        if (selectedPlaces.isEmpty()) {
            Utils.showAlert(Alert.AlertType.INFORMATION, "Danh sách trống", "Không có địa điểm nào để xóa.");
            return;
        }
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION, "Bạn có chắc chắn muốn xóa tất cả các địa điểm đã chọn không?", ButtonType.YES, ButtonType.NO);
        confirmDialog.setTitle("Xác nhận xóa");
        confirmDialog.setHeaderText("Xóa tất cả địa điểm");
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            selectedPlaces.clear();
            if (jsObject != null) {
                jsObject.call("clearAllMarkers");
                jsObject.call("clearRoute");
            }
            routeInfoTextArea.clear();
            statusLabel.setText("Đã xóa tất cả các điểm.");
        }
    }

    @FXML
    private void onFindRoute() {
        if (selectedPlaces.size() < 2) {
            routeInfoTextArea.setText("Cần ít nhất 2 điểm để tạo lộ trình.");
            Utils.showAlert(Alert.AlertType.WARNING, "Thiếu điểm", "Cần ít nhất 2 điểm để tạo lộ trình.");
            if (jsObject != null) jsObject.call("clearRoute");
            return;
        }
        statusLabel.setText("Đang tìm lộ trình với OSRM...");
        routeInfoTextArea.setText("Đang xử lý, vui lòng đợi...");

        new Thread(() -> {
            try {
                List<Place> waypoints = new ArrayList<>(selectedPlaces);
                Route route = routeService.getRouteFromOSRM(waypoints); 

                if (route == null || route.getPolyline() == null || route.getPolyline().isEmpty()) {
                     Platform.runLater(() -> {
                        routeInfoTextArea.setText("Không tìm thấy lộ trình OSRM hoặc có lỗi xảy ra.");
                        statusLabel.setText("Không tìm thấy lộ trình OSRM.");
                        if (jsObject != null) jsObject.call("clearRoute");
                    });
                    return;
                }

                List<String> pointsForJS = route.getPolyline().stream()
                   .map(coord -> String.format(Locale.US, "{\"lat\": %.7f, \"lng\": %.7f}", coord.lat, coord.lng))
                   .collect(Collectors.toList());
                String jsRouteArray = "[" + String.join(",", pointsForJS) + "]";

                Platform.runLater(() -> {
                    if (jsObject != null) {
                       jsObject.call("drawRoute", jsRouteArray); 
                    }
                    String routeDetails = String.format(Locale.US, 
                            "Lộ trình được tìm thấy bởi OSRM:\n" +
                            "- Tổng quãng đường: %.2f km\n" +
                            "- Tổng thời gian dự kiến: %.0f phút\n" +
                            "- Các điểm dừng:\n  %s",
                            route.getTotalDistance(),
                            route.getTotalDuration(),
                            route.getWaypoints().stream().map(Place::getName).collect(Collectors.joining("\n  "))
                    );
                    routeInfoTextArea.setText(routeDetails);
                    statusLabel.setText("Đã tìm thấy và vẽ lộ trình OSRM.");
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    routeInfoTextArea.setText("Lỗi khi tìm lộ trình OSRM: " + e.getMessage());
                    statusLabel.setText("Lỗi khi tìm lộ trình OSRM.");
                    Utils.showAlert(Alert.AlertType.ERROR, "Lỗi Tìm Lộ Trình OSRM", "Đã xảy ra lỗi: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void onSaveRoute() {
        if (selectedPlaces.isEmpty()) {
            Utils.showAlert(Alert.AlertType.WARNING, "Lộ trình trống", "Không có địa điểm nào trong lộ trình để lưu.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Lưu lộ trình");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tệp JSON (*.json)", "*.json"));
        File file = fileChooser.showSaveDialog(statusLabel.getScene().getWindow());

        if (file != null) {
            storageService.savePlaces(new ArrayList<>(selectedPlaces), file.getAbsolutePath());
            statusLabel.setText("Đã lưu lộ trình vào: " + file.getName());
            Utils.showAlert(Alert.AlertType.INFORMATION, "Lưu thành công", "Lộ trình đã được lưu thành công.");
        }
    }

    @FXML
    private void onLoadRoute() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Tải lộ trình");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tệp JSON (*.json)", "*.json"));
        File file = fileChooser.showOpenDialog(statusLabel.getScene().getWindow());

        if (file != null) {
            List<Place> loadedPlaces = storageService.loadPlaces(file.getAbsolutePath());
            if (loadedPlaces != null && !loadedPlaces.isEmpty()) {
                selectedPlaces.clear();
                if (jsObject != null) {
                    jsObject.call("clearAllMarkers");
                    jsObject.call("clearRoute");
                }
                routeInfoTextArea.clear();
                
                selectedPlaces.addAll(loadedPlaces);
                statusLabel.setText("Đã tải lộ trình từ: " + file.getName());
                
                if (jsObject != null) {
                    for (Place place : selectedPlaces) {
                        jsObject.call("addMapMarker", place.getLat(), place.getLng(), place.getName());
                    }
                }
                if (selectedPlaces.size() >= 2) {
                    onFindRoute(); 
                }
            } else {
                statusLabel.setText("Không thể tải lộ trình hoặc tệp trống.");
                Utils.showAlert(Alert.AlertType.ERROR, "Lỗi tải tệp", "Không thể tải lộ trình từ tệp đã chọn hoặc tệp không chứa dữ liệu hợp lệ.");
            }
        }
    }

    @FXML
    private void onExit() {
        Platform.exit();
    }

    @FXML
    private void onAbout() {
        Utils.showAlert(Alert.AlertType.INFORMATION, "Thông tin ứng dụng",
                "Tour Route Planner v1.0\\n" +
                "Ứng dụng lập kế hoạch lộ trình du lịch.\\n" +
                "Sử dụng OpenStreetMap và OSRM.");
    }

    private void drawSimpleRouteOnMap() {
        if (jsObject != null && selectedPlaces.size() >= 2) {
            onFindRoute(); 
        } else if (jsObject != null) {
            jsObject.call("clearRoute");
        }
    }
}