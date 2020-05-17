package org.omegat.gui.editor;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.data.TMXEntry;
import org.omegat.core.statistics.StatisticsInfo;
import org.omegat.gui.main.MainWindowUI;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.UIThreadsUtil;

import javax.swing.*;
import java.awt.*;
import java.math.RoundingMode;
import java.text.NumberFormat;

public class EditorEntry {
    private final EditorController edCtrl;

    public EditorEntry(EditorController edCtrl) {
        this.edCtrl = edCtrl;
    }

    /**
     * Activates the current entry (if available) by displaying source text and embedding displayed text in
     * markers.
     * <p>
     * Also moves document focus to current entry, and makes sure fuzzy info displayed if available.
     */
    public void activateEntry(IEditor.CaretPosition pos) {
        //LOGGING
        UIThreadsUtil.mustBeSwingThread();
//        ada 3 displayedEntryIndex yang dipakai di sini
        SegmentBuilder builder = edCtrl.getBuilder();
        if (exitActivateEntry(builder, pos)) return;
        setCurrentTrans(builder);
        edCtrl.setMenuEnabled();
        showStat();
        showLengthMessage();
        if (Preferences.isPreference(Preferences.EXPORT_CURRENT_SEGMENT)) {
            edCtrl.getSegmentExportImport().exportCurrentSegment(edCtrl.getCurrentEntry());
        }
        navigateEntry(pos);
        scrollForDisplayNearestSegments(pos);
        fireEvent();
    }

    /**
     * Attempt to center the active segment in the editor. When the active
     * segment is taller than the editor, the first line of the editable area
     * will be at the bottom of the editor.
     */
    private void scrollForDisplayNearestSegments(final IEditor.CaretPosition pos) {
        SwingUtilities.invokeLater(() -> {
            Rectangle rect = getSegmentBounds(displayedEntryIndex);
            if (rect != null) {
                // Expand rect vertically to fill height of viewport.
                int viewportHeight = editorUI.getViewport().getHeight();
                rect.y -= (viewportHeight - rect.height) / 2;
                rect.height = viewportHeight;
                editor.scrollRectToVisible(rect);
            }
            setCaretPosition(pos);
        });
    }

    void fireEvent() {
        // check if file was changed
        if (edCtrl.getPreviousDisplayedFileIndex() != edCtrl.getDisplayedFileIndex()) {
            edCtrl.setPreviousDisplayedFileIndex(edCtrl.getDisplayedFileIndex());
//            CBO nya 2
            CoreEvents.fireEntryNewFile(Core.getProjectFilePath(edCtrl.getDisplayedFileIndex()));
        }

        edCtrl.getEditor().autoCompleter.setVisible(false);
        edCtrl.getEditor().repaint();

        // fire event about new segment activated
        CoreEvents.fireEntryActivated(edCtrl.getCurrentEntry());
    }

    void setCurrentTrans(SegmentBuilder builder) {
        edCtrl.setPreviousTranslations(Core.getProject().getAllTranslations(edCtrl.getCurrentEntry()));
        TMXEntry currentTranslation = edCtrl.getPreviousTranslations().getCurrentTranslation();
        builder.createSegmentElement(true, currentTranslation);
        Core.getNotes().setNoteText(currentTranslation.note);

        //then add new marks
        edCtrl.getMarkerController().reprocessImmediately(builder);
        edCtrl.getEditor().resetUndoMgr();
        edCtrl.getHistory().insertNew(builder.segmentNumberInProject);
    }

    void navigateEntry(IEditor.CaretPosition pos) {
        int te = edCtrl.getEditor().getOmDocument().getTranslationEnd();
        int ts = edCtrl.getEditor().getOmDocument().getTranslationStart();
        //
        // Navigate to entry as requested.
        //
        if (pos.position != null) { // check if outside of entry
            pos.position = Math.max(0, pos.position);
            pos.position = Math.min(pos.position, te - ts);
        }
        if (pos.selectionStart != null && pos.selectionEnd != null) { // check if outside of entry
            pos.selectionStart = Math.max(0, pos.selectionStart);
            pos.selectionEnd = Math.min(pos.selectionEnd, te - ts);
            if (pos.selectionStart >= pos.selectionEnd) { // if end after start
                pos.selectionStart = null;
                pos.selectionEnd = null;
            }
        }
    }

