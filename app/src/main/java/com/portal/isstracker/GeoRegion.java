package com.portal.isstracker;

/**
 * Offline, key-less "what is the ISS over?" classifier. Coarse bounding boxes —
 * good enough to label a continent or ocean on the HUD without any geocoding API.
 * Land masses are checked before oceans so a point inside a continent's box wins.
 */
final class GeoRegion {

    private static final class Box {
        final String name;
        final double latMin, latMax, lonMin, lonMax;
        Box(String name, double latMin, double latMax, double lonMin, double lonMax) {
            this.name = name; this.latMin = latMin; this.latMax = latMax;
            this.lonMin = lonMin; this.lonMax = lonMax;
        }
        boolean has(double lat, double lon) {
            return lat >= latMin && lat <= latMax && lon >= lonMin && lon <= lonMax;
        }
    }

    // Order matters: first match wins, land before water.
    private static final Box[] LAND = {
            new Box("Antarctica",     -90, -60, -180, 180),
            new Box("Greenland",       60,  84,  -73, -11),
            new Box("North America",   15,  72, -168, -52),
            new Box("Central America",  7,  22, -106, -77),
            new Box("South America",  -56,  13,  -82, -34),
            new Box("Europe",          36,  71,  -10,  40),
            new Box("Africa",         -35,  37,  -18,  52),
            new Box("Middle East",     12,  42,   34,  63),
            new Box("Russia/N. Asia",  50,  78,   40, 180),
            new Box("Central Asia",    35,  55,   46,  90),
            new Box("South Asia",       5,  37,   60,  97),
            new Box("East Asia",       18,  53,   97, 146),
            new Box("SE Asia",        -11,  29,   92, 142),
            new Box("Australia",      -44, -10,  112, 154),
            new Box("New Zealand",    -48, -34,  166, 179),
    };

    private static final Box[] OCEAN = {
            new Box("Southern Ocean", -90, -55, -180, 180),
            new Box("Arctic Ocean",    72,  90, -180, 180),
            new Box("N. Atlantic",      0,  72,  -82,  -5),
            new Box("S. Atlantic",    -60,   0,  -70,  20),
            new Box("Indian Ocean",   -60,  30,   20, 120),
            new Box("N. Pacific",       0,  66,  120, 180),
            new Box("N. Pacific",       0,  66, -180, -82),
            new Box("S. Pacific",     -60,   0,  150, 180),
            new Box("S. Pacific",     -60,   0, -180, -70),
    };

    static String describe(double lat, double lon) {
        for (Box b : LAND) if (b.has(lat, lon)) return b.name;
        for (Box b : OCEAN) if (b.has(lat, lon)) return b.name;
        return "Open ocean";
    }

    private GeoRegion() {}
}
