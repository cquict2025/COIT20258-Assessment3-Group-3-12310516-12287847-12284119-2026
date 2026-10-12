package au.edu.cqu.drs.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * while listeners are rarely added. The latest lines are kept so a window
 * opened after start-up can show what has already happened.</p>
 */
public class ServerLog {

    /** Number of recent lines kept. */
    public static final int HISTORY_SIZE = 500;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<Consumer<String>> listeners;
    private final Deque<String> history;

    /**
     * Creates a log with no listeners.
     */
    public ServerLog() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.history = new ArrayDeque<>();
    }

    /**
     * Returns the most recent lines, oldest first.
     *
     * @return a copy of up to {@link #HISTORY_SIZE} lines
     */
    public List<String> recentLines() {
        synchronized (this.history) {
            return new ArrayList<>(this.history);
        }
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
        synchronized (this.history) {
            this.history.addLast(line);
            if (this.history.size() > HISTORY_SIZE) {
                this.history.removeFirst();
            }
        }
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
