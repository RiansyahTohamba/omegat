package org.omegat.gui.editor;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.data.IProject;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.statistics.StatisticsInfo;
import org.omegat.gui.main.MainWindow;
import org.omegat.gui.main.MainWindowUI;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.UIThreadsUtil;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;

public class EditorEntry {
    /** Current displayed file. */
    protected int previousDisplayedFileIndex;
    private final EditorController edCtrl;
    //    displayedFileIndex 22 matches
    protected int displayedFileIndex;

    /**
     * Current active segment in current file, if there are segments in file (can be fale if filter active!)
     */
    protected int displayedEntryIndex;
    protected final EditorTextArea3 editor;
    protected final MainWindow mw;

    public EditorEntry(EditorController edCtrl, EditorTextArea3 edt, MainWindow mw) {
        this.editor = edt;
        this.edCtrl = edCtrl;
        this.mw = mw;
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
        edCtrl.setCurrentTrans(builder);
        edCtrl.setMenuEnabled();
        showStat();
        showLengthMessage();
        edCtrl.checkPrefExport();
        navigateEntry(pos);
        edCtrl.scrollForDisplayNearestSegments(pos,displayedEntryIndex);
        fireEvent();
    }
    public boolean iterateNonForward(java.util.List<IProject.FileInfo> files, boolean looped) {
        displayedEntryIndex--;
        if (displayedEntryIndex < 0) {
            displayedFileIndex--;
            if (displayedFileIndex < 0) {
                displayedFileIndex = files.size() - 1;
                looped = true;
            }
            edCtrl.loadDocument();
            displayedEntryIndex = edCtrl.getM_docSegList().length - 1;
        }
        return looped;
    }

    public void setDisplayedFileIndex(int displayedFileIndex) {
        this.displayedFileIndex = displayedFileIndex;
    }

    public int getDisplayedFileIndex() {
        return displayedFileIndex;
    }

    public int getDisplayedEntryIndex() {
        return displayedEntryIndex;
    }

    public void setDisplayedEntryIndex(int displayedEntryIndex) {
        this.displayedEntryIndex = displayedEntryIndex;
    }

    public boolean iterateForward(List<IProject.FileInfo> files, boolean looped) {
        displayedEntryIndex++;
        if (displayedEntryIndex >= edCtrl.getM_docSegList().length) {
            displayedFileIndex++;
            displayedEntryIndex = 0;
            if (displayedFileIndex >= files.size()) {
                displayedFileIndex = 0;
                looped = true;
            }
            edCtrl.loadDocument();
        }
        return looped;
    }
    public void displayFirstEntry() {
        // it was empty project, need to display first entry
        displayedFileIndex = 0;
        displayedEntryIndex = 0;
        edCtrl.loadDocument();
    }

    public void findIndex(int entryNum) {
        IProject dataEngine = Core.getProject();
        for (int i = 0; i < dataEngine.getProjectFiles().size(); i++) {
            IProject.FileInfo fi = dataEngine.getProjectFiles().get(i);
            SourceTextEntry firstEntry = fi.entries.get(0);
            SourceTextEntry lastEntry = fi.entries.get(fi.entries.size() - 1);
            if (firstEntry.entryNum() <= entryNum && lastEntry.entryNum() >= entryNum) {
                // this file
                if (i != displayedFileIndex) {
                    // it's other file than displayed
                    displayedFileIndex = i;
                    edCtrl.loadDocument();
                }
                findCorrectDisplayIndex(entryNum);
                break;
            }
        }
    }

    void fireEvent() {
        // check if file was changed
        if (previousDisplayedFileIndex != displayedFileIndex) {
            previousDisplayedFileIndex = displayedFileIndex;
            CoreEvents.fireEntryNewFile(Core.getProjectFilePath(displayedFileIndex));
        }
        editor.autoCompleter.setVisible(false);
        editor.repaint();
        // fire event about new segment activated
        CoreEvents.fireEntryActivated(edCtrl.getCurrentEntry());
    }
    private void findCorrectDisplayIndex(int entryNum) {
        // find correct displayedEntryIndex
        for (int j = 0; j < edCtrl.getM_docSegList().length; j++) {
            if (edCtrl.getM_docSegList()[j].segmentNumberInProject >= entryNum) { //
                displayedEntryIndex = j;
                break;
            }
        }
    }

    void navigateEntry(IEditor.CaretPosition pos) {
        int te = editor.getOmDocument().getTranslationEnd();
        int ts = editor.getOmDocument().getTranslationStart();
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
                        edCtrl.getScrollPane().getViewport().getView() != editor ||
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
        Document3 doc = editor.getOmDocument();
        String trans = doc.extractTranslation();
        if (trans != null) {
            SourceTextEntry ste = edCtrl.getBuilder().ste;
            String lMsg = " " + ste.getSrcText().length() + "/" + trans.length() + " ";
            mw.showLengthMessage(lMsg);
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
        for (SourceTextEntry ste : Core.getProjectFile(displayedFileIndex).entries) {
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
            int sizeEntries = Core.getProjectFile(displayedFileIndex).entries.size();
            pMsg.append(translatedInFile).append("/")
                    .append(sizeEntries)
                    .append(" (").append(stat.numberofTranslatedSegments)
                    .append("/").append(stat.numberOfUniqueSegments)
                    .append(", ").append(stat.numberOfSegmentsTotal)
                    .append(") ");
            mw.showProgressMessage(pMsg.toString());
        } else {
            /*
             * Percentage mode based on idea by Yu Tang
             * http://dirtysexyquery.blogspot.tw/2013/03/omegat-custom-progress-format.html
             */
            NumberFormat nfPer = NumberFormat.getPercentInstance();
            nfPer.setRoundingMode(RoundingMode.DOWN);
            nfPer.setMaximumFractionDigits(1);
            String arga = (translatedUniqueInFile == 0) ? "0%" : nfPer.format((double) translatedUniqueInFile / uniqueInFile);
            String argb = (stat.numberofTranslatedSegments == 0) ? "0%" : nfPer.format((double) stat.numberofTranslatedSegments / stat.numberOfUniqueSegments);
            int diffUnique = uniqueInFile - translatedUniqueInFile;
            int diffSegments = stat.numberOfUniqueSegments - stat.numberofTranslatedSegments;

            String message = StringUtil.format(
                    OStrings.getString("MW_PROGRESS_DEFAULT_PERCENTAGE"),
                    arga,diffUnique,argb,diffSegments,stat.numberOfSegmentsTotal);
            mw.showProgressMessage(message);
        }
    }
}