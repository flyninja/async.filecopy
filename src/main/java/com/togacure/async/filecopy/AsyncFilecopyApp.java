package com.togacure.async.filecopy;

import com.togacure.async.filecopy.ui.MainController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncFilecopyApp extends Application {

	private MainController controller;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		log.info("primaryStage: {}", primaryStage);
		val loader = new FXMLLoader();
		val root = (Parent) loader.load(getClass().getResourceAsStream("/fxml/main.fxml"));
		val scene = new Scene(root);
		controller = loader.<MainController>getController();
		primaryStage.setTitle("Async file copy");
		primaryStage.setResizable(true);
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	@Override
	public void stop() throws Exception {
		log.info("");
		super.stop();
		controller.shutdown();
	}
}
