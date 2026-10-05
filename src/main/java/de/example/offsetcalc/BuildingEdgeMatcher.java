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

    /*
     * Per-point distance is truncated at this value (pixels).
     * Edges farther away than this are treated as "no match"
     * instead of pulling the optimum towards them.
     */
    private static final double TRUNCATION_PIXELS = 12.0;

    /*
     * Fraction of the best buildings used for the global objective.
     * The worst buildings (wrong geometry, roof lean, clutter)
     * are ignored.
     */
    private static final double TRIM_FRACTION_KEPT = 0.75;

    /*
     * Upper bound used for pure statistics output.
     */
    private static final double STATISTICS_CAP_PIXELS = 50.0;

    /*
     * Per-building diagnostics are skipped above this count.
     */
    private static final int MAX_DIAGNOSTIC_BUILDINGS = 50;

    private final List<BuildingMatch> matches =
            new ArrayList<>();

    private final double radiusMeters;
    private final double metersPerPixel;
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

        this.metersPerPixel =
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

        double[] buildingErrors =
                new double[matches.size()];

        int buildingCount = 0;

        for (BuildingMatch match : matches) {

            ErrorStatistics statistics =
                    match.calculateError(
                            effectiveDx,
                            effectiveDy,
                            TRUNCATION_PIXELS
                    );

            if (statistics.pointCount == 0) {
                continue;
            }

            buildingErrors[buildingCount++] =
                    statistics.totalSquaredError
                            / statistics.pointCount;
        }

        if (buildingCount == 0) {
            return Double.MAX_VALUE;
        }

        java.util.Arrays.sort(
                buildingErrors,
                0,
                buildingCount
        );

        int keep =
                buildingCount <= 3
                        ? buildingCount
                        : (int) Math.ceil(
                        buildingCount * TRIM_FRACTION_KEPT
                );

        double sum = 0.0;

        for (int i = 0; i < keep; i++) {
            sum += buildingErrors[i];
        }

        return Math.sqrt(sum / keep);
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
                way.getUniqueId(),
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

        private final long wayId;
        private final float[] distanceData;
        private final int cols;
        private final int rows;

        private List<Point> geometryPoints;

        private BuildingMatch(
                long wayId,
                Mat imageryDistance,
                List<Point> geometryPoints) {

            this.wayId = wayId;
            this.cols = imageryDistance.cols();
            this.rows = imageryDistance.rows();

            /*
             * Copy the distance map once into a Java array.
             * Reading single pixels via Mat.get() costs a JNI call
             * each and dominated the runtime of the search.
             */
            Mat continuous =
                    imageryDistance.isContinuous()
                            ? imageryDistance
                            : imageryDistance.clone();

            this.distanceData =
                    new float[cols * rows];

            continuous.get(
                    0,
                    0,
                    this.distanceData
            );

            if (continuous != imageryDistance) {
                continuous.release();
            }

            imageryDistance.release();

            this.geometryPoints =
                    geometryPoints;
        }

        private ErrorStatistics calculateError(
                double dx,
                double dy,
                double cap) {

            double capSquared = cap * cap;

            double totalSquaredError = 0.0;
            int count = 0;

            for (Point point :
                    geometryPoints) {

                double x =
                        point.x + dx;

                double y =
                        point.y + dy;

                count++;

                if (x < 0
                        || x >= cols - 1
                        || y < 0
                        || y >= rows - 1) {

                    totalSquaredError += capSquared;
                    continue;
                }

                int x0 =
                        (int) Math.floor(x);

                int y0 =
                        (int) Math.floor(y);

                double fx =
                        x - x0;

                double fy =
                        y - y0;

                int index =
                        y0 * cols + x0;

                double d00 = distanceData[index];
                double d10 = distanceData[index + 1];
                double d01 = distanceData[index + cols];
                double d11 = distanceData[index + cols + 1];

                double distance =
                        d00 * (1.0 - fx) * (1.0 - fy)
                                + d10 * fx * (1.0 - fy)
                                + d01 * (1.0 - fx) * fy
                                + d11 * fx * fy;

                distance =
                        Math.min(
                                distance,
                                cap
                        );

                totalSquaredError +=
                        distance * distance;
            }

            return new ErrorStatistics(
                    totalSquaredError,
                    count
            );
        }

        private void release() {
            // distance Mat is already released in the constructor
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
                            dy + testOffsetY,
                            STATISTICS_CAP_PIXELS
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

    /**
     * Diagnostic: finds the best shift for every building on its own
     * and compares it with the global shift.
     *
     * Small spread  -> a global offset is a good model, remaining
     *                  error comes from a few outlier buildings.
     * Large spread  -> imagery distortion / roof lean / inaccurate
     *                  geometry; a single offset cannot do better.
     *
     * East/North are given with the same sign convention as the
     * final result (shift to apply to the imagery).
     */
    public void logPerBuildingOffsets(
            double globalDx,
            double globalDy) {

        if (matches.isEmpty()) {
            return;
        }

        if (matches.size() > MAX_DIAGNOSTIC_BUILDINGS) {

            ConsoleUtil.log(
                    "per-building offsets skipped: "
                            + matches.size()
                            + " buildings (limit "
                            + MAX_DIAGNOSTIC_BUILDINGS
                            + ")"
            );
            return;
        }

        double globalX = globalDx + testOffsetX;
        double globalY = globalDy + testOffsetY;

        ConsoleUtil.log(
                String.format(
                        java.util.Locale.ROOT,
                        "per-building optimum (global: x=%.2f, y=%.2f px; 1 px = %.3f m)",
                        globalX,
                        globalY,
                        metersPerPixel
                )
        );

        List<Double> ownXs = new ArrayList<>();
        List<Double> ownYs = new ArrayList<>();

        int index = 0;

        for (BuildingMatch match : matches) {

            index++;

            SearchResult own =
                    OffsetSearch.minimize(
                            (x, y) -> {

                                ErrorStatistics statistics =
                                        match.calculateError(
                                                x,
                                                y,
                                                TRUNCATION_PIXELS
                                        );

                                if (statistics.pointCount == 0) {
                                    return Double.MAX_VALUE;
                                }

                                return Math.sqrt(
                                        statistics.totalSquaredError
                                                / statistics.pointCount
                                );
                            }
                    );

            ErrorStatistics atOwn =
                    match.calculateError(
                            own.x,
                            own.y,
                            STATISTICS_CAP_PIXELS
                    );

            ErrorStatistics atGlobal =
                    match.calculateError(
                            globalX,
                            globalY,
                            STATISTICS_CAP_PIXELS
                    );

            double errorOwn =
                    Math.sqrt(
                            atOwn.totalSquaredError
                                    / Math.max(1, atOwn.pointCount)
                    );

            double errorGlobal =
                    Math.sqrt(
                            atGlobal.totalSquaredError
                                    / Math.max(1, atGlobal.pointCount)
                    );

            ownXs.add(own.x);
            ownYs.add(own.y);

            ConsoleUtil.log(
                    String.format(
                            java.util.Locale.ROOT,
                            "  #%d way=%d pts=%d: own=(%.2f, %.2f) px"
                                    + " = E %.2f m / N %.2f m,"
                                    + " err@own=%.2f px, err@global=%.2f px,"
                                    + " delta=(%.2f, %.2f) px",
                            index,
                            match.wayId,
                            match.geometryPoints.size(),
                            own.x,
                            own.y,
                            -own.x * metersPerPixel,
                            own.y * metersPerPixel,
                            errorOwn,
                            errorGlobal,
                            own.x - globalX,
                            own.y - globalY
                    )
            );
        }

        double medianX = median(ownXs);
        double medianY = median(ownYs);

        List<Double> deviations = new ArrayList<>();

        for (int i = 0; i < ownXs.size(); i++) {

            deviations.add(
                    Math.hypot(
                            ownXs.get(i) - medianX,
                            ownYs.get(i) - medianY
                    )
            );
        }

        ConsoleUtil.log(
                String.format(
                        java.util.Locale.ROOT,
                        "per-building optimum: median=(%.2f, %.2f) px,"
                                + " median distance to median=%.2f px (%.2f m),"
                                + " global-to-median=(%.2f, %.2f) px",
                        medianX,
                        medianY,
                        median(deviations),
                        median(deviations) * metersPerPixel,
                        globalX - medianX,
                        globalY - medianY
                )
        );
    }

    private static double median(
            List<Double> values) {

        List<Double> sorted =
                new ArrayList<>(values);

        sorted.sort(
                Double::compareTo
        );

        int n = sorted.size();

        if (n == 0) {
            return Double.NaN;
        }

        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }

        return 0.5 * (
                sorted.get(n / 2 - 1)
                        + sorted.get(n / 2)
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