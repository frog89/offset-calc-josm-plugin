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

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AdjustOffsetAction extends AbstractAction {

    public AdjustOffsetAction() {
        super("Adjust Offset");
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (!MainApplication.isDisplayingMapView()) {
            JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    "There is currently no map view.",
                    "Adjust Offset",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        AdjustOffsetDialog dialog =
                new AdjustOffsetDialog(
                        MainApplication.getMainFrame()
                );

        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            return;
        }

        OsmDataLayer dataLayer =
                dialog.getDataLayer();

        AbstractTileSourceLayer<?> imageryLayer =
                dialog.getImageryLayer();

        if (dataLayer == null || imageryLayer == null) {
            return;
        }

        OffsetCalculationConfig config;

        try {
            config =
                    parseDebugConfig(
                            dialog.getDebugConfigJson()
                    );
        } catch (Exception ex) {

            JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    "Invalid debug configuration JSON:\n\n"
                            + ex.getMessage(),
                    "Adjust Offset",
                    JOptionPane.ERROR_MESSAGE
            );

            return;
        }

        runCalculation(
                dataLayer,
                imageryLayer,
                config
        );
    }

    private OffsetCalculationConfig parseDebugConfig(
            String json) {

        JsonObject object =
                JsonParser.parseString(json)
                        .getAsJsonObject();

        double testOffsetX =
                object.get("TestOffsetX")
                        .getAsDouble();

        double testOffsetY =
                object.get("TestOffsetY")
                        .getAsDouble();

        double buildingSearchRadiusMeters =
                object.get("BuildingSearchRadiusMeters")
                        .getAsDouble();

        double paddingMeters =
                object.get("PaddingMeters")
                        .getAsDouble();

        boolean selectedBuildingsOnly =
                object.has("SelectedBuildingsOnly")
                        && object.get("SelectedBuildingsOnly")
                        .getAsBoolean();

        if (!Double.isFinite(buildingSearchRadiusMeters)
                || buildingSearchRadiusMeters < 0) {

            throw new IllegalArgumentException(
                    "BuildingSearchRadiusMeters must be >= 0."
            );
        }

        if (!Double.isFinite(paddingMeters)
                || paddingMeters < 0) {

            throw new IllegalArgumentException(
                    "PaddingMeters must be >= 0."
            );
        }

        return new OffsetCalculationConfig(
                testOffsetX,
                testOffsetY,
                buildingSearchRadiusMeters,
                paddingMeters,
                selectedBuildingsOnly
        );
    }

    private void runCalculation(
            OsmDataLayer dataLayer,
            AbstractTileSourceLayer<?> imageryLayer,
            OffsetCalculationConfig config) {

        try {
            OffsetCalculator calculator =
                    new OffsetCalculator(
                            dataLayer,
                            imageryLayer,
                            config
                    );

            OffsetResult result =
                    calculator.calculate();

            if (!result.isValid()) {
                JOptionPane.showMessageDialog(
                        MainApplication.getMainFrame(),
                        result.getMessage(),
                        "Adjust Offset",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            calculator.apply(result);

            JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    String.format(
                            "Calculated imagery offset:%n%n"
                            + "East:  %.2f m%n"
                            + "North: %.2f m%n%n"
                            + "Pixel offset:%n"
                            + "X: %.2f px%n"
                            + "Y: %.2f px%n%n"
                            + "Error: %.3f px",
                            result.getEastOffset(),
                            result.getNorthOffset(),
                            result.getPixelX(),
                            result.getPixelY(),
                            result.getError()
                    ),
                    "Adjust Offset",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (Exception ex) {

            JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    "Could not calculate imagery offset:\n\n"
                            + ex.getMessage(),
                    "Adjust Offset",
                    JOptionPane.ERROR_MESSAGE
            );

            ex.printStackTrace();
        }
    }
}