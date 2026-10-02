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

public class OffsetCalculationConfig {

    public double testOffsetX;
    public double testOffsetY;
    public double buildingSearchRadiusMeters;

    public OffsetCalculationConfig(
            double testOffsetX,
            double testOffsetY,
            double buildingSearchRadiusMeters) {

        this.testOffsetX = testOffsetX;
        this.testOffsetY = testOffsetY;
        this.buildingSearchRadiusMeters =
                buildingSearchRadiusMeters;
    }

    @Override
    public String toString() {
        return "OffsetCalculationConfig{" +
                "testOffsetX=" + testOffsetX +
                ", testOffsetY=" + testOffsetY +
                ", buildingSearchRadiusMeters=" +
                buildingSearchRadiusMeters +
                '}';
    }
}