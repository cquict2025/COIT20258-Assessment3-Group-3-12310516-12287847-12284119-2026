package au.edu.cqu.drs.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * A small window showing the running server: its port and storage, how many
 * clients are connected, and a scrolling log. Closing the window stops the
 * server.
 */
public class ServerStatusWindow extends Application {

    /** Lines kept in the log view. */
    private static final int MAX_LINES = ServerLog.HISTORY_SIZE;

    private static ServerBootstrap bootstrap;

    private TextArea logView;
    private Label clientsLabel;

    /**
     * Opens the window for a started server and blocks until it closes.
     *
     * @param running the started server
     */
    public static void open(ServerBootstrap running) {
        bootstrap = running;
        Application.launch(ServerStatusWindow.class);
    }

    /**
     * Builds and shows the window.
     *
     * @param stage the primary stage
     */
    @Override
    public void start(Stage stage) {
        DrsServer server = bootstrap.getServer();
        Label portLabel = new Label("Port " + server.getPort() + "  |  "
                + bootstrap.getConfig().getBackend() + " storage  |  "
                + bootstrap.getConfig().getThreads() + " worker threads");
        this.clientsLabel = new Label();
        showClients(server.getConnectedClients());
        Button stopButton = new Button("Stop server");
        stopButton.setOnAction(event -> stage.close());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(16, portLabel, gap, this.clientsLabel,
                stopButton);
        header.setPadding(new Insets(10));
        this.logView = new TextArea();
        this.logView.setEditable(false);
        this.logView.setStyle("-fx-font-family: monospace;");
        for (String line : bootstrap.getLog().recentLines()) {
            appendLine(line);
        }
        BorderPane root = new BorderPane(this.logView);
        root.setTop(header);
        bootstrap.getLog().addListener(
                line -> Platform.runLater(() -> appendLine(line)));
        server.addClientCountListener(
                count -> Platform.runLater(() -> showClients(count)));
        stage.setTitle("DRS Server");
        stage.setScene(new Scene(root, 860, 520));
        stage.show();
    }

    /**
     * Stops the server when the window closes.
     */
    @Override
    public void stop() {
        bootstrap.close();
    }

    /**
     * Adds a line to the log view, dropping the oldest past the limit.
     *
     * @param line the log line
     */
    private void appendLine(String line) {
        this.logView.appendText(line + System.lineSeparator());
        int extra = this.logView.getParagraphs().size() - MAX_LINES;
        if (extra > 0) {
            int end = 0;
            String text = this.logView.getText();
            for (int i = 0; i < extra; i++) {
                end = text.indexOf('\n', end) + 1;
            }
            this.logView.deleteText(0, end);
        }
    }

    /**
     * Shows the connected-client count.
     *
     * @param count the number of clients
     */
    private void showClients(int count) {
        this.clientsLabel.setText("Connected clients: " + count);
    }

    /**
     * Describes the window.
     *
     * @return the server shown
     */
    @Override
    public String toString() {
        return "ServerStatusWindow for " + bootstrap;
    }
}
