package au.edu.cqu.drs.model;

import java.io.Serializable;

/**
 * Where a disaster was observed. Latitude and longitude are held as well as the
 * street address so that duplicate detection can measure real distance between
 * two reports rather than comparing address text (creative feature 2).
 */
public class Location implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private static final double EARTH_RADIUS_KM = 6371.0;

    private String addressLine;
    private String suburb;
    private double latitude;
    private double longitude;

    /**
     * Creates an empty location.
     */
    public Location() {
        this("", "", 0.0, 0.0);
    }

    /**
     * Creates a location.
     *
     * @param addressLine the address line
     * @param suburb      the suburb
     * @param latitude    the latitude
     * @param longitude   the longitude
     */
    public Location(String addressLine, String suburb, double latitude,
                    double longitude) {
        this.addressLine = addressLine;
        this.suburb = suburb;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Returns the address line.
     *
     * @return the address line
     */
    public String getAddressLine() {
        return this.addressLine;
    }

    /**
     * Sets the address line.
     *
     * @param addressLine the address line
     */
    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    /**
     * Returns the suburb.
     *
     * @return the suburb
     */
    public String getSuburb() {
        return this.suburb;
    }

    /**
     * Sets the suburb.
     *
     * @param suburb the suburb
     */
    public void setSuburb(String suburb) {
        this.suburb = suburb;
    }

    /**
     * Returns the latitude.
     *
     * @return the latitude
     */
    public double getLatitude() {
        return this.latitude;
    }

    /**
     * Sets the latitude.
     *
     * @param latitude the latitude
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * Returns the longitude.
     *
     * @return the longitude
     */
    public double getLongitude() {
        return this.longitude;
    }

    /**
     * Sets the longitude.
     *
     * @param longitude the longitude
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * Great-circle distance to another location using the haversine formula.
     *
     * @param other the location to measure to
     * @return distance in kilometres, or Double.MAX_VALUE when other is null
     */
    public double distanceKmTo(Location other) {
        if (other == null) {
            return Double.MAX_VALUE;
        }
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.pow(Math.sin(deltaLon / 2),
                        2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Describes the location as its address, suburb and coordinates.
     *
     * @return a one-line summary of this location
     */
    @Override
    public String toString() {
        return String.format("%s, %s (%.4f, %.4f)", this.addressLine,
                this.suburb,
                             this.latitude, this.longitude);
    }
}
