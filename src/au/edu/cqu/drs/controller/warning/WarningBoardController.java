package au.edu.cqu.drs.controller.warning;

import au.edu.cqu.drs.client.BackgroundCall;
import au.edu.cqu.drs.client.ModuleContext;
import au.edu.cqu.drs.model.warning.EvacuationCentre;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.service.ServiceResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The public warning board (warning-board.fxml), opened from the sign-in
 * screen without an account. It shows each warning in force as a card,
 * coloured and named by its level, most urgent first.
 */
public class WarningBoardController {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final WarningCalls calls;

    @FXML
    private VBox root;

    @FXML
    private VBox cardBox;

    @FXML
    private Label updatedLabel;

    @FXML
    private Label messageLabel;

    /**
     * Creates the controller.
     *
     * @param context the client's connection; nobody is signed in
     */
    public WarningBoardController(ModuleContext context) {
        this.calls = new WarningCalls(context);
    }

    /**
     * Loads the warnings when the screen opens.
     */
    @FXML
    private void initialize() {
        this.messageLabel.managedProperty().bind(
                this.messageLabel.textProperty().isNotEmpty());
        handleRefresh();
    }

    /**
     * Fetches the warnings in force from the server.
     */
    @FXML
    private void handleRefresh() {
        this.messageLabel.setText("");
        BackgroundCall.run(this.root,
                () -> this.calls.warnings(Command.LIST_PUBLIC_WARNINGS),
                this::show);
    }

    /**
     * Describes the controller.
     *
     * @return the screen it drives
     */
    @Override
    public String toString() {
        return "WarningBoardController: warning-board.fxml";
    }

    private void show(ServiceResult<List<Warning>> result) {
        this.cardBox.getChildren().clear();
        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            return;
        }
        this.updatedLabel.setText("Updated " + LocalTime.now().format(CLOCK)
                + ". " + result.getValue().size() + " warning(s) in force.");
        if (result.getValue().isEmpty()) {
            Label empty = new Label("No warnings are in force. Check again "
                    + "if conditions change, and call 000 in a "
                    + "life-threatening emergency.");
            empty.getStyleClass().add("empty-state");
            empty.setWrapText(true);
            this.cardBox.getChildren().add(empty);
            return;
        }
        for (Warning warning : result.getValue()) {
            this.cardBox.getChildren().add(card(warning));
        }
    }

    private VBox card(Warning warning) {
        String style = WarningCalls.levelStyle(warning.getLevel());
        Label level = new Label(warning.getLevel().getDisplayName()
                .toUpperCase(Locale.ENGLISH));
        level.getStyleClass().addAll("level-badge", style);
        Label call = new Label(warning.getLevel().getCallToAction());
        call.getStyleClass().add("card-action");
        HBox heading = new HBox(10, level, call);

        Label area = new Label(warning.getAffectedArea());
        area.getStyleClass().add("card-area");
        VBox card = new VBox(heading, area, detail(warning.getMessage()));
        card.getStyleClass().addAll("warning-card",
                style.replace("level-", "card-"));
        if (warning.getRecommendedAction() != null
                && !warning.getRecommendedAction().isBlank()) {
            Label action = new Label("What to do: "
                    + warning.getRecommendedAction());
            action.getStyleClass().add("card-action");
            card.getChildren().add(action);
        }
        EvacuationCentre centre = warning.getEvacuationCentre();
        if (centre != null) {
            card.getChildren().add(detail("Evacuation centre: "
                    + centre.getName() + " (room for "
                    + centre.getCapacity() + " people)"));
        }
        card.getChildren().add(detail(String.format(
                "%s, version %d. Issued %s. Expires %s.",
                warning.getWarningId(), warning.getVersion(),
                warning.getIssuedAt().format(WarningCalls.TIME_FORMAT),
                warning.getExpiresAt().format(WarningCalls.TIME_FORMAT))));
        return card;
    }

    private static Label detail(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-detail");
        label.setWrapText(true);
        return label;
    }
}
