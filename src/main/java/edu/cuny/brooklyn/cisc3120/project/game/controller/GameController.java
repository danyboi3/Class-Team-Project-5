package edu.cuny.brooklyn.cisc3120.project.game.controller;

import edu.cuny.brooklyn.cisc3120.project.game.TargetGameApp;
import edu.cuny.brooklyn.cisc3120.project.game.model.*;
import edu.cuny.brooklyn.cisc3120.project.game.model.DecisionWrapper.UserDecision;
import edu.cuny.brooklyn.cisc3120.project.game.model.GameStatistics.StatNameValue;
import edu.cuny.brooklyn.cisc3120.project.game.net.StatusBroadcaster;
import edu.cuny.brooklyn.cisc3120.project.game.utils.I18n;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class GameController {
	private final static Logger LOGGER = LoggerFactory.getLogger(GameController.class);
	private final static String APP_TITLE = "CISC 3120 Fall 2017: TargetGame";
	private Target target;
	@FXML
	private Canvas targetCanvas;

	@FXML
	private TextField xAimedAtTextField;

	@FXML
	private TextField yAimedAtTextField;

	@FXML
	private Button fireWeaponButton;

	@FXML
	private ComboBox<Locale> lcComboBox;

	@FXML
	private TableView<StatNameValue> gameStatTableView;

	@FXML
	private TableColumn<StatNameValue, String> tableViewStatName;

	@FXML
	private TableColumn<StatNameValue, String> tableViewStatValue;

	@FXML
	private VBox playersOnLineVbox;

	private TargetGame targetGame = new TargetGame();

	private Stage stage;

	private StatusBroadcaster statusBroadCaster;

	public void setStage(Stage stage) {
		this.stage = stage;
		this.stage.setOnCloseRequest(event -> {
			LOGGER.debug("User clicked the X button on the stage.");
			exitGame(event);
		});
	}

	@FXML
	void initialize() throws IOException, URISyntaxException {
		LOGGER.debug("Initializing GameController.");
		setWeaponDisable(true);
		initializeI18n();
		gameStatTableView.setVisible(false);
		playersOnLineVbox.setVisible(false);
		statusBroadCaster = new StatusBroadcaster();
		statusBroadCaster.start();
	}

	@FXML
	void fireTheWeapon(ActionEvent event) {
		LOGGER.debug("Weapon fired!");
		int shotX = Integer.parseInt(xAimedAtTextField.getText());
		int shotY = Integer.parseInt(yAimedAtTextField.getText());
		Shot shot = new Shot(shotX, shotY);
		processShotAction(targetGame, shot);
	}

	@FXML
	void exitGame(ActionEvent event) {
		LOGGER.debug("calling exitGame(ActionEvent event).");
		exitGame((Event) event);
	}

	@FXML
	void newGame(ActionEvent event) {
		LOGGER.debug("started new game.");
		lcComboBox.setDisable(true); // don't allow users to change locale when a game is in session
		addTarget(targetGame, targetCanvas);
		setWeaponDisable(false);
		gameStatTableView.setVisible(true);
		gameStatTableView.setItems(targetGame.getGameStatistics().toObservableList());
		tableViewStatName.setCellValueFactory(new PropertyValueFactory<StatNameValue, String>(StatNameValue.COLUMN_NAME_TITLE));
		tableViewStatValue.setCellValueFactory(new PropertyValueFactory<StatNameValue, String>(StatNameValue.COLUMN_VALUE_TITLE));
		gameStatTableView.getColumns().set(0, tableViewStatName);
		gameStatTableView.getColumns().set(1, tableViewStatValue);
		playersOnLineVbox.setVisible(true);
	}

	@FXML
	void openGame(ActionEvent event) throws IOException {
		String fileData = String.join("", Files.readAllLines(Paths.get(System.getenv("APPDATA"), "targetgame-save.txt")));
		String[] data = fileData.split(" ");
		GameStatistics stats = targetGame.getGameStatistics();

		stats.setNumberOfGuesses(Integer.parseInt(data[0]));
		stats.setNumOfShotsFired(Integer.parseInt(data[1]));
		stats.setNumOfTargetsMade(Integer.parseInt(data[2]));
		stats.setNumOfTargetsShot(Integer.parseInt(data[3]));

		newGame(event);

		clearTarget();
		target = targetGame.setNewTarget(Integer.parseInt(data[4]), Integer.parseInt(data[5]));

		double width = targetCanvas.getWidth();
		double height = targetCanvas.getHeight();
		double cellWidth = width / targetGame.getGameBoard().getWidth();
		double cellHeight = height / targetGame.getGameBoard().getHeight();
		double xPos = cellWidth * targetGame.getTarget().getX();
		double yPos = cellHeight * targetGame.getTarget().getY();
		GraphicsContext gc = targetCanvas.getGraphicsContext2D();
		gc.setFill(targetGame.getTarget().getColor());
		gc.fillRect(xPos, yPos, cellWidth, cellHeight);

		targetGame.getGameStatistics().setNumOfTargetsMade(targetGame.getGameStatistics().getNumOfTargetsMade() - 1);
		stats.updateAccuracy();
		gameStatTableView.setItems(targetGame.getGameStatistics().toObservableList());
	}

	@FXML
	void saveTheGame(ActionEvent event) throws IOException {
		GameStatistics stats = targetGame.getGameStatistics();

		String data = stats.getNumberOfGuesses() + " " + stats.getNumOfShotsFired() + " " + stats.getNumOfTargetsMade() + " " + stats.getNumOfTargetsShot() + " " + target.getX() + " " + target.getY();

		Files.write(Paths.get(System.getenv("APPDATA"), "targetgame-save.txt"), data.getBytes());
	}

	private void exitGame(Event event) {
		LOGGER.debug("calling exitGame(Event event).");
		if (targetGame.isGameStateChanged()) {
			UserDecision decision = NotificationHelper.askUserDecision(new DecisionWrapper(UserDecision.CancelPendingAction));
			switch (decision) {
				case CancelPendingAction:
					event.consume();
					break;
				case DiscardGame:
					statusBroadCaster.close();
					Platform.exit();
					break;
				case SaveGame:
					try {
						targetGame.saveTheGame();
						LOGGER.debug(String.format("Saved the game at %s.", targetGame.getTheGameFile().getPath()));
						statusBroadCaster.close();
						Platform.exit();
					} catch (FileNotFoundException e) {
						LOGGER.error(String.format("Cannot found the file %s while saving the game.",
								targetGame.getTheGameFile().getPath()), e);
						NotificationHelper.showFileNotFound(targetGame.getTheGameFile().getPath());
					} catch (IOException e) {
						LOGGER.error(String.format("Cannot write to the file %s while saving the game.",
								targetGame.getTheGameFile().getPath()), e);
						NotificationHelper.showWritingError(targetGame.getTheGameFile().getPath());
					}
					break;
				default:
					throw new IllegalArgumentException(String.format("User decision's value (%s) is unexpected", decision));
			}
		} else {
			statusBroadCaster.close();
			Platform.exit();
		}
	}

	private void addTarget(TargetGame game, Canvas canvas) {
		target = game.setNewTarget();
		double width = canvas.getWidth();
		double height = canvas.getHeight();
		double cellWidth = width / game.getGameBoard().getWidth();
		double cellHeight = height / game.getGameBoard().getHeight();
		double xPos = cellWidth * game.getTarget().getX();
		double yPos = cellHeight * game.getTarget().getY();
		GraphicsContext gc = targetCanvas.getGraphicsContext2D();
		gc.setFill(game.getTarget().getColor());
		gc.fillRect(xPos, yPos, cellWidth, cellHeight);

		targetGame.getGameStatistics().setNumOfTargetsMade(targetGame.getGameStatistics().getNumOfTargetsMade() + 1);
		gameStatTableView.setItems(targetGame.getGameStatistics().toObservableList());
	}

	private void processShotAction(TargetGame gameState, Shot shot) {
		if (gameState.getTarget().isTargetShot(shot)) {
			Alert alert = new Alert(AlertType.INFORMATION
					, I18n.getBundle().getString("uShotTarget"), ButtonType.CLOSE);
			alert.setTitle(APP_TITLE + ":" + I18n.getBundle().getString("targetShot"));
			alert.setHeaderText(I18n.getBundle().getString("greatShot"));
			alert.showAndWait();
			clearTarget();
			addTarget(gameState, targetCanvas);

			targetGame.getGameStatistics().setNumOfTargetsShot(targetGame.getGameStatistics().getNumOfTargetsShot() + 1);
		} else {
			Alert alert = new Alert(AlertType.INFORMATION
					, I18n.getBundle().getString("uMissedTarget"), ButtonType.CLOSE);
			alert.setTitle(APP_TITLE + ":" + I18n.getBundle().getString("targetMissed"));
			alert.setHeaderText(I18n.getBundle().getString("lousyShooter"));
			alert.showAndWait();

			targetGame.getGameStatistics().setNumberOfGuesses(targetGame.getGameStatistics().getNumberOfGuesses() + 1);

			if (targetGame.getGameStatistics().getNumberOfGuesses() == 5) {
				targetGame.getGameStatistics().setNumberOfGuesses(0);

				Alert alert1 = new Alert(AlertType.INFORMATION
						, I18n.getBundle().getString("uMissedTarget"), ButtonType.CLOSE);
				alert1.setTitle(APP_TITLE + ":" + I18n.getBundle().getString("targetMissed"));
				alert1.setHeaderText(I18n.getBundle().getString("gameOver"));
				alert1.showAndWait();

				gameStatTableView.setItems(targetGame.getGameStatistics().toObservableList());

				clearTarget();
				addTarget(gameState, targetCanvas);
			}
		}

		targetGame.getGameStatistics().setNumOfShotsFired(targetGame.getGameStatistics().getNumOfShotsFired() + 1);
		targetGame.getGameStatistics().updateAccuracy();
		gameStatTableView.setItems(targetGame.getGameStatistics().toObservableList());
	}

	private void clearTarget() {
		double width = targetCanvas.getWidth();
		double height = targetCanvas.getHeight();
		double cellWidth = width / targetGame.getGameBoard().getWidth();
		double cellHeight = height / targetGame.getGameBoard().getHeight();
		double xPos = cellWidth * targetGame.getTarget().getX();
		double yPos = cellHeight * targetGame.getTarget().getY();

		GraphicsContext gc = targetCanvas.getGraphicsContext2D();
		gc.clearRect(xPos, yPos, cellWidth, cellHeight);

	}

	private void setWeaponDisable(boolean disabled) {
		xAimedAtTextField.setDisable(disabled);
		yAimedAtTextField.setDisable(disabled);
		fireWeaponButton.setDisable(disabled);
	}

	private void initializeI18n() throws IOException, URISyntaxException {
		List<Locale> lcList = I18n.getSupportedLocale();
		lcComboBox.getItems().addAll(lcList);
		Callback<ListView<Locale>, ListCell<Locale>> lcCellFactory =
				new Callback<ListView<Locale>, ListCell<Locale>>() {

					@Override
					public ListCell<Locale> call(ListView<Locale> lv) {
						return new ListCell<Locale>() {
							@Override
							protected void updateItem(Locale lc, boolean empty) {
								super.updateItem(lc, empty);
								if (lc == null || empty) {
									setText("");
								} else {
									setText(I18n.getDisplayLC(lc));
								}
							}
						};
					}
				};
		lcComboBox.setValue(I18n.getSelectedLocale());
		lcComboBox.setConverter(new StringConverter<Locale>() {

			@Override
			public Locale fromString(String arg0) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String toString(Locale lc) {
				return I18n.getDisplayLC(lc);
			}
		});
		lcComboBox.setCellFactory(lcCellFactory);
		lcComboBox.valueProperty().addListener(
				(observedLocale, oldLocale, newLocale) -> {
					LOGGER.debug(String.format("Change locale from %s to %s.", oldLocale, newLocale));
					try {
						LOGGER.debug("TODO: change language results to a new game. Need to handle it better.");
						reLoadScene(stage, newLocale);
					} catch (IOException e) {
						LOGGER.error("failed to load locale specific scene.", e);
					}
				});
	}


	private void reLoadScene(Stage stage, Locale locale) throws IOException {
		I18n.setSelectedLocale(locale);
		I18n.setBundle(ResourceBundle.getBundle(I18n.getBundleBaseName(), locale));
		FXMLLoader loader = new FXMLLoader(TargetGameApp.class.getResource(TargetGameApp.FXML_MAIN_SCENE)
				, I18n.getBundle());
		Parent pane = loader.load();

		StackPane viewHolder = (StackPane) stage.getScene().getRoot();

		viewHolder.getChildren().clear();
		viewHolder.getChildren().add(pane);

		GameController controller = loader.getController();
		controller.setStage(stage);
		stage.setTitle(I18n.getBundle().getString(TargetGameApp.APP_TITLE_KEY));

		LOGGER.debug(targetGame.getTarget() == null ? "No target set yet." : targetGame.getTarget().toString());
	}
}
