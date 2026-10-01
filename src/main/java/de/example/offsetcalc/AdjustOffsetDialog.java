package de.example.offsetcalc;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.util.List;

public class AdjustOffsetDialog extends JDialog {

    private final JComboBox<OsmDataLayer> dataLayerCombo;
    private final JComboBox<AbstractTileSourceLayer<?>> imageryLayerCombo;
    private final JTextArea debugConfigTextArea;

    private static final String DEFAULT_DEBUG_CONFIG =
            """
            {
              "TestOffsetX": 0.0,
              "TestOffsetY": 0.0,
              "CannyContourApproxEpsilon": 0.015,
              "CannyMaxContourCorners": 12
            }
            """;

    private boolean confirmed;

    public AdjustOffsetDialog(java.awt.Frame owner) {

        super(
                owner,
                "Adjust Offset",
                Dialog.ModalityType.APPLICATION_MODAL
        );

        List<OsmDataLayer> dataLayers =
                MainApplication.getLayerManager()
                        .getLayersOfType(OsmDataLayer.class);

        List<AbstractTileSourceLayer> imageryLayers =
                MainApplication.getLayerManager()
                        .getLayersOfType(AbstractTileSourceLayer.class);

        dataLayerCombo =
                new JComboBox<>(
                        dataLayers.toArray(
                                new OsmDataLayer[0]
                        )
                );

        imageryLayerCombo =
                new JComboBox<>(
                        imageryLayers.toArray(
                                new AbstractTileSourceLayer<?>[0]
                        )
                );

        debugConfigTextArea =
                new JTextArea(
                        DEFAULT_DEBUG_CONFIG
                );

        debugConfigTextArea.setRows(10);
        debugConfigTextArea.setColumns(50);
        debugConfigTextArea.setLineWrap(false);
        debugConfigTextArea.setTabSize(2);

        dataLayerCombo.setRenderer(
                new LayerComboBoxRenderer<>()
        );

        imageryLayerCombo.setRenderer(
                new LayerComboBoxRenderer<>()
        );

        JPanel selectionPanel =
                new JPanel(
                        new GridLayout(2, 2, 8, 8)
                );

        selectionPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        12, 12, 12, 12
                )
        );

        selectionPanel.add(
                new JLabel("Data Layer:")
        );

        selectionPanel.add(
                dataLayerCombo
        );

        selectionPanel.add(
                new JLabel("Imagery Layer:")
        );

        selectionPanel.add(
                imageryLayerCombo
        );

        JPanel debugPanel =
                new JPanel(
                        new BorderLayout(8, 8)
                );

        debugPanel.setBorder(
                BorderFactory.createTitledBorder(
                        "Debug Configuration (JSON)"
                )
        );

        debugPanel.add(
                new JScrollPane(
                        debugConfigTextArea
                ),
                BorderLayout.CENTER
        );

        boolean debugMode =
                Boolean.getBoolean(
                        "offsetcalc.debug"
                );

        debugPanel.setVisible(
                debugMode
        );

        JButton okButton =
                new JButton("OK");

        okButton.addActionListener(
                e -> {
                    confirmed = true;
                    dispose();
                }
        );

        JPanel buttonPanel =
                new JPanel();

        buttonPanel.add(okButton);

        setLayout(
                new BorderLayout()
        );

        JPanel centerPanel =
                new JPanel(
                        new BorderLayout(8, 8)
                );

        centerPanel.add(
                selectionPanel,
                BorderLayout.NORTH
        );

        if (debugMode) {
            centerPanel.add(
                    debugPanel,
                    BorderLayout.CENTER
            );
        }

        add(
                centerPanel,
                BorderLayout.CENTER
        );

        add(
                buttonPanel,
                BorderLayout.SOUTH
        );

        pack();

        setSize(
                Math.max(getWidth(), 450),
                getHeight()
        );

        setLocationRelativeTo(owner);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public OsmDataLayer getDataLayer() {
        return (OsmDataLayer)
                dataLayerCombo.getSelectedItem();
    }

    public AbstractTileSourceLayer<?> getImageryLayer() {
        return (AbstractTileSourceLayer<?>)
                imageryLayerCombo.getSelectedItem();
    }

    public String getDebugConfigJson() {
        return debugConfigTextArea.getText();
    }

    /**
     * Simple renderer that displays the layer name
     * instead of Object.toString().
     */
    private static class LayerComboBoxRenderer<T>
            extends javax.swing.DefaultListCellRenderer {

        @Override
        public java.awt.Component getListCellRendererComponent(
                javax.swing.JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
            );

            if (value instanceof org.openstreetmap.josm.gui.layer.Layer) {
                setText(
                        ((org.openstreetmap.josm.gui.layer.Layer)
                                value).getName()
                );
            }

            return this;
        }
    }
}