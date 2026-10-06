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

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Dialog;

public class OffsetProgressDialog extends JDialog {

    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public OffsetProgressDialog(java.awt.Frame owner) {

        super(
                owner,
                "Adjust Offset",
                Dialog.ModalityType.APPLICATION_MODAL
        );

        statusLabel =
                new JLabel("Starting calculation...");

        progressBar =
                new JProgressBar();

        progressBar.setIndeterminate(true);

        JPanel contentPanel =
                new JPanel(
                        new BorderLayout(8, 8)
                );

        contentPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        16, 16, 16, 16
                )
        );

        contentPanel.add(
                statusLabel,
                BorderLayout.NORTH
        );

        contentPanel.add(
                progressBar,
                BorderLayout.CENTER
        );

        setLayout(
                new BorderLayout()
        );

        add(
                contentPanel,
                BorderLayout.CENTER
        );

        setDefaultCloseOperation(
                DO_NOTHING_ON_CLOSE
        );

        setSize(360, 120);

        setLocationRelativeTo(owner);
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }
}