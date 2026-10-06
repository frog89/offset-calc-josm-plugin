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

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import javax.swing.*;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.util.List;

public class AdjustOffsetDialog extends JDialog {
    private static final String HELP_TEXT =
            """
            Adjust Offset - How it works
    
            This plugin automatically calculates an offset between
            an OSM data layer and an imagery layer.
    
            Prerequisites:
            - A data layer with building outlines (tag "building",
              closed ways) must be loaded.
            - An imagery layer (e.g. DOP or satellite imagery) must
              be loaded.
    
            Steps:
            1. Select a data layer and an imagery layer.
            2. Optionally enable "Selected buildings only" to use
               only the currently selected buildings.
            3. Press "OK" to start the calculation.
    
            The calculation:
            - Edges are extracted from the imagery crop
              (Canny edge detection).
            - Building outlines from the data layer are rasterized.
            - The plugin searches for the X/Y shift at which the
              imagery edges and the building edges match best.
            - The resulting shift is converted into the current
              JOSM projection and applied as an imagery offset.
    
            Notes:
            - The calculation requires a visible map view with
              loaded buildings.
            - A very small area or missing building edges can
              degrade the result.
            """;

    private final JComboBox<OsmDataLayer> dataLayerCombo;
    private final JComboBox<AbstractTileSourceLayer<?>> imageryLayerCombo;
    private final JTextArea debugConfigTextArea;
    private final JCheckBox selectedBuildingsOnlyCheckBox;

    private static String lastDebugConfigJson;
    private static boolean lastSelectedBuildingsOnly = false;

    private static final String DEFAULT_DEBUG_CONFIG =
            """
            {
              "TestOffsetX": 0.0,
              "TestOffsetY": 0.0,
              "BuildingSearchRadiusMeters": 20.0,
              "PaddingMeters": 40.0,
              "ApplyResult": true
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
                        lastDebugConfigJson != null
                                ? lastDebugConfigJson
                                : DEFAULT_DEBUG_CONFIG
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

        /*
         * Selection panel: data layer, imagery layer, checkbox.
         */
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

        selectedBuildingsOnlyCheckBox =
                new JCheckBox(
                        "Selected buildings only",
                        lastSelectedBuildingsOnly
                );

        JPanel optionsPanel =
                new JPanel(
                        new java.awt.FlowLayout(
                                java.awt.FlowLayout.LEFT,
                                0,
                                0
                        )
                );

        optionsPanel.add(
                selectedBuildingsOnlyCheckBox
        );

        JPanel northPanel =
                new JPanel(
                        new BorderLayout(0, 4)
                );

        northPanel.add(
                selectionPanel,
                BorderLayout.NORTH
        );

        northPanel.add(
                optionsPanel,
                BorderLayout.SOUTH
        );

        /*
         * Debug panel: JSON configuration (debug mode only).
         */
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

        /*
         * Buttons: OK, Cancel (centered) and "?" (right).
         */
        JButton okButton =
                new JButton("OK");

        okButton.addActionListener(
                e -> {
                    lastDebugConfigJson =
                            debugConfigTextArea.getText();
                    lastSelectedBuildingsOnly =
                            selectedBuildingsOnlyCheckBox.isSelected();
                    confirmed = true;
                    dispose();
                }
        );

        JButton cancelButton =
                new JButton("Cancel");

        cancelButton.addActionListener(
                e -> {
                    confirmed = false;
                    dispose();
                }
        );

        JButton helpButton =
                new JButton("?");

        helpButton.setMargin(
                new java.awt.Insets(2, 6, 2, 6)
        );

        helpButton.setToolTipText(
                "Show help"
        );

        helpButton.addActionListener(
                e -> JOptionPane.showMessageDialog(
                        AdjustOffsetDialog.this,
                        HELP_TEXT,
                        "Adjust Offset - Help",
                        JOptionPane.INFORMATION_MESSAGE
                )
        );

        JPanel actionButtonPanel =
                new JPanel(
                        new java.awt.FlowLayout(
                                java.awt.FlowLayout.CENTER,
                                8,
                                0
                        )
                );

        actionButtonPanel.add(okButton);
        actionButtonPanel.add(cancelButton);

        JPanel helpButtonPanel =
                new JPanel(
                        new java.awt.FlowLayout(
                                java.awt.FlowLayout.RIGHT,
                                0,
                                0
                        )
                );

        helpButtonPanel.add(helpButton);

        JPanel buttonPanel =
                new JPanel(
                        new BorderLayout()
                );

        buttonPanel.add(
                actionButtonPanel,
                BorderLayout.CENTER
        );

        buttonPanel.add(
                helpButtonPanel,
                BorderLayout.EAST
        );

        /*
         * Layout assembly.
         */
        setLayout(
                new BorderLayout()
        );

        JPanel centerPanel =
                new JPanel(
                        new BorderLayout(8, 8)
                );

        centerPanel.add(
                northPanel,
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

        /*
         * ENTER triggers OK, ESC triggers Cancel.
         */
        getRootPane().setDefaultButton(
                okButton
        );

        getRootPane().registerKeyboardAction(
                e -> {
                    confirmed = false;
                    dispose();
                },
                javax.swing.KeyStroke.getKeyStroke(
                        java.awt.event.KeyEvent.VK_ESCAPE,
                        0
                ),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
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

    public boolean isSelectedBuildingsOnly() {
        return selectedBuildingsOnlyCheckBox.isSelected();
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