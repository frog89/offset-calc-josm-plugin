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

import static de.example.offsetcalc.FilterUtil.filterOutWay;

public class OffsetCalculator {
    // To test behaviour for imagery offsets. Both offsets need to be 0.0 In production mode.
    final double TEST_OFFSET_X = 0.0;
    final double TEST_OFFSET_Y = 0.0;

    private final OsmDataLayer dataLayer;
    private final AbstractTileSourceLayer<?> imageryLayer;

    private final MapView mapView;

    public OffsetCalculator(
            OsmDataLayer dataLayer,
            AbstractTileSourceLayer<?> imageryLayer) {

        this.dataLayer = dataLayer;
        this.imageryLayer = imageryLayer;

        this.mapView =
                MainApplication.getMap().mapView;
    }

    private void logErrorMatrix(SearchResult best, Mat imageryDistance, List<Point> geometryPoints) {
        System.out.println(
                "OffsetCalc: local subpixel error matrix"
        );

        double matrixCenterX = best.x;
        double matrixCenterY = best.y;

        for (double y = matrixCenterY - 2.0;
             y <= matrixCenterY + 2.0;
             y += 0.5) {

            StringBuilder line = new StringBuilder();

            for (double x = matrixCenterX - 2.0;
                 x <= matrixCenterX + 2.0;
                 x += 0.5) {

                double error =
                        calculateError(
                                imageryDistance,
                                geometryPoints,
                                x,
                                y
                        );

                line.append(
                        String.format(
                                "%.3f ",
                                error
                        )
                );
            }

            System.out.println(line);
        }
    }

