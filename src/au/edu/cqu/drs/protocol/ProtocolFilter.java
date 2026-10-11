package au.edu.cqu.drs.protocol;

import java.io.ObjectInputFilter;
import java.util.Collection;
import java.util.Map;

/**
 * Allow-list applied to every object stream the client or server reads.
 *
 * <p>Java deserialisation can rebuild any class on the classpath, which lets
 * a crafted message run code on the receiver. This filter allows only the
 * classes the protocol needs and rejects everything else before it is
 * created:</p>
 * <ul>
 *   <li>classes in {@code au.edu.cqu.drs} and its sub-packages;</li>
 *   <li>classes directly in {@code java.lang} and {@code java.time};</li>
 *   <li>collections and maps from {@code java.util}, with the helper classes
 *       they serialise through;</li>
 *   <li>arrays of primitives or of any allowed class.</li>
 * </ul>
 * <p>It also limits the depth of the object graph, the number of objects
 * and the length of any one array, so a message cannot exhaust memory.</p>
 */
public class ProtocolFilter implements ObjectInputFilter {

    /** Deepest object nesting accepted. */
    public static final long MAX_DEPTH = 20;

    /** Most objects accepted in one message. */
    public static final long MAX_REFERENCES = 10_000;

    /** Longest array accepted: 1 MB of bytes. */
    public static final long MAX_ARRAY_LENGTH = 1024 * 1024;

    /** Package prefix of the application's own classes. */
    private static final String APPLICATION_PACKAGE = "au.edu.cqu.drs.";

    /**
     * Allows or rejects one step of deserialisation.
     *
     * @param info the class, depth, reference count and array length
     * @return {@link Status#ALLOWED} or {@link Status#REJECTED}
     */
    @Override
    public Status checkInput(FilterInfo info) {
        if (info.depth() > MAX_DEPTH
                || info.references() > MAX_REFERENCES
                || info.arrayLength() > MAX_ARRAY_LENGTH) {
            return Status.REJECTED;
        }
        Class<?> type = info.serialClass();
        if (type == null) {
            return Status.ALLOWED;
        }
        if (isAllowed(type)) {
            return Status.ALLOWED;
        }
        return Status.REJECTED;
    }

    /**
     * Tells whether a class is on the allow-list.
     *
     * @param type the class about to be deserialised
     * @return {@code true} if the class may be rebuilt
     */
    public boolean isAllowed(Class<?> type) {
        Class<?> element = type;
        while (element.isArray()) {
            element = element.getComponentType();
        }
        if (element.isPrimitive()) {
            return true;
        }
        String name = element.getName();
        String packageName = element.getPackageName();
        if (name.startsWith(APPLICATION_PACKAGE)) {
            return true;
        }
        if (packageName.equals("java.lang")
                || packageName.equals("java.time")) {
            return true;
        }
        return packageName.equals("java.util") && isCollectionType(element);
    }

    /**
     * Tells whether a {@code java.util} class is a collection, a map, a map
     * entry, or the proxy that {@code List.of} and {@code Map.of} serialise
     * through.
     *
     * @param type a class in {@code java.util}
     * @return {@code true} for the collection classes
     */
    private boolean isCollectionType(Class<?> type) {
        return Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Map.Entry.class.isAssignableFrom(type)
                || type.getName().equals("java.util.CollSer");
    }

    /**
     * Describes the filter and its limits.
     *
     * @return the allowed packages and the limits
     */
    @Override
    public String toString() {
        return String.format("ProtocolFilter: au.edu.cqu.drs.**, java.lang.*, "
                + "java.time.*, java.util collections, arrays; depth <= %d, "
                + "references <= %d, array length <= %d",
                MAX_DEPTH, MAX_REFERENCES, MAX_ARRAY_LENGTH);
    }
}
