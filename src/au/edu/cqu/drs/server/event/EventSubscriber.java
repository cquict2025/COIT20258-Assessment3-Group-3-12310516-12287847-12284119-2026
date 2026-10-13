package au.edu.cqu.drs.server.event;

/**
 * Receives the events published on an {@link EventBus} (the observer in the
 * Observer pattern).
 *
 * <p>The bus calls {@link #onEvent} on the publisher's thread, so an
 * implementation must return quickly and never block: one that talks to a
 * client should only queue the event, as {@link QueuedSubscriber} does.</p>
 */
@FunctionalInterface
public interface EventSubscriber {

    /**
     * Receives one event.
     *
     * @param event the event
     */
    void onEvent(ServerEvent event);
}
