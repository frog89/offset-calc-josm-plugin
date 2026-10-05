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

    private final double testOffsetX;
    private final double testOffsetY;
    private final double buildingSearchRadiusMeters;
    private final double paddingMeters;
    private final boolean selectedBuildingsOnly;

    public OffsetCalculationConfig(
            double testOffsetX,
            double testOffsetY,
            double buildingSearchRadiusMeters,
            double paddingMeters,
            boolean selectedBuildingsOnly) {

        this.testOffsetX = testOffsetX;
        this.testOffsetY = testOffsetY;
        this.buildingSearchRadiusMeters =
                buildingSearchRadiusMeters;
        this.paddingMeters =
                paddingMeters;
        this.selectedBuildingsOnly =
                selectedBuildingsOnly;
    }

    public double getTestOffsetX() {
        return testOffsetX;
    }

    public double getTestOffsetY() {
        return testOffsetY;
    }

    public double getBuildingSearchRadiusMeters() {
        return buildingSearchRadiusMeters;
    }

    public double getPaddingMeters() {
        return paddingMeters;
    }

    public boolean isSelectedBuildingsOnly() {
        return selectedBuildingsOnly;
    }

    @Override
    public String toString() {

        return "OffsetCalculationConfig{"
                + "testOffsetX="
                + testOffsetX
                + ", testOffsetY="
                + testOffsetY
                + ", buildingSearchRadiusMeters="
                + buildingSearchRadiusMeters
                + ", paddingMeters="
                + paddingMeters
                + ", selectedBuildingsOnly="
                + selectedBuildingsOnly
                + '}';
    }
}
