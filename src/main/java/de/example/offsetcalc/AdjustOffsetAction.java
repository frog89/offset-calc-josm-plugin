package de.example.offsetcalc;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;

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

        OsmDataLayer dataLayer = dialog.getDataLayer();

        AbstractTileSourceLayer<?> imageryLayer =
                dialog.getImageryLayer();

        if (dataLayer == null || imageryLayer == null) {
            return;
        }

        runCalculation(dataLayer, imageryLayer);
    }

    private void runCalculation(
            OsmDataLayer dataLayer,
            AbstractTileSourceLayer<?> imageryLayer) {

        try {
            OffsetCalculator calculator =
                    new OffsetCalculator(
                            dataLayer,
                            imageryLayer
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