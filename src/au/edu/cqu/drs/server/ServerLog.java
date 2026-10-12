package au.edu.cqu.drs.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The server's activity log, passed to every listener: the console when the
 * server runs headless, the status window otherwise.
 *
 * <p>Each line carries the time and the name of the thread that wrote it,
 * which shows which worker served each client. Listeners are held in a
 * {@link CopyOnWriteArrayList} because many client threads log at once
 * while listeners are rarely added.</p>
 */
public class ServerLog {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<Consumer<String>> listeners;

    /**
     * Creates a log with no listeners.
     */
    public ServerLog() {
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Adds a listener that receives every line.
     *
     * @param listener the listener
     */
    public void addListener(Consumer<String> listener) {
        this.listeners.add(listener);
    }

    /**
     * Records an event.
     *
     * @param message what happened
     */
    public void info(String message) {
        publish(message);
    }

    /**
     * Records a failure. The stack trace stays on the server.
     *
     * @param message what failed
     * @param error   the cause
     */
    public void error(String message, Throwable error) {
        publish("ERROR " + message + ": " + error);
        error.printStackTrace();
    }

    /**
     * Stamps a line and passes it to every listener.
     *
     * @param message the text
     */
    private void publish(String message) {
        String line = LocalTime.now().format(TIME) + " ["
                + Thread.currentThread().getName() + "] " + message;
        for (Consumer<String> listener : this.listeners) {
            listener.accept(line);
        }
    }

    /**
     * Describes the log.
     *
     * @return the number of listeners
     */
    @Override
    public String toString() {
        return "ServerLog: " + this.listeners.size() + " listener(s)";
    }
}
