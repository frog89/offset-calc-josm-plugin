/*
 * Copyright (C) 2026 Frank Augustin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package de.example.offsetcalc;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.gui.MapView;

/**
 * Projection-independent conversion of screen pixels
 * into real-world ground distance in meters.
 *
 * The previous approach used the raw East/North difference
 * from MapView.getEastNorth(...). That is only correct for
 * metric projections (e.g. UTM). For Mercator (EPSG:3857),
 * the values are Mercator units, not meters.
 *
 * This helper converts through LatLon and uses the
 * great-circle distance, which is always in meters.
 */
public final class MapScaleUtil {

    private MapScaleUtil() {
    }

    /**
     * Real-world ground distance per screen pixel in east
     * direction, evaluated at the given screen position.
     */
    public static double metersPerPixelEast(
            MapView mapView,
            int screenX,
            int screenY) {

        EastNorth a =
                mapView.getEastNorth(screenX, screenY);

        EastNorth b =
                mapView.getEastNorth(screenX + 1, screenY);

        return groundDistanceMeters(mapView, a, b);
    }

    /**
     * Real-world ground distance per screen pixel in north
     * direction, evaluated at the given screen position.
     */
    public static double metersPerPixelNorth(
            MapView mapView,
            int screenX,
            int screenY) {

        EastNorth a =
                mapView.getEastNorth(screenX, screenY);

        EastNorth b =
                mapView.getEastNorth(screenX, screenY + 1);

        return groundDistanceMeters(mapView, a, b);
    }

    private static double groundDistanceMeters(
            MapView mapView,
            EastNorth a,
            EastNorth b) {

        Projection projection =
                mapView.getProjection();

        LatLon llA =
                projection.eastNorth2latlon(a);

        LatLon llB =
                projection.eastNorth2latlon(b);

        return llA.greatCircleDistance(llB);
    }
}