package org.omegat.gui.editor;

import org.omegat.core.Core;
import org.omegat.core.data.IProject;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.gui.main.DockablePanel;
import org.omegat.util.gui.UIThreadsUtil;

import java.awt.*;
import java.util.List;

public class EditorFilter implements IFilterForEditor {

    /** Dockable pane for editor. */
    private DockablePanel pane;

    public IEditorFilter getFilter() {
        return entriesFilter;
    }

    /**
     * instance variable, TODO: nanti lihat instance variable ini dipakai dimana saja sehingga bisa dibuatkan subsetnya
     * entriesFilterControlComponent
     * pane
     * entriesFilter
     *
     * {@inheritDoc} Document is reloaded to immediately have the filter being
     * effective.
     */
    public void setFilter(IEditorFilter filter) {
        UIThreadsUtil.mustBeSwingThread();

        if (entriesFilterControlComponent != null) {
            pane.remove(entriesFilterControlComponent);
        }

        entriesFilter = filter;
        entriesFilterControlComponent = filter.getControlComponent();
        pane.add(entriesFilterControlComponent, BorderLayout.NORTH);
        pane.revalidate();

        preventNullLoad();
    }

    private void preventNullLoad() {
        SourceTextEntry curEntry = getCurrentEntry();
        Document3 doc = editor.getOmDocument();
        IProject project = Core.getProject();
        // Prevent NullPointerErrors in loadDocument. Only load if there is a document.
        if (doc != null && project != null && project.getProjectFiles() != null && curEntry != null) {
            int curEntryNum = curEntry.entryNum();
            loadDocument(); // rebuild entrylist
            if (entriesFilter == null || entriesFilter.allowed(curEntry)) {
                gotoEntry(curEntry.entryNum());
            } else {
                nextSegmentEntry(curEntryNum);
            }
        }
    }

    private void nextSegmentEntry(int curEntryNum) {
        // Go to next (available) segment. But first, we need to reset
        // the displayedEntryIndex to the number where the current but
        // filtered entry could have been if it was not filtered.
        for (int j = 0; j < m_docSegList.length; j++) {
            if (m_docSegList[j].segmentNumberInProject >= curEntryNum) { //
                displayedEntryIndex = j - 1;
                break;
            }
        }
        nextEntry();
    }

    /**
     * entriesFilter
     * entriesFilterControlComponent
     * pane
     * {@inheritDoc} Document is reloaded if appropriate to immediately remove
     * the filter;
     */
    public void removeFilter() {
        UIThreadsUtil.mustBeSwingThread();

        if (entriesFilter == null && entriesFilterControlComponent == null) {
            return;
        }

        entriesFilter = null;
        if (entriesFilterControlComponent != null) {
            pane.remove(entriesFilterControlComponent);
            pane.revalidate();
            entriesFilterControlComponent = null;
        }

        int curEntryNum = getCurrentEntryNumber();
        Document3 doc = editor.getOmDocument();
        IProject project = Core.getProject();
        // `if` check is to prevent NullPointerErrors in loadDocument.
        // Only load if there is a document and the project is loaded.
        if (doc != null && project != null && project.isProjectLoaded()) {
            List<IProject.FileInfo> files = project.getProjectFiles();
            if (files != null && !files.isEmpty()) {
                loadDocument();
                gotoEntry(curEntryNum);
            }
        }
    }

}
