package au.edu.cqu.drs.model.warning;

import java.io.Serializable;

/**
 * A place people can go when a warning tells them to leave, with how many
 * people it can take.
 */
public class EvacuationCentre implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private String name;
    private int capacity;

    /**
     * Creates an empty centre, for forms and the repositories.
     */
    public EvacuationCentre() {
    }

    /**
     * Creates a centre.
     *
     * @param name     where it is, for example Laverton Community Hall
     * @param capacity how many people it can take
     */
    public EvacuationCentre(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    /**
     * Returns the centre's name.
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the centre's name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns how many people the centre can take.
     *
     * @return the capacity
     */
    public int getCapacity() {
        return this.capacity;
    }

    /**
     * Sets how many people the centre can take.
     *
     * @param capacity the capacity
     */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Describes the centre.
     *
     * @return the name and capacity
     */
    @Override
    public String toString() {
        return this.name + " (capacity " + this.capacity + ")";
    }
}
