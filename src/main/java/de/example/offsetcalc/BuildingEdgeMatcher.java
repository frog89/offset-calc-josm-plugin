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
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

public final class BuildingEdgeMatcher {

    private static final int MAX_GEOMETRY_POINTS = 10000;

    private final List<BuildingMatch> matches =
            new ArrayList<>();

    private final double radiusMeters;
    private final double testOffsetX;
    private final double testOffsetY;

    private int candidateBuildingCount;
    private int buildingWithEdgesCount;

    public BuildingEdgeMatcher(
            OsmDataLayer dataLayer,
            MapView mapView,
            Mat imageryEdges,
            double radiusMeters,
            double testOffsetX,
            double testOffsetY,
            boolean selectedBuildingsOnly,
            int imageOriginX,
            int imageOriginY) {

        this.radiusMeters = radiusMeters;
        this.testOffsetX = testOffsetX;
        this.testOffsetY = testOffsetY;

        double eastPerPixel =
                Math.abs(
                        getEastPerPixel(
                                mapView
                        )
                );

        double northPerPixel =
                Math.abs(
                        getNorthPerPixel(
                                mapView
                        )
                );

        if (!Double.isFinite(eastPerPixel)
                || eastPerPixel <= 0
                || !Double.isFinite(northPerPixel)
                || northPerPixel <= 0) {

            throw new IllegalStateException(
                    "Could not determine map scale."
            );
        }

        double metersPerPixel =
                Math.min(
                        eastPerPixel,
                        northPerPixel
                );

        double radiusPixels =
                radiusMeters / metersPerPixel;

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

            candidateBuildingCount++;

            BuildingMatch match =
                    createBuildingMatch(
                            way,
                            mapView,
                            imageryEdges,
                            radiusPixels,
                            imageOriginX,
                            imageOriginY
                    );

            if (match != null) {
                matches.add(match);
                buildingWithEdgesCount++;
            }
        }

