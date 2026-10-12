package au.edu.cqu.drs.client;

import au.edu.cqu.drs.service.ErrorCode;
import au.edu.cqu.drs.service.ServiceResult;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a call to the server in a JavaFX {@link Task}, so the window stays
 * responsive, and shows that the screen is busy until the answer arrives.
 *
 * <p>While the call runs, the screen is disabled and the cursor shows the
 * wait symbol. The result is handed back on the JavaFX thread, where the
 * controller can update its controls. An unexpected fault in the call comes
 * back as a failed {@link ServiceResult}, so controllers handle every
 * outcome in one place.</p>
 */
public final class BackgroundCall {

    private static final Logger LOGGER =
            Logger.getLogger(BackgroundCall.class.getName());

    private static final ExecutorService WORKERS =
            Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, "drs-client-call");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Prevents instances; every method is static.
     */
    private BackgroundCall() {
    }

    /**
     * Runs a call in the background.
     *
     * @param screen the screen to mark busy, may be null
     * @param call   the call to the server
     * @param onDone receives the result on the JavaFX thread
     * @param <T>    the value type
     */
    public static <T> void run(Node screen,
                               Supplier<ServiceResult<T>> call,
                               Consumer<ServiceResult<T>> onDone) {
        Task<ServiceResult<T>> task = new Task<>() {
            @Override
            protected ServiceResult<T> call() {
                return call.get();
            }
        };
        setBusy(screen, true);
        task.setOnSucceeded(event -> {
            setBusy(screen, false);
            onDone.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(screen, false);
            LOGGER.log(Level.WARNING, "Server call failed",
                    task.getException());
            onDone.accept(ServiceResult.failure(
                    ErrorCode.ADAPTER_UNAVAILABLE,
                    "Something went wrong talking to the server."));
        });
        WORKERS.execute(task);
    }

    /**
     * Marks a screen busy or ready.
     *
     * @param screen the screen, may be null
     * @param busy   {@code true} while a call runs
     */
    private static void setBusy(Node screen, boolean busy) {
        if (screen == null) {
            return;
        }
        screen.setDisable(busy);
        Scene scene = screen.getScene();
        if (scene != null) {
            if (busy) {
                scene.setCursor(Cursor.WAIT);
            } else {
                scene.setCursor(Cursor.DEFAULT);
            }
        }
    }

    /**
     * Describes this helper class.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "BackgroundCall: runs server calls off the JavaFX thread";
    }
}
