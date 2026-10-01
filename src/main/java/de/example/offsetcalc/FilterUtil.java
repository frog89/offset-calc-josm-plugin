package de.example.offsetcalc;

public class FilterUtil {
    public static  boolean filterOutWay(org.openstreetmap.josm.data.osm.Way way) {
        if (way.isDeleted() || way.isIncomplete()) {
            return true;
        }

        if (!way.isClosed()) {
            return true;
        }

        if (!way.hasTag("building")) {
            return true;
        }

        if (way.getNodesCount() < 2) {
            return true;
        }
        return false;
    }
}