        downsampleGeometryPoints();
    }

    public double calculateError(
            double dx,
            double dy) {

        double effectiveDx =
                dx + testOffsetX;

        double effectiveDy =
                dy + testOffsetY;

        double totalSquaredError = 0.0;
        int buildingCount = 0;

        for (BuildingMatch match : matches) {

            ErrorStatistics statistics =
                    match.calculateError(
                            effectiveDx,
                            effectiveDy
                    );

            if (statistics.pointCount == 0) {
                continue;
            }

            double buildingMeanSquaredError =
                    statistics.totalSquaredError
                            / statistics.pointCount;

            totalSquaredError +=
                    buildingMeanSquaredError;

            buildingCount++;
        }

        if (buildingCount == 0) {
            return Double.MAX_VALUE;
        }

        return Math.sqrt(
                totalSquaredError
                        / buildingCount
        );
    }

    public int getCandidateBuildingCount() {
        return candidateBuildingCount;
    }

    public int getBuildingWithEdgesCount() {
        return buildingWithEdgesCount;
    }

    public int getGeometryPointCount() {

        int count = 0;

        for (BuildingMatch match : matches) {
            count += match.geometryPoints.size();
        }

        return count;
    }

    public double getRadiusMeters() {
        return radiusMeters;
    }

    public void release() {

        for (BuildingMatch match : matches) {
            match.release();
        }

        matches.clear();
    }

    private BuildingMatch createBuildingMatch(
            Way way,
            MapView mapView,
            Mat imageryEdges,
            double radiusPixels,
            int imageOriginX,
            int imageOriginY) {

        List<Point2D> points =
                getWayPoints(
                        way,
                        mapView,
                        imageOriginX,
                        imageOriginY
                );

        if (points.size() < 2) {
            return null;
        }

        double searchRangePixels = 102.0;

        java.awt.Rectangle bounds =
                getExpandedBounds(
                        points,
                        radiusPixels + searchRangePixels,
                        imageryEdges.cols(),
                        imageryEdges.rows()
                );

        if (bounds.width < 2
                || bounds.height < 2) {
            return null;
        }

        BufferedImage buildingMask =
                createBuildingMask(
                        points,
                        bounds,
                        0.0,
                        0.0
                );

        Mat buildingMaskMat =
                bufferedImageToMat(
                        buildingMask
                );

        Mat buildingMaskInverse =
                new Mat();

        Core.bitwise_not(
                buildingMaskMat,
                buildingMaskInverse
        );

        Mat buildingDistance =
                new Mat();

        Imgproc.distanceTransform(
                buildingMaskInverse,
                buildingDistance,
                Imgproc.DIST_L2,
                3
        );

        org.opencv.core.Rect openCvBounds =
                new org.opencv.core.Rect(
                        bounds.x,
                        bounds.y,
                        bounds.width,
                        bounds.height
                );

        Mat localImageryEdges =
                imageryEdges
                        .submat(openCvBounds)
                        .clone();

        long selectedEdgePixels = 0L;

        for (int y = 0;
             y < localImageryEdges.rows();
             y++) {

            for (int x = 0;
                 x < localImageryEdges.cols();
                 x++) {

                double[] edge =
                        localImageryEdges.get(
                                y,
                                x
                        );

                if (edge == null
                        || edge[0] <= 0) {
                    continue;
                }

                double[] distance =
                        buildingDistance.get(
                                y,
                                x
                        );

                if (distance == null
                        || distance[0] > radiusPixels) {

                    localImageryEdges.put(
                            y,
                            x,
                            0
                    );

                } else {

                    selectedEdgePixels++;
                }
            }
        }

        buildingDistance.release();
        buildingMaskInverse.release();
        buildingMaskMat.release();

        if (selectedEdgePixels == 0) {
            localImageryEdges.release();
            return null;
        }

        Mat imageryInverse =
                new Mat();

        Core.bitwise_not(
                localImageryEdges,
                imageryInverse
        );

        Mat imageryDistance =
                new Mat();

        Imgproc.distanceTransform(
                imageryInverse,
                imageryDistance,
                Imgproc.DIST_L2,
                3
        );

        imageryInverse.release();
        localImageryEdges.release();

        BufferedImage geometry =
                createBuildingMask(
                        points,
                        bounds,
                        0.0,
                        0.0
                );

        List<Point> geometryPoints =
                extractPoints(
                        geometry
                );

        if (geometryPoints.isEmpty()) {
            imageryDistance.release();
            return null;
        }

        return new BuildingMatch(
                imageryDistance,
                geometryPoints
        );
    }

    private BufferedImage createBuildingMask(
            List<Point2D> points,
            java.awt.Rectangle bounds,
            double offsetX,
            double offsetY) {

        BufferedImage image =
                new BufferedImage(
                        bounds.width,
                        bounds.height,
                        BufferedImage.TYPE_BYTE_GRAY
                );

        Graphics2D graphics =
                image.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF
            );

            graphics.setColor(
                    Color.BLACK
            );

            graphics.fillRect(
                    0,
                    0,
                    bounds.width,
                    bounds.height
            );

            graphics.setColor(
                    Color.WHITE
            );

            graphics.setStroke(
                    new BasicStroke(
                            2.0f,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    )
            );

            for (int i = 0;
                 i < points.size() - 1;
                 i++) {

                Point2D a =
                        points.get(i);

                Point2D b =
                        points.get(i + 1);

                int ax =
                        (int) Math.round(
                                a.getX()
                                        + offsetX
                                        - bounds.x
                        );

                int ay =
                        (int) Math.round(
                                a.getY()
                                        + offsetY
                                        - bounds.y
                        );

                int bx =
                        (int) Math.round(
                                b.getX()
                                        + offsetX
                                        - bounds.x
                        );

                int by =
                        (int) Math.round(
                                b.getY()
                                        + offsetY
                                        - bounds.y
                        );

                graphics.drawLine(
                        ax,
                        ay,
                        bx,
                        by
                );
            }

        } finally {
            graphics.dispose();
        }

        return image;
    }

    private java.awt.Rectangle getExpandedBounds(
            List<Point2D> points,
            double radiusPixels,
            int imageWidth,
            int imageHeight) {

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Point2D point : points) {

            minX =
                    Math.min(
                            minX,
                            point.getX()
                    );

            minY =
                    Math.min(
                            minY,
                            point.getY()
                    );

            maxX =
                    Math.max(
                            maxX,
                            point.getX()
                    );

            maxY =
                    Math.max(
                            maxY,
                            point.getY()
                    );
        }

        int x =
                Math.max(
                        0,
                        (int) Math.floor(
                                minX - radiusPixels
                        )
                );

        int y =
                Math.max(
                        0,
                        (int) Math.floor(
                                minY - radiusPixels
                        )
                );

        int right =
                Math.min(
                        imageWidth - 1,
                        (int) Math.ceil(
                                maxX + radiusPixels
                        )
                );

        int bottom =
                Math.min(
                        imageHeight - 1,
                        (int) Math.ceil(
                                maxY + radiusPixels
                        )
                );

        return new java.awt.Rectangle(
                x,
                y,
                right - x + 1,
                bottom - y + 1
        );
    }

    private List<Point2D> getWayPoints(
            Way way,
            MapView mapView,
            int imageOriginX,
            int imageOriginY) {

        List<Point2D> points =
                new ArrayList<>();

        for (Node node : way.getNodes()) {

            if (node.isDeleted()
                    || node.isIncomplete()) {
                continue;
            }

            Point2D mapPoint =
                    mapView.getPoint2D(
                            node.getEastNorth()
                    );

            points.add(
                    new Point2D.Double(
                            mapPoint.getX()
                                    - imageOriginX,
                            mapPoint.getY()
                                    - imageOriginY
                    )
            );
        }

        return points;
    }

    private Mat bufferedImageToMat(
            BufferedImage image) {

        Mat mat =
                new Mat(
                        image.getHeight(),
                        image.getWidth(),
                        CvType.CV_8UC1
                );

        byte[] data =
                ((DataBufferByte)
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

    private List<Point> extractPoints(
            BufferedImage image) {

        List<Point> points =
                new ArrayList<>();

        byte[] data =
                ((DataBufferByte)
                        image.getRaster()
                                .getDataBuffer())
                        .getData();

        int width =
                image.getWidth();

        int height =
                image.getHeight();

        for (int y = 0;
             y < height;
             y++) {

            for (int x = 0;
                 x < width;
                 x++) {

                int index =
                        y * width + x;

                if ((data[index] & 0xff) > 0) {

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

    private void downsampleGeometryPoints() {

        int total =
                getGeometryPointCount();

        if (total <= MAX_GEOMETRY_POINTS) {
            return;
        }

        double factor =
                (double) MAX_GEOMETRY_POINTS
                        / total;

        for (BuildingMatch match : matches) {

            int originalSize =
                    match.geometryPoints.size();

            int targetSize =
                    Math.max(
                            1,
                            (int) Math.floor(
                                    originalSize
                                            * factor
                            )
                    );

            if (targetSize >= originalSize) {
                continue;
            }

            List<Point> reduced =
                    new ArrayList<>(
                            targetSize
                    );

            double step =
                    (double) originalSize
                            / targetSize;

            for (int i = 0;
                 i < targetSize;
                 i++) {

                int index =
                        (int) Math.floor(
                                i * step
                        );

                reduced.add(
                        match.geometryPoints.get(
                                index
                        )
                );
            }

            match.geometryPoints =
                    reduced;
        }
    }

    private static double getEastPerPixel(
            MapView mapView) {

        return mapView.getEastNorth(1, 0).east()
                - mapView.getEastNorth(0, 0).east();
    }

    private static double getNorthPerPixel(
            MapView mapView) {

        return mapView.getEastNorth(0, 1).north()
                - mapView.getEastNorth(0, 0).north();
    }

    private static final class BuildingMatch {

        private final Mat imageryDistance;

        private List<Point> geometryPoints;

        private BuildingMatch(
                Mat imageryDistance,
                List<Point> geometryPoints) {

            this.imageryDistance =
                    imageryDistance;

            this.geometryPoints =
                    geometryPoints;
        }

        private ErrorStatistics calculateError(
                double dx,
                double dy) {

            double totalSquaredError = 0.0;
            int count = 0;

            int width =
                    imageryDistance.cols();

            int height =
                    imageryDistance.rows();

            for (Point point :
                    geometryPoints) {

                double x =
                        point.x + dx;

                double y =
                        point.y + dy;

                if (x < 0
                        || x >= width - 1
                        || y < 0
                        || y >= height - 1) {

                    totalSquaredError +=
                            50.0 * 50.0;

                    count++;

                    continue;
                }

                int x0 =
                        (int) Math.floor(x);

                int y0 =
                        (int) Math.floor(y);

                int x1 =
                        x0 + 1;

                int y1 =
                        y0 + 1;

                double fx =
                        x - x0;

                double fy =
                        y - y0;

                double d00 =
                        imageryDistance.get(
                                y0,
                                x0
                        )[0];

                double d10 =
                        imageryDistance.get(
                                y0,
                                x1
                        )[0];

                double d01 =
                        imageryDistance.get(
                                y1,
                                x0
                        )[0];

                double d11 =
                        imageryDistance.get(
                                y1,
                                x1
                        )[0];

                double distance =
                        d00
                                * (1.0 - fx)
                                * (1.0 - fy)
                                + d10
                                * fx
                                * (1.0 - fy)
                                + d01
                                * (1.0 - fx)
                                * fy
                                + d11
                                * fx
                                * fy;

                distance =
                        Math.min(
                                distance,
                                50.0
                        );

                totalSquaredError +=
                        distance * distance;

                count++;
            }

            return new ErrorStatistics(
                    totalSquaredError,
                    count
            );
        }

        private void release() {
            imageryDistance.release();
        }
    }

    public void logBuildingErrorStatistics(
            double dx,
            double dy) {

        List<Double> errors =
                new ArrayList<>();

        for (BuildingMatch match :
                matches) {

            ErrorStatistics statistics =
                    match.calculateError(
                            dx + testOffsetX,
                            dy + testOffsetY
                    );

            if (statistics.pointCount == 0) {
                continue;
            }

            double error =
                    Math.sqrt(
                            statistics.totalSquaredError
                                    / statistics.pointCount
                    );

            if (Double.isFinite(error)) {
                errors.add(error);
            }
        }

        if (errors.isEmpty()) {
            ConsoleUtil.log(
                    "building error statistics: no data"
            );
            return;
        }

        errors.sort(
                Double::compareTo
        );

        double sum = 0.0;

        for (double error : errors) {
            sum += error;
        }

        double mean =
                sum / errors.size();

        double median =
                percentile(
                        errors,
                        50.0
                );

        double p90 =
                percentile(
                        errors,
                        90.0
                );

        double p95 =
                percentile(
                        errors,
                        95.0
                );

        double minimum =
                errors.get(0);

        double maximum =
                errors.get(
                        errors.size() - 1
                );

        ConsoleUtil.log(
                "building error statistics: count="
                        + errors.size()
                        + ", min="
                        + minimum
                        + ", median="
                        + median
                        + ", mean="
                        + mean
                        + ", p90="
                        + p90
                        + ", p95="
                        + p95
                        + ", max="
                        + maximum
        );
    }

    private double percentile(
            List<Double> values,
            double percentile) {

        if (values.isEmpty()) {
            return Double.NaN;
        }

        if (values.size() == 1) {
            return values.get(0);
        }

        double position =
                percentile
                        / 100.0
                        * (values.size() - 1);

        int lower =
                (int) Math.floor(position);

        int upper =
                (int) Math.ceil(position);

        if (lower == upper) {
            return values.get(lower);
        }

        double fraction =
                position - lower;

        return values.get(lower)
                + fraction
                * (
                values.get(upper)
                        - values.get(lower)
        );
    }

    private static final class ErrorStatistics {

        private final double totalSquaredError;
        private final int pointCount;

        private ErrorStatistics(
                double totalSquaredError,
                int pointCount) {

            this.totalSquaredError =
                    totalSquaredError;

            this.pointCount =
                    pointCount;
        }
    }
}