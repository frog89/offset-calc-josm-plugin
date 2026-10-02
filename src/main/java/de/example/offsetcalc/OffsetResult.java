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

public final class OffsetResult {

    private final boolean valid;
    private final String message;

    private final double pixelX;
    private final double pixelY;

    private final double eastOffset;
    private final double northOffset;

    private final double error;

    private OffsetResult(
            boolean valid,
            String message,
            double pixelX,
            double pixelY,
            double eastOffset,
            double northOffset,
            double error) {

        this.valid = valid;
        this.message = message;

        this.pixelX = pixelX;
        this.pixelY = pixelY;

        this.eastOffset = eastOffset;
        this.northOffset = northOffset;

        this.error = error;
    }

    public static OffsetResult invalid(
            String message) {

        return new OffsetResult(
                false,
                message,
                0,
                0,
                0,
                0,
                Double.NaN
        );
    }

    public static OffsetResult valid(
            double pixelX,
            double pixelY,
            double eastOffset,
            double northOffset,
            double error) {

        return new OffsetResult(
                true,
                null,
                pixelX,
                pixelY,
                eastOffset,
                northOffset,
                error
        );
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    public double getPixelX() {
        return pixelX;
    }

    public double getPixelY() {
        return pixelY;
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
}