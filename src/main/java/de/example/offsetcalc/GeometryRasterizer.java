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

import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class GeometryRasterizer {

    private GeometryRasterizer() {
    }

    public static BufferedImage rasterize(
            OsmDataLayer dataLayer,
            MapView mapView,
            int width,
            int height,
            double scale,
            boolean selectedBuildingsOnly) {

        BufferedImage image =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_INT_ARGB
                );

        Graphics2D graphics =
                image.createGraphics();

        try {

            graphics.setComposite(
                    java.awt.AlphaComposite.Clear
            );

            graphics.fillRect(
                    0,
                    0,
                    width,
                    height
            );

            graphics.setComposite(
                    java.awt.AlphaComposite.SrcOver
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            graphics.setColor(
                    java.awt.Color.WHITE
            );

            /*
             * Die Strichbreite bezieht sich auf das fertige
             * Analysebild.
             */
            graphics.setStroke(
                    new BasicStroke(
                            2.0f,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    )
            );

            Iterable<Way> ways;

            if (selectedBuildingsOnly) {

                ways =
                        dataLayer.getDataSet()
                                .getSelectedWays();

            } else {

                ways =
                        dataLayer.getDataSet()
                                .getWays();
            }

            for (Way way : ways) {

                if (FilterUtil.filterOutWay(way)) {
                    continue;
                }

                List<Node> nodes =
                        way.getNodes();

                if (nodes.size() < 2) {
                    continue;
                }

                Path2D path =
                        new Path2D.Double();

                boolean firstPoint = true;

                for (Node node : nodes) {

                    if (node == null
                            || node.isDeleted()
                            || node.isIncomplete()) {

                        continue;
                    }

                    java.awt.geom.Point2D point =
                            mapView.getPoint2D(
                                    node.getEastNorth()
                            );

                    double x =
                            point.getX() * scale;

                    double y =
                            point.getY() * scale;

                    if (firstPoint) {

                        path.moveTo(
                                x,
                                y
                        );

                        firstPoint = false;

                    } else {

                        path.lineTo(
                                x,
                                y
                        );
                    }
                }

                if (firstPoint) {
                    continue;
                }

                if (way.isClosed()) {
                    path.closePath();
                }

                graphics.draw(path);
            }

        } finally {
            graphics.dispose();
        }

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