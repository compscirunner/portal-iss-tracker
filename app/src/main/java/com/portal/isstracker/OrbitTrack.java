package com.portal.isstracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Approximate ISS ground track. Without a live TLE we fit a simple circular-orbit
 * model to the current sub-satellite point: latitude swings ±inclination as a sine
 * of the argument-of-latitude, and longitude advances at the orbital rate minus
 * Earth's rotation. Good enough to draw the characteristic ground-track sine wave
 * a few passes either side of "now".
 */
final class OrbitTrack {

    private static final double INC_DEG    = 51.6;          // ISS orbital inclination
    private static final double PERIOD_MIN = 92.9;          // orbital period
    private static final double N_DEG_MIN  = 360.0 / PERIOD_MIN;        // orbital rate
    private static final double WE_DEG_MIN = 360.0 / 1436.07;          // Earth sidereal rate

    /**
     * Ground-track points from {@code fromMin}..{@code toMin} relative to now.
     * @param northbound whether the ISS is currently moving north (sets the ascending vs descending branch)
     * @return list of {lat, lon} in degrees, lon normalized to [-180, 180]
     */
    static List<double[]> predict(double lat0, double lon0, boolean northbound,
                                  double fromMin, double toMin, double stepMin) {
        double iRad = Math.toRadians(INC_DEG);
        double sinI = Math.sin(iRad), cosI = Math.cos(iRad);

        double s = Math.max(-1.0, Math.min(1.0, Math.sin(Math.toRadians(lat0)) / sinI));
        double u0 = Math.toDegrees(Math.asin(s));           // [-90, 90]
        if (!northbound) u0 = 180.0 - u0;                   // descending branch
        double dlam0 = Math.toDegrees(Math.atan2(cosI * Math.sin(Math.toRadians(u0)),
                                                 Math.cos(Math.toRadians(u0))));

        List<double[]> out = new ArrayList<>();
        for (double tau = fromMin; tau <= toMin + 1e-9; tau += stepMin) {
            double uRad = Math.toRadians(u0 + N_DEG_MIN * tau);
            double lat = Math.toDegrees(Math.asin(sinI * Math.sin(uRad)));
            double dlam = Math.toDegrees(Math.atan2(cosI * Math.sin(uRad), Math.cos(uRad)));
            double lon = lon0 + (dlam - dlam0) - WE_DEG_MIN * tau;
            lon = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;   // wrap to [-180,180]
            out.add(new double[]{lat, lon});
        }
        return out;
    }

    private OrbitTrack() {}
}