    public OffsetResult calculate() {

        if (mapView.getWidth() <= 0
                || mapView.getHeight() <= 0) {

            return OffsetResult.invalid(
                    "The map view has no usable size."
            );
        }

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

        Core.MinMaxLocResult edgeMinMax =
                Core.minMaxLoc(imageryEdges);

        System.out.println(
                "OffsetCalc: imagery edges min="
                        + edgeMinMax.minVal
                        + ", max="
                        + edgeMinMax.maxVal
        );

        int imageryEdgePixels = 0;

        for (int y = 0; y < imageryEdges.rows(); y++) {
            for (int x = 0; x < imageryEdges.cols(); x++) {
                if (imageryEdges.get(y, x)[0] > 0) {
                    imageryEdgePixels++;
                }
            }
        }

        System.out.println(
                "OffsetCalc: imagery edge pixels = "
                        + imageryEdgePixels
        );

        /*
         * 3. Render OSM geometries.
         */
        BufferedImage geometryImage =
                GeometryRasterizer.rasterize(
                        dataLayer,
                        mapView,
                        TEST_OFFSET_X,
                        TEST_OFFSET_Y
                );

        long geometryPixels = 0;

        for (int y = 0; y < geometryImage.getHeight(); y++) {
            for (int x = 0; x < geometryImage.getWidth(); x++) {
                int rgb = geometryImage.getRGB(x, y);

                if ((rgb & 0x00FFFFFF) != 0) {
                    geometryPixels++;
                }
            }
        }

        System.out.println(
                "OffsetCalc: geometry pixels = "
                        + geometryPixels
        );

        System.out.println(
                "OffsetCalc: imagery size = "
                        + imagery.getWidth()
                        + " x "
                        + imagery.getHeight()
        );

        System.out.println(
                "OffsetCalc: geometry size = "
                        + geometryImage.getWidth()
                        + " x "
                        + geometryImage.getHeight()
        );

        try {
            ImageIO.write(
                    imagery,
                    "png",
                    new File("D:\\temp\\offset-imagery.png")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            ImageIO.write(
                    geometryImage,
                    "png",
                    new File("D:\\temp\\offset-geometry.png")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        /*
         * 4. Convert imagery edges to a distance map.
         *
         * White = imagery edge
         * Black = background
         */
        Mat imageryEdgeInverse = new Mat();

        Core.bitwise_not(
                imageryEdges,
                imageryEdgeInverse
        );

        Core.MinMaxLocResult inverseMinMax =
                Core.minMaxLoc(imageryEdgeInverse);

        System.out.println(
                "OffsetCalc: imagery inverse min="
                        + inverseMinMax.minVal
                        + ", max="
                        + inverseMinMax.maxVal
        );

        Mat imageryDistance = new Mat();

        Imgproc.distanceTransform(
                imageryEdgeInverse,
                imageryDistance,
                Imgproc.DIST_L2,
                3
        );

        Core.MinMaxLocResult minMax =
                Core.minMaxLoc(imageryDistance);

        System.out.println(
                "OffsetCalc: imagery distance min="
                        + minMax.minVal
                        + ", max="
                        + minMax.maxVal
        );

        Core.MinMaxLocResult distanceMinMax =
                Core.minMaxLoc(imageryDistance);

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

        System.out.println(
                "OffsetCalc: distance samples: "
                        + imageryDistance.get(0, 0)[0]
                        + ", "
                        + imageryDistance.get(
                        imageryDistance.rows() / 2,
                        imageryDistance.cols() / 2
                )[0]
                        + ", "
                        + imageryDistance.get(
                        imageryDistance.rows() - 1,
                        imageryDistance.cols() - 1
                )[0]
        );

        long zeroCount = 0L;
        long nonZeroCount = 0L;
        for (int y = 0; y < imageryEdgeInverse.rows(); y++) {
            for (int x = 0; x < imageryEdgeInverse.cols(); x++) {

                double value =
                        imageryEdgeInverse.get(y, x)[0];

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

        /*
         * 5. Extract OSM geometry pixels.
         */
        List<Point> geometryPoints =
                extractPoints(
                        bufferedImageToGrayMat(geometryImage)
                );

        List<Point> imageryEdgePoints =
                extractPoints(imageryEdges);

        System.out.println(
                "OffsetCalc: imagery edge points = "
                        + imageryEdgePoints.size()
        );

        java.awt.Rectangle dataBounds =
                getDataLayerBounds();

        System.out.println(
                "OffsetCalc: data bounds = "
                        + dataBounds
        );

        imageryEdgePoints.removeIf(
                point -> !dataBounds.contains(
                        point.x,
                        point.y
                )
        );

        System.out.println(
                "OffsetCalc: imagery edge points in data bounds = "
                        + imageryEdgePoints.size()
        );

        Mat imageryEdgesInBounds =
                Mat.zeros(
                        imageryEdges.size(),
                        imageryEdges.type()
                );

        for (Point point : imageryEdgePoints) {

            int x =
                    (int) point.x;

            int y =
                    (int) point.y;

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
        if (geometryPoints.size() < 100) {

            release(
                    imageryEdges,
                    imageryEdgeInverse,
                    imageryDistance
            );

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
         * 6. Search for the best pixel offset.
         */
        SearchResult best =
                search(
                        imageryDistance,
                        geometryPoints
                );

        logErrorMatrix(best, imageryDistance, geometryPoints);

        System.out.println(
                "OffsetCalc: search result: x="
                        + best.x
                        + ", y="
                        + best.y
                        + ", error="
                        + best.error
        );

        System.out.println(
                "OffsetCalc: error at (0,0) = "
                        + calculateError(
                        imageryDistance,
                        geometryPoints,
                        0,
                        0
                )
        );

        double eastPerPixel = getEastPerPixel();
        double northPerPixel = getNorthPerPixel();

        double east = -best.x * eastPerPixel;
        double north = -best.y * northPerPixel;

        System.out.println(
                "OffsetCalc: eastPerPixel=" + eastPerPixel +
                ", northPerPixel=" + northPerPixel
        );

        release(
                imageryEdges,
                imageryEdgeInverse,
                imageryDistance
        );

        System.out.println(
                "OffsetCalc: east="
                        + east
                        + ", north="
                        + north
        );

        return OffsetResult.valid(
                best.x,
                best.y,
                east,
                north,
                best.error
        );
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

    private void release(Mat... mats) {

        for (Mat mat : mats) {

            if (mat != null) {
                mat.release();
            }
        }
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

    private static class SearchResult {

        final double x;
        final double y;
        final double error;

        SearchResult(
                double x,
                double y,
                double error) {

            this.x = x;
            this.y = y;
            this.error = error;
        }
    }
}