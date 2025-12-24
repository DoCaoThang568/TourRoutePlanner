package tourrouteplanner;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import tourrouteplanner.util.Constants;

import java.io.File;
import java.io.IOException;
import javafx.scene.control.Alert;

/**
 * Main application class for TourRoutePlanner.
 * Initializes the JavaFX user interface, loads the FXML file,
 * and manages the application lifecycle, including JxBrowser initialization and
 * shutdown.
 */
public class Main extends Application {

    private tourrouteplanner.controller.MainController mainController; // Stores the controller instance for shutdown
                                                                       // method call

    /**
     * Main entry point for all JavaFX applications.
     * The start method is called after the init method has returned,
     * and after the system is ready for the application to begin running.
     * This method loads the FXML file, sets up the controller, and displays the
     * main window.
     *
     * @param primaryStage the primary stage for this application, onto which
     *                     the application scene can be set.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            // Ensure data directory exists before application launches
            File dataDir = new File(Constants.DATA_PATH);
            if (!dataDir.exists()) {
                if (dataDir.mkdirs()) {
                    System.out.println("Data directory '" + Constants.DATA_PATH + "' has been created.");
                } else {
                    System.err.println("Could not create data directory '" + Constants.DATA_PATH
                            + "'. Please check write permissions.");
                    // Consider showing an Alert to the user here if directory creation is mandatory
                }
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("Main.fxml"));
            Parent root = loader.load();
            mainController = loader.getController(); // Get controller instance

            primaryStage.setTitle(Constants.APP_NAME);
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            // Handle critical error when FXML cannot be loaded
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Critical Startup Error");
            alert.setHeaderText("Could not start TourRoutePlanner application");
            alert.setContentText(
                    "A critical error occurred while loading the main user interface (Main.fxml).\\nThe application cannot continue.\\nError details: "
                            + e.getMessage());
            alert.showAndWait();
            // Consider System.exit(1) here if application cannot function without FXML
        } catch (NullPointerException e) {
            e.printStackTrace();
            // Handle error when FXML file is not found
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Critical Startup Error");
            alert.setHeaderText("Could not find user interface file");
            alert.setContentText(
                    "Main.fxml file not found. Ensure this file is located in resources/tourrouteplanner and has been built with the application.\\nThe application cannot continue.");
            alert.showAndWait();
            // Consider System.exit(1) here
        }
    }

    /**
     * This method is called when the JavaFX application terminates.
     * It ensures that resources, especially JxBrowser, are properly released.
     *
     * @throws Exception if an error occurs during the stop process.
     */
    @Override
    public void stop() throws Exception {
        if (mainController != null) {
            mainController.shutdownJxBrowser(); // Call JxBrowser shutdown method from controller
        }
        super.stop(); // Call parent class stop method
    }

    /**
     * Traditional Java main method.
     * Ignored in properly deployed JavaFX applications via JavaFX mechanisms.
     * main() only serves as a fallback in case the application cannot be
     * launched through deployment artifacts (e.g., in IDEs with limited JavaFX
     * support
     * or when running from command line without JavaFX launcher).
     * NetBeans and modern IDEs typically bypass main() and use
     * Application.launch().
     *
     * @param args command line arguments (typically unused in JavaFX GUI
     *             applications).
     */
    public static void main(String[] args) {
        launch(args); // Launch JavaFX application
    }
}