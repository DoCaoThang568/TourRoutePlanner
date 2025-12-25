# 🗺️ Tour Route Planner

![Status](https://img.shields.io/badge/Status-Active-brightgreen)
![Java](https://img.shields.io/badge/Java-17+-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![Maven](https://img.shields.io/badge/Maven-3.6+-red)
![License](https://img.shields.io/badge/License-MIT-green)

A professional JavaFX application for planning and visualizing tourist routes, featuring interactive maps, turn-by-turn navigation, and route optimization.

## ✨ Features

### 🗺️ Interactive Mapping

- **Smart Map Integration**: Powered by JxBrowser and OpenLayers.
- **MapTiler Integration**: High-quality vector tiles.
- **Search & Geocoding**: Find any place using Nominatim API.
- **Reverse Geocoding**: Click anywhere on the map to get the address.

### 🚗 Advanced Routing

- **OSRM Powered**: Fast and accurate routing engine.
- **Turn-by-urn Directions**: Detailed navigation instructions.
- **Route Management**: Add, remove, and reorder stops easily.
- **Save & Load**: Persist your favorite routes as JSON files.

### 🎨 Modern UI/UX

- **Glassmorphism Design**: Modern, sleek interface with glass effects.
- **Dark Mode**: Fully supported dark theme for night usage.
- **Responsive Layout**: Adapts to different window sizes.
- **Keyboard Shortcuts**: Fast navigation with hotkeys.

## ⌨️ Keyboard Shortcuts

| Shortcut     | Action                    |
| ------------ | ------------------------- |
| `Ctrl` + `S` | 💾 Save Route             |
| `Ctrl` + `O` | 📂 Load Route             |
| `Ctrl` + `F` | 🔍 Focus Search Box       |
| `Delete`     | 🗑️ Remove Selected Place  |
| `Escape`     | ❌ Clear Selection/Search |

## 🏗️ Architecture

The project follows a **Clean Architecture** and **SOLID** principles approach:

```
src/main/java/tourrouteplanner
├── controller/       # UI Logic & Event Handlers
│   ├── MainController.java
│   ├── MapHelper.java
│   ├── SearchHelper.java
│   └── RouteHelper.java
├── model/            # Data Models (Place, Route)
├── service/          # Business Logic
│   ├── RoutingService.java    # OSRM integration
│   ├── GeocodingService.java  # Nominatim integration
│   └── StorageService.java    # JSON persistence
├── util/             # Cross-cutting concerns
    ├── Constants.java
    └── InstructionFormatter.java
```

### 📝 Professional Logging

- **SLF4J + Logback**: Fully implemented structured logging.
- **Log Rotation**: Automatic log file rotation and archiving.
- **Console & File**: Logs output to both console and `logs/app.log`.

## 🚀 Getting Started

### Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **JxBrowser License** (Required)
- **MapTiler API Key** (Free tier available)

### Configuration

1. Clone the repository:

   ```bash
   git clone <your-repo-url>
   cd TourRoutePlanner
   ```

2. Create `src/main/resources/config.properties`:

   ```properties
   jxbrowser.license.key=YOUR_LICENSE_KEY
   maptiler.api.key=YOUR_API_KEY
   # Optional: Local server overrides
   # osrm.server.url=http://localhost:5000
   # nominatim.server.url=http://localhost:8080
   ```

3. Build and Run:
   ```bash
   mvn clean javafx:run
   ```

### 🐳 Docker (Optional)

Run a local Nominatim server for offline geocoding:

```bash
docker-compose up -d
```

Update `config.properties` to use `http://localhost:8080`.

## 🛠️ Technologies

- **JavaFX 21**: Modern desktop UI toolkit.
- **JxBrowser 7**: Chromium-based browser embedding.
- **OpenLayers**: Advanced map visualization.
- **Gson**: JSON data handling.
- **SLF4J / Logback**: Industry-standard logging.

## 👥 Author

- **Cao Thang**
