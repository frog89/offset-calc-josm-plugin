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

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class GeometryRasterizer {

    private GeometryRasterizer() {
    }

    public static BufferedImage rasterize(
            OsmDataLayer dataLayer,
            MapView mapView,
            double offsetX,
            double offsetY) {

        int width =
                mapView.getWidth();

        int height =
                mapView.getHeight();

        BufferedImage image =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_BYTE_GRAY
                );

        Graphics2D g =
                image.createGraphics();

        final int[] wayCount = {0};
        final int[] closedWayCount = {0};

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF
        );

        g.setColor(Color.BLACK);

        g.fillRect(
                0,
                0,
                width,
                height
        );

        g.setColor(Color.WHITE);

        /*
         * 2 pixels is deliberately slightly thicker than
         * a one-pixel line. This makes the distance function
         * less sensitive to sub-pixel rounding.
         */
        g.setStroke(
                new BasicStroke(
                        2.0f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                )
        );

        dataLayer.getDataSet()
                .allNonDeletedCompletePrimitives()
                .forEach(
                        primitive -> {

                            if (!(primitive instanceof Way)) {
                                return;
                            }

                            Way way = (Way) primitive;

                            wayCount[0]++;
                            if (way.isClosed()) {
                                closedWayCount[0]++;
                            }

                            if (FilterUtil.filterOutWay(way)) {
                                return;
                            }

                            for (
                                    int i = 0;
                                    i < way.getNodesCount() - 1;
                                    i++
                            ) {

                                Node a =
                                        way.getNode(i);

                                Node b =
                                        way.getNode(i + 1);

                                if (a.isIncomplete()
                                        || b.isIncomplete()) {
                                    continue;
                                }

                                java.awt.geom.Point2D pa =
                                        mapView.getPoint2D(
                                                a.getEastNorth()
                                        );

                                java.awt.geom.Point2D pb =
                                        mapView.getPoint2D(
                                                b.getEastNorth()
                                        );

                                java.awt.geom.Point2D shiftedPa =
                                        new java.awt.geom.Point2D.Double(
                                                pa.getX() + offsetX,
                                                pa.getY() + offsetY
                                        );

                                java.awt.geom.Point2D shiftedPb =
                                        new java.awt.geom.Point2D.Double(
                                                pb.getX() + offsetX,
                                                pb.getY() + offsetY
                                        );

                                if (isOutside(
                                        shiftedPa,
                                        width,
                                        height
                                ) && isOutside(
                                        shiftedPb,
                                        width,
                                        height
                                )) {
                                    continue;
                                }

                                g.drawLine(
                                        (int) Math.round(shiftedPa.getX()),
                                        (int) Math.round(shiftedPa.getY()),
                                        (int) Math.round(shiftedPb.getX()),
                                        (int) Math.round(shiftedPb.getY())
                                );                            }
                        }
                );

        System.out.println(
                "OffsetCalc: ways="
                        + wayCount[0]
                        + ", closed="
                        + closedWayCount[0]
        );

        g.dispose();

        return image;
    }

    private static boolean isOutside(
            java.awt.geom.Point2D p,
            int width,
            int height) {

        return p.getX() < 0
                || p.getX() >= width
                || p.getY() < 0
                || p.getY() >= height;
    }
}