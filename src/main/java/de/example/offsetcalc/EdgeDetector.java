package de.example.offsetcalc;

import nu.pattern.OpenCV;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
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
                bufferedImageToMat(image);

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
                new org.opencv.core.Size(5, 5),
                1.2
        );

        Mat edges =
                new Mat();

        Imgproc.Canny(
                blurred,
                edges,
                50,
                150
        );

        java.io.File tempDirectory =
                new java.io.File("D:\\temp");

        if (!tempDirectory.exists()) {
            tempDirectory.mkdirs();
        }

        org.opencv.imgcodecs.Imgcodecs.imwrite(
                "D:\\temp\\imagery-canny.png",
                edges
        );

        source.release();
        gray.release();
        blurred.release();

        return edges;
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