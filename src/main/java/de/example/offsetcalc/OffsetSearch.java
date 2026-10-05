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

import java.util.function.DoubleBinaryOperator;

/**
 * Coarse-to-fine grid search for the (x, y) pixel shift that
 * minimizes an error function.
 *
 * Pass 1: +/- 100 px in steps of 4 px
 * Pass 2: +/-   4 px in steps of 0.5 px (covers the half step of pass 1)
 * Pass 3: +/- 0.5 px in steps of 0.1 px
 */
public final class OffsetSearch {

    private static final double COARSE_RANGE = 100.0;
    private static final double COARSE_STEP = 4.0;

    private static final double MEDIUM_RANGE = 4.0;
    private static final double MEDIUM_STEP = 0.5;

    private static final double FINE_RANGE = 0.5;
    private static final double FINE_STEP = 0.1;

    private OffsetSearch() {
    }

    public static SearchResult minimize(
            DoubleBinaryOperator error) {

        SearchResult best =
                new SearchResult(0, 0, Double.MAX_VALUE);

        best = scan(error, 0, 0, COARSE_RANGE, COARSE_STEP, best);

        best = scan(error, best.x, best.y,
                MEDIUM_RANGE, MEDIUM_STEP, best);

        best = scan(error, best.x, best.y,
                FINE_RANGE, FINE_STEP, best);

        return best;
    }

    private static SearchResult scan(
            DoubleBinaryOperator error,
            double centerX,
            double centerY,
            double range,
            double step,
            SearchResult start) {

        SearchResult best = start;

        int n = (int) Math.round(range / step);

        for (int iy = -n; iy <= n; iy++) {

            double y = centerY + iy * step;

            for (int ix = -n; ix <= n; ix++) {

                double x = centerX + ix * step;

                double e = error.applyAsDouble(x, y);

                if (e < best.error) {
                    best = new SearchResult(x, y, e);
                }
            }
        }

        return best;
    }
}
