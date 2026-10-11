package au.edu.cqu.drs.model;

/**
 * Where a disaster was observed. Latitude and longitude are held as well as the
 * street address so that duplicate detection can measure real distance between
 * two reports rather than comparing address text (creative feature 2).
 */
public class Location {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private String addressLine;
    private String suburb;
    private double latitude;
    private double longitude;

    public Location() {
        this("", "", 0.0, 0.0);
    }

    public Location(String addressLine, String suburb, double latitude,
                    double longitude) {
        this.addressLine = addressLine;
        this.suburb = suburb;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getAddressLine() {
        return this.addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getSuburb() {
        return this.suburb;
    }

    public void setSuburb(String suburb) {
        this.suburb = suburb;
    }

    public double getLatitude() {
        return this.latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return this.longitude;
    }

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

    @Override
    public String toString() {
        return String.format("%s, %s (%.4f, %.4f)", this.addressLine,
                this.suburb,
                             this.latitude, this.longitude);
    }
}
