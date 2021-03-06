package org.jdownloader.gui.views.downloads;

import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

import jd.gui.swing.jdgui.components.toolbar.actions.PauseDownloadsAction;
import jd.gui.swing.jdgui.menu.ChunksEditor;
import jd.gui.swing.jdgui.menu.ParalellDownloadsEditor;
import jd.gui.swing.jdgui.menu.ParallelDownloadsPerHostEditor;
import jd.gui.swing.jdgui.menu.SpeedlimitEditor;

import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.exttable.ExtCheckBoxMenuItem;
import org.jdownloader.settings.staticreferences.CFG_GUI;

public class QuickSettingsPopup extends JPopupMenu {
    private GenericConfigEventListener<Boolean> list;

    public QuickSettingsPopup() {
        super();
        add(new ChunksEditor());
        add(new ParalellDownloadsEditor());
        add(new ParallelDownloadsPerHostEditor());
        add(new SpeedlimitEditor());
        PauseDownloadsAction pdl = new PauseDownloadsAction();
        pdl.requestUpdate(this);
        add(new ExtCheckBoxMenuItem(pdl));
        this.list = new GenericConfigEventListener<Boolean>() {

            @Override
            public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                superSetVisible(false);
            }

            @Override
            public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
            }
        };
        CFG_GUI.DOWNLOAD_TAB_OVERVIEW_VISIBLE.getEventSender().addListener(list, true);

        add(new JSeparator());
        add(new MenuManagerMainToolbarAction());

        add(new ExtCheckBoxMenuItem(new SpeedlimitToggleAction()));
    }

    public void superSetVisible(boolean b) {
        super.setVisible(b);
    }
}
