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
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.awt.image.BufferedImage;
import java.util.List;

public final class ConsoleUtil {

    private ConsoleUtil() {
    }

    @FunctionalInterface
    public interface ErrorFunction {
        double calculate(
                Mat distance,
                List<Point> points,
                double dx,
                double dy);
    }

    public static void log(String message) {
        System.out.println(
                "OffsetCalc: " + message
        );
    }

    public static void error(
            String message,
            Throwable throwable) {

        System.err.println(
                "OffsetCalc: " + message
        );

        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    public static void logImageryEdgeStatistics(
            Mat imageryEdges) {

        Core.MinMaxLocResult edgeMinMax =
                Core.minMaxLoc(
                        imageryEdges
                );

        log(
                "imagery edges min="
                        + edgeMinMax.minVal
                        + ", max="
                        + edgeMinMax.maxVal
        );

        int imageryEdgePixels = 0;

        for (int y = 0;
             y < imageryEdges.rows();
             y++) {

            for (int x = 0;
                 x < imageryEdges.cols();
                 x++) {

                if (imageryEdges.get(y, x)[0] > 0) {
                    imageryEdgePixels++;
                }
            }
        }

        log(
                "imagery edge pixels = "
                        + imageryEdgePixels
        );
    }

    public static void logGeometryStatistics(
            BufferedImage imagery,
            BufferedImage geometryImage) {

        long geometryPixels = 0;

        for (int y = 0;
             y < geometryImage.getHeight();
             y++) {

            for (int x = 0;
                 x < geometryImage.getWidth();
                 x++) {

                int rgb =
                        geometryImage.getRGB(
                                x,
                                y
                        );

                if ((rgb & 0x00FFFFFF) != 0) {
                    geometryPixels++;
                }
            }
        }

        log(
                "geometry pixels = "
                        + geometryPixels
        );

        log(
                "imagery size = "
                        + imagery.getWidth()
                        + " x "
                        + imagery.getHeight()
        );

        log(
                "geometry size = "
                        + geometryImage.getWidth()
                        + " x "
                        + geometryImage.getHeight()
        );
    }

    public static void logSearchResult(
            SearchResult best,
            Mat imageryDistance,
            List<Point> geometryPoints,
            ErrorCalculator errorCalculator) {

        logErrorMatrix(
                best,
                imageryDistance,
                geometryPoints,
                errorCalculator
        );

        log(
                "search result: x="
                        + best.x
                        + ", y="
                        + best.y
                        + ", error="
                        + best.error
        );

        log(
                "error at (0,0) = "
                        + errorCalculator.calculateError(
                        imageryDistance,
                        geometryPoints,
                        0,
                        0
                )
        );
    }

    public static void logErrorMatrix(
            double centerX,
            double centerY,
            Mat imageryDistance,
            List<Point> geometryPoints,
            ErrorFunction errorFunction) {

        log(
                "local subpixel error matrix"
        );

        for (double y = centerY - 2.0;
             y <= centerY + 2.0;
             y += 0.5) {

            StringBuilder line =
                    new StringBuilder();

            for (double x = centerX - 2.0;
                 x <= centerX + 2.0;
                 x += 0.5) {

                double error =
                        errorFunction.calculate(
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

            log(
                    line.toString()
            );
        }
    }

    public static void logErrorMatrix(
            SearchResult best,
            Mat imageryDistance,
            List<Point> geometryPoints,
            ErrorCalculator errorCalculator) {

        log(
                "local subpixel error matrix"
        );

        double matrixCenterX =
                best.x;

        double matrixCenterY =
                best.y;

        for (double y = matrixCenterY - 2.0;
             y <= matrixCenterY + 2.0;
             y += 0.5) {

            StringBuilder line =
                    new StringBuilder();

            for (double x = matrixCenterX - 2.0;
                 x <= matrixCenterX + 2.0;
                 x += 0.5) {

                double error =
                        errorCalculator.calculateError(
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

            log(
                    line.toString()
            );
        }
    }

    public interface ErrorCalculator {

        double calculateError(
                Mat distance,
                List<Point> points,
                double dx,
                double dy);
    }
}