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

import org.opencv.core.Mat;
import org.opencv.core.Point;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.imagery.OffsetBookmark;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OffsetCalculator {
    private final OsmDataLayer dataLayer;
    private final AbstractTileSourceLayer<?> imageryLayer;
    private final OffsetCalculationConfig config;

    private final MapView mapView;

    public OffsetCalculator(
            OsmDataLayer dataLayer,
            AbstractTileSourceLayer<?> imageryLayer,
            OffsetCalculationConfig config) {

        this.dataLayer = dataLayer;
        this.imageryLayer = imageryLayer;
        this.config = config;

        this.mapView =
                MainApplication.getMap().mapView;
    }

    public OffsetResult calculate() {

        if (mapView.getWidth() <= 0
                || mapView.getHeight() <= 0) {

            return OffsetResult.invalid(
                    "The map view has no usable size."
            );
        }

        BufferedImage imagery =
                renderImagery();

        Mat imageryEdges =
                EdgeDetector.detectEdges(
                        imagery
                );

        BufferedImage geometryImage =
                renderGeometry();

        saveDebugImages(
                imagery,
                geometryImage
        );

        BuildingEdgeMatcher matcher =
                null;

        try {

            matcher =
                    new BuildingEdgeMatcher(
                            dataLayer,
                            mapView,
                            imageryEdges,
                            config.buildingSearchRadiusMeters,
                            config.testOffsetX,
                            config.testOffsetY
                    );

            if (matcher.getBuildingWithEdgesCount() == 0) {

                return OffsetResult.invalid(
                        "No buildings with nearby imagery edges were detected."
                );
            }

            if (matcher.getGeometryPointCount() < 100) {

                return OffsetResult.invalid(
                        "Too few OSM geometry pixels were detected."
                );
            }

            SearchResult best =
                    search(
                            matcher
                    );

            double eastPerPixel =
                    getEastPerPixel();

            double northPerPixel =
                    getNorthPerPixel();

            double east =
                    -best.x * eastPerPixel;

            double north =
                    -best.y * northPerPixel;

            ConsoleUtil.log(
                    "result: x="
                            + best.x
                            + ", y="
                            + best.y
                            + ", east="
                            + east
                            + ", north="
                            + north
                            + ", error="
                            + best.error
            );

            return OffsetResult.valid(
                    best.x,
                    best.y,
                    east,
                    north,
                    best.error
            );

        } finally {

            if (matcher != null) {
                matcher.release();
            }

            imageryEdges.release();
        }
    }

    private BufferedImage renderGeometry() {
        return GeometryRasterizer.rasterize(
                dataLayer,
                mapView,
                0.0,
                0.0
        );
    }

    private void saveDebugImages(
            BufferedImage imagery,
            BufferedImage geometryImage) {

        try {

            ImageIO.write(
                    imagery,
                    "png",
                    new File(
                            "D:\\temp\\offset-imagery.png"
                    )
            );

            ImageIO.write(
                    geometryImage,
                    "png",
                    new File(
                            "D:\\temp\\offset-geometry.png"
                    )
            );

        } catch (IOException e) {

            throw new RuntimeException(e);
        }
    }

    /**
     * Render ONLY the selected imagery layer.
     */
    private BufferedImage renderImagery() {

        int width = mapView.getWidth();
        int height = mapView.getHeight();

        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = image.createGraphics();

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

            // Wichtig: paintLayer() erwartet einen gültigen Clip.
            graphics.setClip(
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight()
            );

            /*
             * JOSM exposes paintLayer specifically for
             * rendering an individual map layer.
             */
            mapView.paintLayer(
                    imageryLayer,
                    graphics
            );

        } finally {
            graphics.dispose();
        }

        return image;
    }

    /**
     * Coarse-to-fine optimization.
     */
    private SearchResult search(
            BuildingEdgeMatcher matcher) {

        SearchResult best =
                new SearchResult(
                        0,
                        0,
                        Double.MAX_VALUE
                );

        /*
         * First pass:
         *
         * Search +/- 100 pixels in steps of 5.
         */
        for (int y = -100;
             y <= 100;
             y += 5) {

            for (int x = -100;
                 x <= 100;
                 x += 5) {

                double error =
                        matcher.calculateError(
                                x,
                                y
                        );

                if (error < best.error) {

                    best =
                            new SearchResult(
                                    x,
                                    y,
                                    error
                            );
                }
            }
        }

        /*
         * Second pass:
         *
         * Refine around the coarse result.
         */
        double centerX =
                best.x;

        double centerY =
                best.y;

        SearchResult refined =
                new SearchResult(
                        centerX,
                        centerY,
                        best.error
                );

        for (double y = centerY - 2;
             y <= centerY + 2;
             y += 0.25) {

            for (double x = centerX - 2;
                 x <= centerX + 2;
                 x += 0.25) {

                double error =
                        matcher.calculateError(
                                x,
                                y
                        );

                if (error < refined.error) {

                    refined =
                            new SearchResult(
                                    x,
                                    y,
                                    error
                            );
                }
            }
        }

        return refined;
    }

    private void createCannyDebugImages(
            Mat imageryEdges,
            List<Point> imageryEdgePoints) {

        java.io.File tempDirectory =
                new java.io.File("D:\\temp");

        if (!tempDirectory.exists()) {
            tempDirectory.mkdirs();
        }

        org.opencv.imgcodecs.Imgcodecs.imwrite(
                "D:\\temp\\imagery-canny.png",
                imageryEdges
        );

        Mat imageryEdgesInBounds =
                Mat.zeros(
                        imageryEdges.size(),
                        imageryEdges.type()
                );

        for (Point point : imageryEdgePoints) {
            int x = (int) point.x;
            int y = (int) point.y;

            imageryEdgesInBounds.put(
                    y,
                    x,
                    255
            );
        }

        org.opencv.imgcodecs.Imgcodecs.imwrite(
                "D:\\temp\\imagery-canny-bounds.png",
                imageryEdgesInBounds
        );

        imageryEdgesInBounds.release();
    }

    private java.awt.Rectangle getDataLayerBounds() {

        int width = mapView.getWidth();
        int height = mapView.getHeight();

        int minX = width;
        int minY = height;
        int maxX = 0;
        int maxY = 0;

        for (org.openstreetmap.josm.data.osm.Way way :
                dataLayer.getDataSet().getWays()) {

            if (FilterUtil.filterOutWay(way)) {
                continue;
            }

            for (org.openstreetmap.josm.data.osm.Node node :
                    way.getNodes()) {

                if (node.isDeleted()
                        || node.isIncomplete()) {
                    continue;
                }

                java.awt.geom.Point2D point =
                        mapView.getPoint2D(
                                node.getEastNorth()
                        );

                int x =
                        (int) Math.round(point.getX());

                int y =
                        (int) Math.round(point.getY());

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (minX > maxX || minY > maxY) {
            return new java.awt.Rectangle(
                    0,
                    0,
                    width,
                    height
            );
        }

        int margin = 10;

        minX = Math.max(
                0,
                minX - margin
        );

        minY = Math.max(
                0,
                minY - margin
        );

        maxX = Math.min(
                width - 1,
                maxX + margin
        );

        maxY = Math.min(
                height - 1,
                maxY + margin
        );

        return new java.awt.Rectangle(
                minX,
                minY,
                maxX - minX + 1,
                maxY - minY + 1
        );
    }

    private List<Point> extractPoints(
            Mat edges) {

        List<Point> points =
                new ArrayList<>();

        for (
                int y = 0;
                y < edges.rows();
                y++
        ) {

            for (
                    int x = 0;
                    x < edges.cols();
                    x++
            ) {

                double value =
                        edges.get(
                                y,
                                x
                        )[0];

                if (value > 0) {

                    points.add(
                            new Point(
                                    x,
                                    y
                            )
                    );
                }
            }
        }

        return points;
    }

    private double getEastPerPixel() {
        EastNorth a = mapView.getEastNorth(0, 0);
        EastNorth b = mapView.getEastNorth(1, 0);
        return b.east() - a.east();
    }

    private double getNorthPerPixel() {
        EastNorth a = mapView.getEastNorth(0, 0);
        EastNorth b = mapView.getEastNorth(0, 1);
        return b.north() - a.north();
    }

    /**
     * Apply result to JOSM.
     */
    public void apply(
            OffsetResult result) {
        ConsoleUtil.log(
                "DEBUG: imagery offset application disabled."
        );
        return;
        /*
        Projection projection =
                MainApplication.getMap()
                        .mapView
                        .getProjection();

        EastNorth current =
                imageryLayer
                        .getDisplaySettings()
                        .getDisplacement();

        EastNorth newOffset =
                new EastNorth(
                        current.east()
                                + result.getEastOffset(),

                        current.north()
                                + result.getNorthOffset()
                );

        EastNorth center =
                mapView.getCenter();

        org.openstreetmap.josm.data.coor.LatLon centerLatLon =
                projection.eastNorth2latlon(
                        center
                );

        OffsetBookmark bookmark =
                new OffsetBookmark(
                        projection.toCode(),
                        imageryLayer.getInfo().getId(),
                        imageryLayer.getInfo().getName(),
                        "Offset Calc",
                        newOffset,
                        centerLatLon
                );

        imageryLayer
                .getDisplaySettings()
                .setOffsetBookmark(
                        bookmark
                );

        mapView.repaint();
        */
    }
}