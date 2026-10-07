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

package de.franka.offsetcalc;

import nu.pattern.OpenCV;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EdgeDetector {

    private static boolean initialized;

    /**
     * System-independent directory for debug output.
     * Created lazily on first access.
     */
    private static Path debugDirectory;

    private EdgeDetector() {
    }

    public static synchronized void initialize() {

        if (initialized) {
            return;
        }

        OpenCV.loadLocally();

        initialized = true;
    }

    /**
     * Returns (and lazily creates) a unique temp directory
     * for debug output. The directory lives under the
     * operating system's temp folder, so it works on
     * Windows, Linux and macOS.
     */
    public static synchronized Path getDebugDirectory() {

        if (debugDirectory == null) {

            try {

                debugDirectory =
                        Files.createTempDirectory(
                                "offsetcalc-debug-"
                        );

                ConsoleUtil.log(
                        "debug directory = "
                                + debugDirectory
                );

            } catch (IOException e) {

                throw new RuntimeException(
                        "Could not create debug directory.",
                        e
                );
            }
        }

        return debugDirectory;
    }

    public static Mat detectEdges(
            BufferedImage image) {

        initialize();

        Mat source =
                bufferedImageToMat(
                        image
                );

        Mat gray =
                new Mat();

        if (source.channels() == 4) {

            Imgproc.cvtColor(
                    source,
                    gray,
                    Imgproc.COLOR_BGRA2GRAY
            );

        } else {

            Imgproc.cvtColor(
                    source,
                    gray,
                    Imgproc.COLOR_BGR2GRAY
            );
        }

        Mat blurred =
                new Mat();

        Imgproc.GaussianBlur(
                gray,
                blurred,
                new org.opencv.core.Size(
                        5,
                        5
                ),
                1.2
        );

        /*
         * Otsu-based threshold pair.
         *
         * Otsu is computed on the gradient magnitude of the
         * blurred image. The low threshold is set to a fixed
         * fraction of the Otsu value (classic Canny practice).
         */
        double otsuValue =
                computeOtsuThreshold(
                        blurred
                );

        double otsuLow =
                Math.max(
                        1.0,
                        otsuValue * 0.4
                );

        double otsuHigh =
                Math.max(
                        otsuLow + 1.0,
                        otsuValue
                );

        Mat edgesOtsu =
                new Mat();

        Imgproc.Canny(
                blurred,
                edgesOtsu,
                otsuLow,
                otsuHigh
        );

        ConsoleUtil.log(
                String.format(
                        java.util.Locale.ROOT,
                        "Canny Otsu: low=%.2f, high=%.2f, edge pixels=%d",
                        otsuLow,
                        otsuHigh,
                        Core.countNonZero(edgesOtsu)
                )
        );

        Path debugDir =
                EdgeDetector.getDebugDirectory();

        Imgcodecs.imwrite(
                debugDir.resolve(
                        "offset-canny.png"
                ).toString(),
                edgesOtsu
        );

        source.release();
        gray.release();
        blurred.release();

        return edgesOtsu;
    }

    /**
     * Otsu threshold on the gradient magnitude of the
     * given single-channel image.
     * <p>
     * Canny computes the gradient internally but does not
     * expose it, so we compute a Sobel magnitude here.
     */
    private static double computeOtsuThreshold(
            Mat gray) {

        Mat gradX =
                new Mat();

        Mat gradY =
                new Mat();

        Imgproc.Sobel(
                gray,
                gradX,
                CvType.CV_16S,
                1,
                0,
                3
        );

        Imgproc.Sobel(
                gray,
                gradY,
                CvType.CV_16S,
                0,
                1,
                3
        );

        Mat absX =
                new Mat();

        Mat absY =
                new Mat();

        Core.convertScaleAbs(
                gradX,
                absX
        );

        Core.convertScaleAbs(
                gradY,
                absY
        );

        Mat magnitude =
                new Mat();

        Core.addWeighted(
                absX,
                0.5,
                absY,
                0.5,
                0,
                magnitude
        );

        /*
         * Otsu needs an 8-bit single-channel image.
         * Normalize the magnitude to 0..255 first.
         */
        Mat normalized =
                new Mat();

        Core.normalize(
                magnitude,
                normalized,
                0,
                255,
                Core.NORM_MINMAX,
                CvType.CV_8UC1
        );

        Mat dummy =
                new Mat();

        double otsu =
                Imgproc.threshold(
                        normalized,
                        dummy,
                        0,
                        255,
                        Imgproc.THRESH_BINARY
                                | Imgproc.THRESH_OTSU
                );

        gradX.release();
        gradY.release();
        absX.release();
        absY.release();
        magnitude.release();
        normalized.release();
        dummy.release();

        return otsu;
    }

    /**
     * Median of the gradient magnitude of the given
     * single-channel image.
     */
    private static double computeMedianGradient(
            Mat gray) {

        Mat gradX =
                new Mat();

        Mat gradY =
                new Mat();

        Imgproc.Sobel(
                gray,
                gradX,
                CvType.CV_16S,
                1,
                0,
                3
        );

        Imgproc.Sobel(
                gray,
                gradY,
                CvType.CV_16S,
                0,
                1,
                3
        );

        Mat absX =
                new Mat();

        Mat absY =
                new Mat();

        Core.convertScaleAbs(
                gradX,
                absX
        );

        Core.convertScaleAbs(
                gradY,
                absY
        );

        Mat magnitude =
                new Mat();

        Core.addWeighted(
                absX,
                0.5,
                absY,
                0.5,
                0,
                magnitude
        );

        /*
         * Histogram of the magnitude values.
         */
        java.util.List<Mat> histInput =
                java.util.Collections.singletonList(
                        magnitude
                );

        Mat hist =
                new Mat();

        Imgproc.calcHist(
                histInput,
                new org.opencv.core.MatOfInt(
                        0),
                new Mat(),
                hist,
                new org.opencv.core.MatOfInt(
                        256),
                new org.opencv.core.MatOfFloat(
                        0, 256)
        );

        double totalPixels =
                magnitude.rows() * (double) magnitude.cols();

        double half =
                totalPixels / 2.0;

        double cumulative = 0.0;

        double median = 0.0;

        for (int i = 0; i < 256; i++) {

            cumulative +=
                    hist.get(i, 0)[0];

            if (cumulative >= half) {
                median = i;
                break;
            }
        }

        gradX.release();
        gradY.release();
        absX.release();
        absY.release();
        magnitude.release();
        hist.release();

        return median;
    }

    private static Mat bufferedImageToMat(
            BufferedImage image) {

        BufferedImage converted =
                new BufferedImage(
                        image.getWidth(),
                        image.getHeight(),
                        BufferedImage.TYPE_3BYTE_BGR
                );

        java.awt.Graphics2D g =
                converted.createGraphics();

        g.drawImage(
                image,
                0,
                0,
                null
        );

        g.dispose();

        byte[] pixels =
                ((DataBufferByte)
                        converted.getRaster()
                                .getDataBuffer())
                        .getData();

        Mat mat =
                new Mat(
                        image.getHeight(),
                        image.getWidth(),
                        CvType.CV_8UC3
                );

        mat.put(
                0,
                0,
                pixels
        );

        return mat;
    }
}