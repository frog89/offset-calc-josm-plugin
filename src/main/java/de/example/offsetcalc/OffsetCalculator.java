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

import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.ViewportData;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.imagery.OffsetBookmark;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

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
                "config = " + config
        );

        if (config.isSelectedBuildingsOnly()
                && dataLayer.getDataSet()
                .getSelectedWays()
                .isEmpty()) {

            return OffsetResult.invalid(
                    "No buildings are selected."
            );
        }

        java.awt.Rectangle initialDataBounds =
                getDataLayerBounds(
                        config.isSelectedBuildingsOnly()
                );

        if (initialDataBounds.width <= 0
                || initialDataBounds.height <= 0) {

            return OffsetResult.invalid(
                    "No usable building bounds found."
            );
        }

        ProjectionBounds projectionBounds =
                getProjectionBounds(
                        initialDataBounds
                );

        ViewportData originalViewport =
                new ViewportData(
                        mapView.getCenter(),
                        mapView.getScale()
                );

        try {

            /*
             * Let JOSM itself perform the zoom.
             *
             * This is important because JOSM's
             * zoomTo(ProjectionBounds) also applies
             * its scaleFloor() handling.
             */
            mapView.zoomTo(
                    projectionBounds
            );

            /*
             * The analysis viewport is now based on
             * the actual MapView state after JOSM
             * has performed the zoom.
             */
            AnalysisViewport analysisViewport =
                    createAnalysisViewport();

            /*
             * Recalculate the building bounds after
             * the zoom because the screen coordinates
             * have changed.
             */
            java.awt.Rectangle analysisDataBounds =
                    getDataLayerBounds(
                            config.isSelectedBuildingsOnly()
                    );

            if (analysisDataBounds.width <= 0
                    || analysisDataBounds.height <= 0) {

                return OffsetResult.invalid(
                        "No usable building bounds found after zoom."
                );
            }

            /*
             * Render the complete current JOSM viewport
             * in its native MapView coordinate system.
             */
            BufferedImage fullImagery =
                    renderImagery(
                            analysisViewport
                    );

            /*
             * Crop the imagery around the building bounds.
             *
             * Padding is specified in meters and converted
             * to the current MapView scale inside the
             * crop method.
             */
            BufferedImage imagery =
                    cropToDataBounds(
                            fullImagery,
                            analysisDataBounds,
                            mapView.getWidth(),
                            mapView.getHeight(),
                            config.getPaddingMeters()
                    );

            /*
             * Render geometry in exactly the same
             * MapView coordinate system.
             */
            BufferedImage fullGeometry =
                    renderGeometry(
                            analysisViewport,
                            config.isSelectedBuildingsOnly()
                    );

            /*
             * Apply exactly the same crop to the geometry.
             */
            BufferedImage geometryImage =
                    cropToDataBounds(
                            fullGeometry,
                            analysisDataBounds,
                            mapView.getWidth(),
                            mapView.getHeight(),
                            config.getPaddingMeters()
                    );

            /*
             * Canny is calculated only after the crop.
             */
            Mat imageryEdges =
                    EdgeDetector.detectEdges(
                            imagery
                    );

            saveDebugImages(
                    imagery,
                    geometryImage
            );

            List<Point> imageryEdgePoints =
                    extractPoints(
                            imageryEdges
                    );

            createCannyDebugImages(
                    imageryEdges,
                    imageryEdgePoints,
                    new java.awt.Rectangle(
                            0,
                            0,
                            imagery.getWidth(),
                            imagery.getHeight()
                    )
            );

            java.awt.Point imageOrigin =
                    getAnalysisImageOrigin(
                            analysisDataBounds,
                            config.getPaddingMeters()
                    );

            int imageOriginX =
                    imageOrigin.x;

            int imageOriginY =
                    imageOrigin.y;

            ConsoleUtil.log(
                    "analysis image origin: x="
                            + imageOriginX
                            + ", y="
                            + imageOriginY
            );

            BuildingEdgeMatcher matcher =
                    new BuildingEdgeMatcher(
                            dataLayer,
                            mapView,
                            imageryEdges,
                            config.getBuildingSearchRadiusMeters(),
                            config.getTestOffsetX(),
                            config.getTestOffsetY(),
                            config.isSelectedBuildingsOnly(),
                            imageOriginX,
                            imageOriginY
                    );

            SearchResult best =
                    search(
                            matcher
                    );

            matcher.logBuildingErrorStatistics(
                    best.x,
                    best.y
            );

            ConsoleUtil.log(
                    "objective (truncated, trimmed RMS) = "
                            + best.error
                            + ", shift px = ("
                            + best.x + ", " + best.y + ")"
            );

            matcher.logPerBuildingOffsets(
                    best.x,
                    best.y
            );

            ConsoleUtil.log(
                    "building matching: candidates="
                            + matcher.getCandidateBuildingCount()
                            + ", withEdges="
                            + matcher.getBuildingWithEdgesCount()
            );

            double eastPerPixel =
                    Math.abs(
                            getEastPerPixel()
                    );

            double northPerPixel =
                    Math.abs(
                            getNorthPerPixel()
                    );

            double eastOffset =
                    -best.x * eastPerPixel;

            /*
             * Pixel-Y zeigt nach unten, North zeigt nach oben.
             * best.y ist die Verschiebung der Geometrie in Bildkoordinaten;
             * das Imagery muss entgegengesetzt verschoben werden.
             * Entgegengesetzt in Pixel-Y (nach oben) = positives North.
             * (East dagegen: -best.x, da beide Achsen gleich laufen.)
             */
            double northOffset =
                    best.y * northPerPixel;

            return OffsetResult.valid(
                    best.x,
                    best.y,
                    eastOffset,
                    northOffset,
                    best.error
            );

        } finally {

            mapView.zoomTo(
                    originalViewport
            );
        }
    }

    private BufferedImage renderGeometry(
            AnalysisViewport analysisViewport,
            boolean selectedBuildingsOnly) {

        int mapViewWidth =
                mapView.getWidth();

        int mapViewHeight =
                mapView.getHeight();

        if (mapViewWidth <= 0
                || mapViewHeight <= 0) {

            throw new IllegalStateException(
                    "MapView has no usable size."
            );
        }

        /*
         * Geometry is rendered in exactly the same
         * coordinate system as the MapView.
         *
         * No scaling is applied here.
         */
        return GeometryRasterizer.rasterize(
                dataLayer,
                mapView,
                mapViewWidth,
                mapViewHeight,
                selectedBuildingsOnly
        );
    }

    /**
     * Render ONLY the selected imagery layer.
     */
    private BufferedImage renderImagery(
            AnalysisViewport analysisViewport) {

        int mapViewWidth =
                mapView.getWidth();

        int mapViewHeight =
                mapView.getHeight();

        if (mapViewWidth <= 0
                || mapViewHeight <= 0) {

            throw new IllegalStateException(
                    "MapView has no usable size."
            );
        }

        BufferedImage image =
                new BufferedImage(
                        mapViewWidth,
                        mapViewHeight,
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
                    mapViewWidth,
                    mapViewHeight
            );

            graphics.setComposite(
                    java.awt.AlphaComposite.SrcOver
            );

            graphics.setClip(
                    0,
                    0,
                    mapViewWidth,
                    mapViewHeight
            );

            /*
             * Important:
             *
             * paintLayer() expects a Graphics2D whose
             * dimensions correspond to the MapView.
             *
             * Therefore we deliberately do NOT scale
             * the Graphics2D here.
             */
            mapView.paintLayer(
                    imageryLayer,
                    graphics
            );

        } finally {
            graphics.dispose();
        }

        ConsoleUtil.log(
                "full imagery raster: width="
                        + image.getWidth()
                        + ", height="
                        + image.getHeight()
                        + ", mapView="
                        + mapViewWidth
                        + "x"
                        + mapViewHeight
        );

        return image;
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
     * Coarse-to-fine optimization.
     */
    private SearchResult search(
            BuildingEdgeMatcher matcher) {

        return OffsetSearch.minimize(
                matcher::calculateError
        );
    }

    private void createCannyDebugImages(
            Mat imageryEdges,
            List<Point> imageryEdgePoints,
            java.awt.Rectangle dataBounds) {

        try {

            BufferedImage cannyImage =
                    new BufferedImage(
                            imageryEdges.cols(),
                            imageryEdges.rows(),
                            BufferedImage.TYPE_BYTE_GRAY
                    );

            byte[] pixels =
                    new byte[
                            imageryEdges.cols()
                                    * imageryEdges.rows()
                            ];

            imageryEdges.get(
                    0,
                    0,
                    pixels
            );

            byte[] targetPixels =
                    (
                            (
                                    java.awt.image.DataBufferByte)
                                    cannyImage.getRaster()
                                            .getDataBuffer()
                    ).getData();

            System.arraycopy(
                    pixels,
                    0,
                    targetPixels,
                    0,
                    pixels.length
            );

            ImageIO.write(
                    cannyImage,
                    "png",
                    new File(
                            "D:\\temp\\offset-canny.png"
                    )
            );

            ConsoleUtil.log(
                    "canny image size: width="
                            + imageryEdges.cols()
                            + ", height="
                            + imageryEdges.rows()
                            + ", edge points="
                            + imageryEdgePoints.size()
                            + ", data bounds: x="
                            + dataBounds.x
                            + ", y="
                            + dataBounds.y
                            + ", width="
                            + dataBounds.width
                            + ", height="
                            + dataBounds.height
            );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not write Canny debug image.",
                    e
            );
        }
    }

    private java.awt.Rectangle getDataLayerBounds(
            boolean selectedBuildingsOnly) {

        int minX =
                Integer.MAX_VALUE;

        int minY =
                Integer.MAX_VALUE;

        int maxX =
                Integer.MIN_VALUE;

        int maxY =
                Integer.MIN_VALUE;

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

            for (Node node :
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
                        (int) Math.round(
                                point.getX()
                        );

                int y =
                        (int) Math.round(
                                point.getY()
                        );

                minX =
                        Math.min(
                                minX,
                                x
                        );

                minY =
                        Math.min(
                                minY,
                                y
                        );

                maxX =
                        Math.max(
                                maxX,
                                x
                        );

                maxY =
                        Math.max(
                                maxY,
                                y
                        );
            }
        }

        if (minX > maxX
                || minY > maxY) {

            return new java.awt.Rectangle(
                    0,
                    0,
                    mapView.getWidth(),
                    mapView.getHeight()
            );
        }

        int margin = 10;

        minX -= margin;
        minY -= margin;
        maxX += margin;
        maxY += margin;

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

    private org.openstreetmap.josm.data.ProjectionBounds getProjectionBounds(
            java.awt.Rectangle screenBounds) {

        org.openstreetmap.josm.data.coor.EastNorth min =
                mapView.getEastNorth(
                        screenBounds.x,
                        screenBounds.y
                );

        org.openstreetmap.josm.data.coor.EastNorth max =
                mapView.getEastNorth(
                        screenBounds.x + screenBounds.width,
                        screenBounds.y + screenBounds.height
                );

        double minEast =
                Math.min(
                        min.east(),
                        max.east()
                );

        double maxEast =
                Math.max(
                        min.east(),
                        max.east()
                );

        double minNorth =
                Math.min(
                        min.north(),
                        max.north()
                );

        double maxNorth =
                Math.max(
                        min.north(),
                        max.north()
                );

        return new org.openstreetmap.josm.data.ProjectionBounds(
                minEast,
                minNorth,
                maxEast,
                maxNorth
        );
    }

    private AnalysisViewport createAnalysisViewport() {

        final int maxRasterSize = 2000;

        int mapViewWidth =
                mapView.getWidth();

        int mapViewHeight =
                mapView.getHeight();

        if (mapViewWidth <= 0
                || mapViewHeight <= 0) {

            throw new IllegalStateException(
                    "MapView has no usable size."
            );
        }

        double rasterScale;

        if (mapViewWidth >= mapViewHeight) {

            rasterScale =
                    (double) maxRasterSize
                            / mapViewWidth;

        } else {

            rasterScale =
                    (double) maxRasterSize
                            / mapViewHeight;
        }

        int width =
                Math.max(
                        1,
                        (int) Math.round(
                                mapViewWidth
                                        * rasterScale
                        )
                );

        int height =
                Math.max(
                        1,
                        (int) Math.round(
                                mapViewHeight
                                        * rasterScale
                        )
                );

        double mapViewScale =
                mapView.getScale();

        ConsoleUtil.log(
                "analysis viewport: mapView="
                        + mapViewWidth
                        + "x"
                        + mapViewHeight
                        + ", raster="
                        + width
                        + "x"
                        + height
                        + ", rasterScale="
                        + rasterScale
                        + ", mapViewScale="
                        + mapViewScale
                        + ", center="
                        + mapView.getCenter()
        );

        return new AnalysisViewport(
                mapView.getCenter(),
                mapViewScale,
                width,
                height
        );
    }

    /**
     * Apply result to JOSM.
     */
    public void applyResult(OffsetResult result) {
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

    private BufferedImage cropToDataBounds(
            BufferedImage source,
            java.awt.Rectangle dataBounds,
            int mapViewWidth,
            int mapViewHeight,
            double paddingMeters) {

        if (source == null) {
            throw new IllegalArgumentException(
                    "Source image must not be null."
            );
        }

        if (dataBounds == null
                || dataBounds.width <= 0
                || dataBounds.height <= 0) {

            throw new IllegalArgumentException(
                    "Invalid data bounds."
            );
        }

        if (mapViewWidth <= 0
                || mapViewHeight <= 0) {

            throw new IllegalArgumentException(
                    "Invalid MapView dimensions."
            );
        }

        if (paddingMeters < 0.0) {
            throw new IllegalArgumentException(
                    "Padding must not be negative."
            );
        }

        double mapViewScale =
                mapView.getScale();

        if (mapViewScale <= 0.0) {
            throw new IllegalStateException(
                    "MapView has no usable scale."
            );
        }

        /*
         * JOSM's scale is expressed in projected
         * East/North units per screen pixel.
         *
         * Therefore we can convert the requested
         * padding from meters to MapView pixels.
         */
        int paddingPixels =
                (int) Math.ceil(
                        paddingMeters
                                / mapViewScale
                );

        /*
         * The source image is a uniform scaled
         * representation of the complete MapView.
         *
         * Therefore the same scale factor is used
         * for X and Y.
         */
        double rasterScale =
                (double) source.getWidth()
                        / mapViewWidth;

        int paddedX =
                dataBounds.x
                        - paddingPixels;

        int paddedY =
                dataBounds.y
                        - paddingPixels;

        int paddedRight =
                dataBounds.x
                        + dataBounds.width
                        + paddingPixels;

        int paddedBottom =
                dataBounds.y
                        + dataBounds.height
                        + paddingPixels;

        int sourceX =
                (int) Math.floor(
                        paddedX * rasterScale
                );

        int sourceY =
                (int) Math.floor(
                        paddedY * rasterScale
                );

        int sourceRight =
                (int) Math.ceil(
                        paddedRight * rasterScale
                );

        int sourceBottom =
                (int) Math.ceil(
                        paddedBottom * rasterScale
                );

        sourceX =
                Math.max(
                        0,
                        Math.min(
                                sourceX,
                                source.getWidth() - 1
                        )
                );

        sourceY =
                Math.max(
                        0,
                        Math.min(
                                sourceY,
                                source.getHeight() - 1
                        )
                );

        sourceRight =
                Math.max(
                        sourceX + 1,
                        Math.min(
                                sourceRight,
                                source.getWidth()
                        )
                );

        sourceBottom =
                Math.max(
                        sourceY + 1,
                        Math.min(
                                sourceBottom,
                                source.getHeight()
                        )
                );

        int cropWidth =
                sourceRight
                        - sourceX;

        int cropHeight =
                sourceBottom
                        - sourceY;

        BufferedImage cropped =
                new BufferedImage(
                        cropWidth,
                        cropHeight,
                        BufferedImage.TYPE_INT_ARGB
                );

        Graphics2D graphics =
                cropped.createGraphics();

        try {

            graphics.drawImage(
                    source,
                    0,
                    0,
                    cropWidth,
                    cropHeight,
                    sourceX,
                    sourceY,
                    sourceRight,
                    sourceBottom,
                    null
            );

        } finally {
            graphics.dispose();
        }

        ConsoleUtil.log(
                "cropped analysis raster: source="
                        + source.getWidth()
                        + "x"
                        + source.getHeight()
                        + ", data bounds="
                        + dataBounds.x
                        + ","
                        + dataBounds.y
                        + " "
                        + dataBounds.width
                        + "x"
                        + dataBounds.height
                        + ", padding="
                        + paddingMeters
                        + "m ("
                        + paddingPixels
                        + "px)"
                        + ", result="
                        + cropWidth
                        + "x"
                        + cropHeight
        );

        return cropped;
    }

    private java.awt.Point getAnalysisImageOrigin(
            java.awt.Rectangle dataBounds,
            double paddingMeters) {

        if (dataBounds == null
                || dataBounds.width <= 0
                || dataBounds.height <= 0) {

            throw new IllegalArgumentException(
                    "Invalid data bounds."
            );
        }

        if (paddingMeters < 0.0) {
            throw new IllegalArgumentException(
                    "Padding must not be negative."
            );
        }

        double mapViewScale =
                mapView.getScale();

        if (mapViewScale <= 0.0
                || !Double.isFinite(mapViewScale)) {

            throw new IllegalStateException(
                    "MapView has no usable scale."
            );
        }

        int paddingPixels =
                (int) Math.ceil(
                        paddingMeters
                                / mapViewScale
                );

        int originX =
                dataBounds.x
                        - paddingPixels;

        int originY =
                dataBounds.y
                        - paddingPixels;

        originX =
                Math.max(
                        0,
                        originX
                );

        originY =
                Math.max(
                        0,
                        originY
                );

        return new java.awt.Point(
                originX,
                originY
        );
    }

    private static final class AnalysisViewport {

        private final org.openstreetmap.josm.data.coor.EastNorth center;
        private final double scale;
        private final int width;
        private final int height;

        private AnalysisViewport(
                org.openstreetmap.josm.data.coor.EastNorth center,
                double scale,
                int width,
                int height) {

            this.center = center;
            this.scale = scale;
            this.width = width;
            this.height = height;
        }
    }
}