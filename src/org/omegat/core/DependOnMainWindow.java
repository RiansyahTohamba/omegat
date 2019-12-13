package org.omegat.core;

import org.omegat.gui.comments.CommentsTextArea;
import org.omegat.gui.dictionaries.DictionariesTextArea;
import org.omegat.gui.editor.EditorController;
import org.omegat.gui.exttrans.MachineTranslateTextArea;
import org.omegat.gui.glossary.GlossaryManager;
import org.omegat.gui.glossary.GlossaryTextArea;
import org.omegat.gui.issues.IIssues;
import org.omegat.gui.issues.IssuesPanelController;
import org.omegat.gui.main.MainWindow;
import org.omegat.gui.matches.MatchesTextArea;
import org.omegat.gui.multtrans.MultipleTransPane;
import org.omegat.gui.notes.NotesTextArea;
import org.omegat.gui.properties.SegmentPropertiesArea;

public final class DependOnMainWindow {
    private static MachineTranslateTextArea machineTranslatePane;
    private static IIssues issuesWindow;

    static void dependOnMainWindow() {
        /*        TODO: how to separate these objects?  */
        MainWindow me = new MainWindow();
        Core.mainWindow = me;

        Core.editor = new EditorController(me);
        issuesWindow = new IssuesPanelController(me);
        Core.matcher = new MatchesTextArea(me);
        Core.notes = new NotesTextArea(me);
        Core.comments = new CommentsTextArea(me);
        machineTranslatePane = new MachineTranslateTextArea(me);
        Core.dictionaries = new DictionariesTextArea(me);
        Core.multiple = new MultipleTransPane(me);

        GlossaryTextArea glossaryArea = new GlossaryTextArea(me);
        Core.glossary = glossaryArea;
        Core.glossaryManager = new GlossaryManager(glossaryArea);

        new SegmentPropertiesArea(me);
    }
    public static MachineTranslateTextArea getMachineTranslatePane() {
        return machineTranslatePane;
    }
    public static IIssues getIssues() {
//        mainmenwindowhandler dan editorController
        return issuesWindow;
    }
}