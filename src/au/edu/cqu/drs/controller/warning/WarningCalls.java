package au.edu.cqu.drs.controller.warning;

import au.edu.cqu.drs.client.ModuleContext;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;
import au.edu.cqu.drs.service.ServiceResult;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The calls the warning screens make to the server, and small display
 * helpers they share. Every method that talks to the server blocks, so the
 * screens call them through {@code BackgroundCall}, never on the JavaFX
 * thread.
 */
public final class WarningCalls {

    /** How times are shown on the warning screens. */
    public static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

    /** A shorter form for narrow columns and lists. */
    public static final DateTimeFormatter SHORT_TIME =
            DateTimeFormatter.ofPattern("d MMM h:mm a", Locale.ENGLISH);

    private final ModuleContext context;

    /**
     * Creates the calls for a screen.
     *
     * @param context the client's connection and session
     */
    public WarningCalls(ModuleContext context) {
        this.context = context;
    }

    /**
     * Sends a warning command with up to two arguments.
     *
     * @param command the command
     * @param type    the class of the value expected back
     * @param pairs   argument name and value, alternating
     * @param <T>     the value type
     * @return the result
     */
    public <T> ServiceResult<T> send(Command command, Class<T> type,
            Object... pairs) {
        Request request = this.context.newRequest(command);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            request.put((String) pairs[i], (Serializable) pairs[i + 1]);
        }
        Response response = this.context.send(request);
        try {
            return response.toServiceResult(type);
        } catch (IllegalArgumentException e) {
            return ServiceResult.failure(ErrorCode.ADAPTER_UNAVAILABLE,
                    "The server sent an unexpected answer.");
        }
    }

    /**
     * Sends a command whose answer is a list of warnings.
     *
     * @param command the command
     * @param pairs   argument name and value, alternating
     * @return the warnings, or the failure
     */
    public ServiceResult<List<Warning>> warnings(Command command,
            Object... pairs) {
        ServiceResult<ArrayList> result = send(command, ArrayList.class,
                pairs);
        if (result.isFailure()) {
            return ServiceResult.failure(result.getErrorCode(),
                    result.getMessage(), result.getAffectedField());
        }
        List<Warning> warnings = new ArrayList<>();
        for (Object item : result.getValue()) {
            warnings.add((Warning) item);
        }
        return ServiceResult.success(warnings);
    }

    /**
     * Returns the style class for a level, for example level-watch-and-act.
     *
     * @param level the level
     * @return the style class
     */
    public static String levelStyle(WarningLevel level) {
        return "level-" + level.name().toLowerCase(Locale.ROOT)
                .replace('_', '-');
    }

    /**
     * Describes the calls.
     *
     * @return who they are made as
     */
    @Override
    public String toString() {
        return "WarningCalls for " + (this.context.getCurrentUser() == null
                ? "the public" : this.context.getCurrentUser().getUsername());
    }
}
