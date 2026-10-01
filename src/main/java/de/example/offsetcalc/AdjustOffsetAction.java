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

        double cannyContourApproxEpsilon =
                object.get(
                        "CannyContourApproxEpsilon"
                ).getAsDouble();

        int cannyMaxContourCorners =
                object.get(
                        "CannyMaxContourCorners"
                ).getAsInt();

        if (cannyContourApproxEpsilon <= 0) {
            throw new IllegalArgumentException(
                    "CannyContourApproxEpsilon must be > 0."
            );
        }

        if (cannyMaxContourCorners < 3) {
            throw new IllegalArgumentException(
                    "CannyMaxContourCorners must be >= 3."
            );
        }

        return new OffsetCalculationConfig(
                testOffsetX,
                testOffsetY,
                cannyContourApproxEpsilon,
                cannyMaxContourCorners
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