    boolean exitActivateEntry(SegmentBuilder builder, IEditor.CaretPosition pos) {
        if (
                edCtrl.getCurrentEntry() == null ||
                        edCtrl.getEditorUI().getViewport().getView() != edCtrl.getEditor() ||
                        !Core.getProject().isProjectLoaded()
        ) {
            return true;
        } else if (!builder.hasBeenCreated()) {
            // If the builder has not been created then we are trying to jump to a
            // segment that is in the current document but not yet loaded. To avoid
            // loading large swaths of the document at once, we then re-load the
            // document centered at the destination segment.
            edCtrl.loadDocument();
            activateEntry(pos);
            return true;
        }
        return false;
    }

    /**
     * Display length of source and translation parts in the status bar.
     */
    void showLengthMessage() {
        Document3 doc = edCtrl.getEditor().getOmDocument();
        String trans = doc.extractTranslation();
        if (trans != null) {
            SourceTextEntry ste = edCtrl.getBuilder().ste;
            String lMsg = " " + ste.getSrcText().length() + "/" + trans.length() + " ";
            edCtrl.getMw().showLengthMessage(lMsg);
        }
    }

    /**
     * Calculate statistic for file, request statistic for project and display in status bar.
     */
    public void showStat() {
        int translatedInFile = 0;
        int translatedUniqueInFile = 0;
        int uniqueInFile = 0;
        boolean isUnique;
        for (SourceTextEntry ste : Core.getProjectFile(edCtrl.getDisplayedFileIndex()).entries) {
            isUnique = ste.getDuplicate() != SourceTextEntry.DUPLICATE.NEXT;
            if (isUnique) {
                uniqueInFile++;
            }
            if (Core.isTranslated(ste)) {
                translatedInFile++;
                if (isUnique) {
                    translatedUniqueInFile++;
                }
            }
        }

        showProgress(translatedInFile, translatedUniqueInFile, uniqueInFile);
    }

    void showProgress(int translatedInFile, int translatedUniqueInFile, int uniqueInFile) {
        StatisticsInfo stat = Core.getProject().getStatistics();

        final MainWindowUI.StatusBarMode progressMode =
                Preferences.getPreferenceEnumDefault(Preferences.SB_PROGRESS_MODE,
                        MainWindowUI.StatusBarMode.DEFAULT);

        if (progressMode == MainWindowUI.StatusBarMode.DEFAULT) {
            StringBuilder pMsg = new StringBuilder(1024).append(" ");
            pMsg.append(translatedInFile).append("/").append(Core.getProjectFile(edCtrl.getDisplayedFileIndex()).entries.size()).append(" (")
                    .append(stat.numberofTranslatedSegments).append("/").append(stat.numberOfUniqueSegments)
                    .append(", ").append(stat.numberOfSegmentsTotal).append(") ");
            edCtrl.getMw().showProgressMessage(pMsg.toString());
        } else {
            /*
             * Percentage mode based on idea by Yu Tang
             * http://dirtysexyquery.blogspot.tw/2013/03/omegat-custom-progress-format.html
             */
            NumberFormat nfPer = NumberFormat.getPercentInstance();
            nfPer.setRoundingMode(RoundingMode.DOWN);
            nfPer.setMaximumFractionDigits(1);

            String message = StringUtil.format(OStrings.getString("MW_PROGRESS_DEFAULT_PERCENTAGE"),
                    (translatedUniqueInFile == 0) ? "0%" : nfPer.format((double) translatedUniqueInFile / uniqueInFile),
                    uniqueInFile - translatedUniqueInFile,
                    (stat.numberofTranslatedSegments == 0) ? "0%"
                            : nfPer.format((double) stat.numberofTranslatedSegments / stat.numberOfUniqueSegments),
                    stat.numberOfUniqueSegments - stat.numberofTranslatedSegments, stat.numberOfSegmentsTotal);

            edCtrl.getMw().showProgressMessage(message);
        }
    }
}