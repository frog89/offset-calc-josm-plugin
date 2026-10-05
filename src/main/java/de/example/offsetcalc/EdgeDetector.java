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

import nu.pattern.OpenCV;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public final class EdgeDetector {

    private static boolean initialized;

    private EdgeDetector() {
    }

    public static synchronized void initialize() {

        if (initialized) {
            return;
        }

        OpenCV.loadLocally();

        initialized = true;
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

        Mat edges20_60 =
                new Mat();

        Mat edges30_90 =
                new Mat();

        Mat edges50_150 =
                new Mat();

        Imgproc.Canny(
                blurred,
                edges20_60,
                20,
                60
        );

        Imgproc.Canny(
                blurred,
                edges30_90,
                30,
                90
        );

        Imgproc.Canny(
                blurred,
                edges50_150,
                50,
                150
        );

        long count20_60 =
                Core.countNonZero(
                        edges20_60
                );

        long count30_90 =
                Core.countNonZero(
                        edges30_90
                );

        long count50_150 =
                Core.countNonZero(
                        edges50_150
                );

        ConsoleUtil.log(
                "Canny 20/60 edge pixels="
                        + count20_60
        );

        ConsoleUtil.log(
                "Canny 30/90 edge pixels="
                        + count30_90
        );

        ConsoleUtil.log(
                "Canny 50/150 edge pixels="
                        + count50_150
        );

        Imgcodecs.imwrite(
                "D:\\temp\\offset-canny-20-60.png",
                edges20_60
        );

        Imgcodecs.imwrite(
                "D:\\temp\\offset-canny-30-90.png",
                edges30_90
        );

        Imgcodecs.imwrite(
                "D:\\temp\\offset-canny-50-150.png",
                edges50_150
        );

        edges20_60.release();
        edges50_150.release();

        source.release();
        gray.release();
        blurred.release();

        return edges30_90;
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