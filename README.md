# TourRoutePlanner

A JavaFX application for planning and visualizing tourist routes, initially focused on Vietnam.

## Features
- Search and display tourist places on an interactive map (powered by MapTiler).
- Plan and optimize travel routes between multiple destinations using OSRM.
- Reverse geocoding (find address from map click) and geocoding (find location from search) via Nominatim.
- Save and load custom routes in JSON format.
- View detailed route information (distance, estimated duration).
- Interactive map with custom markers and pop-up information.
- Support for Vietnamese locations and Unicode.

## Getting Started

### Prerequisites
- Java 17 or newer
- Maven 3.6+
- Valid JxBrowser License Key (for map display)
- Valid MapTiler API Key (for map tiles)

### Build and Run
1.  Clone this repository:
    ```sh
    git clone <your-repo-url>
    cd TourRoutePlanner
    ```
2.  **Configuration:**
    -   Navigate to `src/main/resources/`.
    -   Create a file named `config.properties` if it doesn't exist.
    -   Add your JxBrowser license key and MapTiler API key to `config.properties`:
        ```properties
        jxbrowser.license.key=YOUR_JXBROWSER_LICENSE_KEY
        maptiler.api.key=YOUR_MAPTILER_API_KEY
        # Optional: Configure local OSRM and Nominatim server URLs if needed
        # osrm.server.url=http://localhost:5000
        # nominatim.server.url=http://localhost:8080
        ```
3.  Build and run the application:
    ```sh
    mvn clean javafx:run
    ```

### Configuration Details
- The `config.properties` file in `src/main/resources/` is crucial for:
    -   `jxbrowser.license.key`: Your commercial or evaluation license key for JxBrowser.
    -   `maptiler.api.key`: Your API key from MapTiler for accessing map tiles.
    -   `osrm.server.url` (Optional): URL for your OSRM routing server. Defaults to a public server if not set.
    -   `nominatim.server.url` (Optional): URL for your Nominatim geocoding server. Defaults to a public server if not set.
- The application uses MapTiler for map tiles, OSRM for route calculation, and Nominatim for geocoding services.

## Project Structure
- `src/main/java/tourrouteplanner/` - Main application Java code (controllers, services, models).
- `src/main/resources/` - FXML layouts, `map.html` for JxBrowser, `styles.css`, `config.properties`, and icons.
- `data/` - Default directory for saved routes (JSON files).

## Technologies Used
- Java 17
- JavaFX (for the user interface)
- Maven (for project build and dependency management)
- JxBrowser (for embedding a web browser to display interactive maps)
- MapTiler (for map tiles and styles)
- OSRM (Open Source Routing Machine - for route calculation)
- Nominatim (for geocoding and reverse geocoding services based on OpenStreetMap data)
- Gson (for JSON serialization/deserialization)

## Author
- Cao Thang