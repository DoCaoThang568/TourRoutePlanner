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
- Docker and Docker Compose (for running local Nominatim service)
- Valid JxBrowser License Key (for map display)
- Valid MapTiler API Key (for map tiles)

### Configuration
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

### Running with Docker (Optional - for local Nominatim)

This project includes a `docker-compose.yml` file to easily set up a local Nominatim service for geocoding. This is useful if you prefer not to rely on public Nominatim servers or require offline capabilities.

1.  **Prerequisites for Docker:**
    *   Ensure Docker and Docker Compose are installed on your system.
    *   Download the OpenStreetMap PBF data file for your desired region (e.g., `vietnam-latest.osm.pbf` from Geofabrik).

2.  **Configure Docker Compose:**
    *   Open the `docker-compose.yml` file.
    *   Modify the volume mapping for the PBF file. Change `/mydata/osm/vietnam-latest.osm.pbf` to the actual path of your downloaded PBF file on your host machine. For example, if your PBF file is in `C:\\osm_data\\vietnam-latest.osm.pbf`, the line should look like:
        ```yaml
        - C:\\osm_data\\vietnam-latest.osm.pbf:/data/region.osm.pbf:ro
        ```
    *   (Optional) Adjust the `THREADS` environment variable based on your system's CPU cores.
    *   (Important) Change the `NOMINATIM_PASSWORD` to a secure password.

3.  **Start the Nominatim Service:**
    *   Open a terminal in the project root directory (where `docker-compose.yml` is located).
    *   Run the following command:
        ```sh
        docker-compose up -d
        ```
    *   The first time you run this, Docker will download the Nominatim image and then import the PBF data. This import process can take a significant amount of time (from tens of minutes to several hours) depending on the size of the PBF file and your system's performance. You can monitor the progress using `docker-compose logs -f nominatim`.

4.  **Configure Application to Use Local Nominatim:**
    *   Once the Nominatim service is running (import is complete), update the `src/main/resources/config.properties` file:
        ```properties
        nominatim.server.url=http://localhost:8080
        ```
    *   If you stop and restart the Nominatim container later (e.g., `docker-compose stop` and `docker-compose start`), it will use the previously imported data and start much faster.

### Build and Run
1.  Ensure all configurations are set, including `config.properties`.
2.  Build and run the application:
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