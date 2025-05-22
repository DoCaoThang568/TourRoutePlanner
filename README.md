# TourRoutePlanner

A JavaFX application for planning and visualizing tourist routes in Vietnam.

## Features
- Search and display tourist places on an interactive map
- Plan and optimize travel routes between multiple destinations
- Save and load custom routes
- View detailed route information (distance, duration)
- Support for Vietnamese locations and Unicode

## Getting Started

### Prerequisites
- Java 17 or newer
- Maven 3.6+

### Build and Run
1. Clone this repository:
   `sh
   git clone <your-repo-url>
   cd TourRoutePlanner
   `
2. Build and run the application:
   `sh
   mvn clean javafx:run
   `

### Configuration
- Edit src/main/resources/config.properties to set up API endpoints if needed.
- The application uses OpenStreetMap and OSRM for route calculation.

## Project Structure
- src/main/java/tourrouteplanner/ - Main application code
- src/main/resources/ - FXML layouts, HTML map, config
- data/ - (Optional) Saved routes and user data

## Technologies
- Java 17
- JavaFX
- Maven
- OpenStreetMap, OSRM

## Author
- Cao Thang