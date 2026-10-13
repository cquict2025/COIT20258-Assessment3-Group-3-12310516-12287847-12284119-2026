package au.edu.cqu.drs.server.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publishes {@link ServerEvent}s to every {@link EventSubscriber} (the
 * subject in the Observer pattern).
 *
 * <p>Subscribers are held in a {@link CopyOnWriteArrayList}. Events are
 * published far more often than clients join or leave, and this list lets
 * many worker threads publish at once without locking: each publish walks
 * a snapshot of the list, and only a subscribe or unsubscribe pays for a
 * copy. A subscriber that throws is logged and skipped, so one faulty
 * subscriber never stops the others from receiving the event.</p>
 */
public class EventBus {

    private static final Logger LOGGER =
            Logger.getLogger(EventBus.class.getName());

    private final List<EventSubscriber> subscribers;
    private final List<IntConsumer> countListeners;

    /**
     * Creates a bus with no subscribers.
     */
    public EventBus() {
        this.subscribers = new CopyOnWriteArrayList<>();
        this.countListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Adds a subscriber.
     *
     * @param subscriber the subscriber
     */
    public void subscribe(EventSubscriber subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("The subscriber is null.");
        }
        this.subscribers.add(subscriber);
        countChanged();
    }

    /**
     * Removes a subscriber; does nothing if it is not subscribed.
     *
     * @param subscriber the subscriber
     */
    public void unsubscribe(EventSubscriber subscriber) {
        if (this.subscribers.remove(subscriber)) {
            countChanged();
        }
    }

    /**
     * Sends an event to every subscriber.
     *
     * @param event the event
     */
    public void publish(ServerEvent event) {
        if (event == null) {
            return;
        }
        for (EventSubscriber subscriber : this.subscribers) {
            try {
                subscriber.onEvent(event);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "A subscriber failed to take "
                        + "an event", e);
            }
        }
    }

    /**
     * Returns the number of subscribers.
     *
     * @return the count
     */
    public int getSubscriberCount() {
        return this.subscribers.size();
    }

    /**
     * Adds a listener told the new count whenever a subscriber joins or
     * leaves.
     *
     * @param listener the listener
     */
    public void addSubscriberCountListener(IntConsumer listener) {
        this.countListeners.add(listener);
    }

    /**
     * Tells the count listeners the number of subscribers.
     */
    private void countChanged() {
        int count = this.subscribers.size();
        for (IntConsumer listener : this.countListeners) {
            listener.accept(count);
        }
    }

    /**
     * Describes the bus.
     *
     * @return the number of subscribers
     */
    @Override
    public String toString() {
        return "EventBus: " + this.subscribers.size() + " subscriber(s)";
    }
}
