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

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

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

        ConsoleUtil.log(
                "config = "
                        + config
        );

        /*
         * 1. Render imagery.
         */
        BufferedImage imagery =
                renderImagery();

        /*
         * 2. Detect imagery edges.
         */
        Mat imageryEdges =
                EdgeDetector.detectEdges(
                        imagery
                );

        ConsoleUtil.logImageryEdgeStatistics(
                imageryEdges
        );

        /*
         * 3. Render OSM geometries.
         */
        BufferedImage geometryImage =
                renderGeometry();

        ConsoleUtil.logGeometryStatistics(
                imagery,
                geometryImage
        );

        saveDebugImages(
                imagery,
                geometryImage
        );

        /*
         * 4. Extract imagery edges inside the
         *    OSM data-layer bounds.
         */
        List<Point> imageryEdgePoints =
                extractPoints(
                        imageryEdges
                );

        java.awt.Rectangle dataBounds =
                getDataLayerBounds();

        ConsoleUtil.log(
                "data bounds = "
                        + dataBounds
        );

        imageryEdgePoints.removeIf(
                point -> !dataBounds.contains(
                        point.x,
                        point.y
                )
        );

        ConsoleUtil.log(
                "imagery edge points in data bounds = "
                        + imageryEdgePoints.size()
        );

        createCannyDebugImages(
                imageryEdges,
                imageryEdgePoints
        );

        /*
         * 5. Convert the imagery edges
         *    to a distance map.
         */
        Mat imageryDistance =
                createDistanceTransform(
                        imageryEdges
                );

        /*
         * 6. Extract OSM geometry pixels.
         */
        List<Point> geometryPoints =
                extractGeometryPoints(
                        geometryImage
                );

        if (geometryPoints.size() < 100) {

            imageryDistance.release();
            imageryEdges.release();

            return OffsetResult.invalid(
                    "Too few OSM geometry pixels were detected."
            );
        }

        /*
         * Downsample if necessary.
         */
        geometryPoints =
                downsample(
                        geometryPoints,
                        10000
                );

        /*
         * 7. Search for the best pixel offset.
         */
        SearchResult best =
                search(
                        imageryDistance,
                        geometryPoints
                );

        ConsoleUtil.logSearchResult(
                best,
                imageryDistance,
                geometryPoints,
                this::calculateError
        );

        /*
         * 8. Convert pixel offset to meters.
         */
        double eastPerPixel =
                getEastPerPixel();

        double northPerPixel =
                getNorthPerPixel();

        double east =
                -best.x * eastPerPixel;

        double north =
                -best.y * northPerPixel;

        ConsoleUtil.log(
                "eastPerPixel="
                        + eastPerPixel
                        + ", northPerPixel="
                        + northPerPixel
        );

        ConsoleUtil.log(
                "east="
                        + east
                        + ", north="
                        + north
        );

        /*
         * 9. Release OpenCV resources.
         */
        imageryEdges.release();
        imageryDistance.release();

        return OffsetResult.valid(
                best.x,
                best.y,
                east,
                north,
                best.error
        );
    }

    private BufferedImage renderGeometry() {

        return GeometryRasterizer.rasterize(
                dataLayer,
                mapView,
                config.testOffsetX,
                config.testOffsetY
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
            Mat distance,
            List<Point> edgePoints) {

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
        for (
                int y = -100;
                y <= 100;
                y += 5
        ) {

            for (
                    int x = -100;
                    x <= 100;
                    x += 5
            ) {

                double error =
                        calculateError(
                                distance,
                                edgePoints,
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
        double centerX = best.x;
        double centerY = best.y;

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
                        calculateError(
                                distance,
                                edgePoints,
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

    private Mat createDistanceTransform(
            Mat imageryEdges) {

        /*
         * White = imagery edge
         * Black = background
         *
         * distanceTransform() calculates the distance
         * to the nearest zero pixel. Therefore we
         * invert the edge image first.
         */
        Mat imageryEdgeInverse =
                new Mat();

        Core.bitwise_not(
                imageryEdges,
                imageryEdgeInverse
        );

        Core.MinMaxLocResult inverseMinMax =
                Core.minMaxLoc(
                        imageryEdgeInverse
                );

        System.out.println(
                "OffsetCalc: imagery inverse min="
                        + inverseMinMax.minVal
                        + ", max="
                        + inverseMinMax.maxVal
        );

        Mat imageryDistance =
                new Mat();

        Imgproc.distanceTransform(
                imageryEdgeInverse,
                imageryDistance,
                Imgproc.DIST_L2,
                3
        );

        System.out.println(
                "OffsetCalc: after distanceTransform: "
                        + "rows="
                        + imageryDistance.rows()
                        + ", cols="
                        + imageryDistance.cols()
                        + ", type="
                        + imageryDistance.type()
                        + ", depth="
                        + imageryDistance.depth()
                        + ", channels="
                        + imageryDistance.channels()
                        + ", empty="
                        + imageryDistance.empty()
        );

        Core.MinMaxLocResult distanceMinMax =
                Core.minMaxLoc(
                        imageryDistance
                );

        System.out.println(
                "OffsetCalc: imagery distance min="
                        + distanceMinMax.minVal
                        + ", max="
                        + distanceMinMax.maxVal
        );

        System.out.println(
                "OffsetCalc: distance type="
                        + imageryDistance.type()
                        + ", depth="
                        + imageryDistance.depth()
                        + ", channels="
                        + imageryDistance.channels()
                        + ", min="
                        + distanceMinMax.minVal
                        + ", max="
                        + distanceMinMax.maxVal
        );

        if (imageryDistance.empty()
                || imageryDistance.rows() <= 0
                || imageryDistance.cols() <= 0) {

            throw new IllegalStateException(
                    "OffsetCalc: distance transform produced an empty Mat."
            );
        }

        double[] sampleA =
                imageryDistance.get(0, 0);

        double[] sampleB =
                imageryDistance.get(
                        imageryDistance.rows() / 2,
                        imageryDistance.cols() / 2
                );

        double[] sampleC =
                imageryDistance.get(
                        imageryDistance.rows() - 1,
                        imageryDistance.cols() - 1
                );

        System.out.println(
                "OffsetCalc: distance samples: "
                        + (sampleA == null
                        ? "null"
                        : sampleA[0])
                        + ", "
                        + (sampleB == null
                        ? "null"
                        : sampleB[0])
                        + ", "
                        + (sampleC == null
                        ? "null"
                        : sampleC[0])
        );

        long zeroCount = 0L;
        long nonZeroCount = 0L;

        for (int y = 0;
             y < imageryEdgeInverse.rows();
             y++) {

            for (int x = 0;
                 x < imageryEdgeInverse.cols();
                 x++) {

                double[] pixel =
                        imageryEdgeInverse.get(
                                y,
                                x
                        );

                if (pixel == null) {
                    throw new IllegalStateException(
                            "OffsetCalc: imageryEdgeInverse.get("
                                    + y
                                    + ", "
                                    + x
                                    + ") returned null."
                    );
                }

                double value = pixel[0];

                if (value == 0) {
                    zeroCount++;
                } else {
                    nonZeroCount++;
                }
            }
        }

        System.out.println(
                "OffsetCalc: inverse zero="
                        + zeroCount
                        + ", nonZero="
                        + nonZeroCount
        );

        imageryEdgeInverse.release();

        return imageryDistance;
    }

    private List<Point> extractGeometryPoints(
            BufferedImage geometryImage) {

        Mat geometryGray =
                bufferedImageToGrayMat(
                        geometryImage
                );

        try {

            return extractPoints(
                    geometryGray
            );

        } finally {

            geometryGray.release();
        }
    }

    /**
     * Average distance from every imagery edge
     * to the nearest Data Layer geometry.
     */
    private double calculateError(
            Mat distance,
            List<Point> points,
            double dx,
            double dy) {

        double total = 0;
        int count = 0;

        int width = distance.cols();
        int height = distance.rows();

        for (Point point : points) {

            double x = point.x + dx;
            double y = point.y + dy;

            if (x < 0
                    || x >= width - 1
                    || y < 0
                    || y >= height - 1) {
                continue;
            }

            int x0 = (int) Math.floor(x);
            int y0 = (int) Math.floor(y);

            int x1 = x0 + 1;
            int y1 = y0 + 1;

            double fx = x - x0;
            double fy = y - y0;

            double d00 =distance.get(y0, x0)[0];
            double d10 = distance.get(y0, x1)[0];
            double d01 = distance.get(y1, x0)[0];
            double d11 = distance.get(y1, x1)[0];

            double d =
                    d00 * (1.0 - fx) * (1.0 - fy)
                            + d10 * fx * (1.0 - fy)
                            + d01 * (1.0 - fx) * fy
                            + d11 * fx * fy;

            d = Math.min(d, 50.0);

            total += d * d;
            count++;
        }

        if (count == 0) {
            return Double.MAX_VALUE;
        }

        return Math.sqrt(
                total / count
        );
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

    /**
     * Random-ish deterministic thinning.
     *
     * Taking every nth point is enough for the first
     * implementation and keeps optimization fast.
     */
    private List<Point> downsample(
            List<Point> points,
            int maxPoints) {

        if (points.size() <= maxPoints) {
            return points;
        }

        List<Point> result =
                new ArrayList<>(
                        maxPoints
                );

        double step =
                (double) points.size()
                        / maxPoints;

        for (
                int i = 0;
                i < maxPoints;
                i++
        ) {

            int index =
                    (int) Math.floor(
                            i * step
                    );

            result.add(
                    points.get(index)
            );
        }

        return result;
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

    private Mat bufferedImageToGrayMat(
            BufferedImage image) {

        Mat mat =
                new Mat(
                        image.getHeight(),
                        image.getWidth(),
                        CvType.CV_8UC1
                );

        byte[] data =
                ((java.awt.image.DataBufferByte)
                        image.getRaster()
                                .getDataBuffer())
                        .getData();

        mat.put(
                0,
                0,
                data
        );

        return mat;
    }

    /**
     * Apply result to JOSM.
     */
    public void apply(
            OffsetResult result) {

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
    }
}