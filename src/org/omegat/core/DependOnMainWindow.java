package org.omegat.core;

import org.omegat.gui.comments.IComments;
import org.omegat.gui.dictionaries.IDictionaries;
import org.omegat.gui.editor.IEditor;
import org.omegat.gui.comments.CommentsTextArea;
import org.omegat.gui.dictionaries.DictionariesTextArea;
import org.omegat.gui.editor.EditorController;
import org.omegat.gui.exttrans.MachineTranslateTextArea;
import org.omegat.gui.glossary.GlossaryManager;
import org.omegat.gui.glossary.GlossaryTextArea;
import org.omegat.gui.glossary.IGlossaries;
import org.omegat.gui.issues.IIssues;
import org.omegat.gui.issues.IssuesPanelController;
import org.omegat.gui.main.MainWindow;
import org.omegat.gui.matches.IMatcher;
import org.omegat.gui.matches.MatchesTextArea;
import org.omegat.gui.multtrans.MultipleTransPane;
import org.omegat.gui.notes.INotes;
import org.omegat.gui.notes.NotesTextArea;
import org.omegat.gui.properties.SegmentPropertiesArea;

public final class DependOnMainWindow {
    protected static IGlossaries glossary;
    private static MachineTranslateTextArea machineTranslatePane;
    private static IIssues issuesWindow;
    // protected buat apa kalau ga bisa di extend?wkwk
    protected static IEditor editor;
    private static IMatcher matcher;
    private static DictionariesTextArea dictionaries;
    private static INotes notes;
    private static IComments comments;
    @SuppressWarnings("unused")
    private static MultipleTransPane multiple;
    private static GlossaryManager glossaryManager;

    static void dependOnMainWindow(MainWindow me) {
        
        editor = new EditorController(me);

        issuesWindow = new IssuesPanelController(me);
        matcher = new MatchesTextArea(me);
        notes = new NotesTextArea(me);
        comments = new CommentsTextArea(me);
        machineTranslatePane = new MachineTranslateTextArea(me);
        dictionaries = new DictionariesTextArea(me);
        multiple = new MultipleTransPane(me);

        GlossaryTextArea glossaryArea = new GlossaryTextArea(me);
        glossary = glossaryArea;
        glossaryManager = new GlossaryManager(glossaryArea);

        new SegmentPropertiesArea(me);
    }

    /** Get editor instance. */
    public static IEditor getEditor() {
        return editor;
    }    

    public static MachineTranslateTextArea getMachineTranslatePane() {
        return machineTranslatePane;
    }
    public static IIssues getIssues() {
//        mainmenwindowhandler dan editorController
        return issuesWindow;
    }

    /** Get matcher component instance. */
    public static IMatcher getMatcher() {
        return matcher;
    }

    /** Get notes instance. */
    public static INotes getNotes() {
        return notes;
    }

    /**
     * Get comments area
     *
     * @return the comment area
     */
    public static IComments getComments() {
        return comments;
    }

    public static IDictionaries getDictionaries() {
        return dictionaries;
    }

    /** Get glossary instance. */
    public static IGlossaries getGlossary() {
        return glossary;
    }

    public static GlossaryManager getGlossaryManager() {
        return glossaryManager;
    }
}