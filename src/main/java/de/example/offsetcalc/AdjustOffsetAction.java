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

import javax.swing.*;
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
                            dialog.getDebugConfigJson(),
                            dialog.isSelectedBuildingsOnly()
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
            String json,
            boolean selectedBuildingsOnly) {

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

        boolean applyResult =
                object.has("ApplyResult")
                        && object.get("ApplyResult")
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
                selectedBuildingsOnly,
                applyResult
        );
    }

    private void runCalculation(
            OsmDataLayer dataLayer,
            AbstractTileSourceLayer<?> imageryLayer,
            OffsetCalculationConfig config) {

        OffsetProgressDialog progressDialog =
                new OffsetProgressDialog(
                        MainApplication.getMainFrame()
                );

        SwingWorker<OffsetResult, String> worker =
                new SwingWorker<>() {

                    @Override
                    protected OffsetResult doInBackground() {

                        OffsetCalculator calculator =
                                new OffsetCalculator(
                                        dataLayer,
                                        imageryLayer,
                                        config,
                                        this::publish
                                );

                        return calculator.calculate();
                    }

                    @Override
                    protected void process(
                            java.util.List<String> chunks) {

                        if (!chunks.isEmpty()) {
                            progressDialog.setStatus(
                                    chunks.get(
                                            chunks.size() - 1
                                    )
                            );
                        }
                    }

                    @Override
                    protected void done() {

                        progressDialog.dispose();

                        try {

                            OffsetResult result =
                                    get();

                            if (!result.isValid()) {

                                JOptionPane.showMessageDialog(
                                        MainApplication.getMainFrame(),
                                        result.getMessage(),
                                        "Adjust Offset",
                                        JOptionPane.WARNING_MESSAGE
                                );

                                return;
                            }

                            if (config.isApplyResult()) {

                                OffsetCalculator calculator =
                                        new OffsetCalculator(
                                                dataLayer,
                                                imageryLayer,
                                                config,
                                                this::publish
                                        );

                                calculator.applyResult(result);

                            } else {

                                ConsoleUtil.log(
                                        "Apply Result is disabled !!!"
                                );
                            }

                            JOptionPane.showMessageDialog(
                                    MainApplication.getMainFrame(),
                                    String.format(
                                            java.util.Locale.ROOT,
                                            "Calculated imagery offset for %d buildings:%n%n"
                                                    + "East:  %.2f Meter (= %.2f Pixel)%n"
                                                    + "North: %.2f Meter (= %.2f Pixel)%n%n"
                                                    + "Error: %.3f",
                                            result.getBuildingCount(),
                                            result.getEastOffset(),
                                            result.getEastPixels(),
                                            result.getNorthOffset(),
                                            result.getNorthPixels(),
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
                };

        worker.execute();

        progressDialog.setVisible(true);
    }
}