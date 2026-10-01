package de.example.offsetcalc;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

import javax.swing.JMenuItem;

public class OffsetCalcPlugin extends Plugin {

    public OffsetCalcPlugin(PluginInformation info) {
        super(info);

        JMenuItem item = new JMenuItem(
                new AdjustOffsetAction()
        );

        MainApplication.getMenu()
                .imageryMenu
                .add(item);
    }
}