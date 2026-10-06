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

public final class OffsetResult {

    private final boolean valid;
    private final String message;

    private final double eastOffset;
    private final double northOffset;

    private final double error;

    private final int buildingCount;

    /*
     * Pixel shifts that correspond to eastOffset / northOffset.
     * These carry the correct sign convention of the applied
     * imagery shift (east = -best.x, north = +best.y).
     */
    private final double eastPixels;
    private final double northPixels;

    private OffsetResult(
            boolean valid,
            String message,
            double eastOffset,
            double northOffset,
            double error,
            int buildingCount,
            double eastPixels,
            double northPixels) {

        this.valid = valid;
        this.message = message;

        this.eastOffset = eastOffset;
        this.northOffset = northOffset;

        this.error = error;

        this.buildingCount = buildingCount;

        this.eastPixels = eastPixels;
        this.northPixels = northPixels;
    }

    public static OffsetResult invalid(
            String message) {

        return new OffsetResult(
                false,
                message,
                0,
                0,
                Double.NaN,
                0,
                0,
                0
        );
    }

    public static OffsetResult valid(
            double eastOffset,
            double northOffset,
            double error,
            int buildingCount,
            double eastPixels,
            double northPixels) {

        return new OffsetResult(
                true,
                null,
                eastOffset,
                northOffset,
                error,
                buildingCount,
                eastPixels,
                northPixels
        );
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    public double getEastOffset() {
        return eastOffset;
    }

    public double getNorthOffset() {
        return northOffset;
    }

    public double getError() {
        return error;
    }

    public int getBuildingCount() {
        return buildingCount;
    }

    public double getEastPixels() {
        return eastPixels;
    }

    public double getNorthPixels() {
        return northPixels;
    }
}