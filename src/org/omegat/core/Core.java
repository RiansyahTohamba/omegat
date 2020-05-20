/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2008 Alex Buloichik
               2010 Wildrich Fourie
               Home page: http://www.omegat.org/
               Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.omegat.core.data.*;
import org.omegat.core.segmentation.Segmenter;
import org.omegat.core.spellchecker.ISpellChecker;
import org.omegat.core.spellchecker.SpellChecker;
import org.omegat.core.tagvalidation.ITagValidation;
import org.omegat.core.tagvalidation.TagValidationTool;
import org.omegat.core.threads.IAutoSave;
import org.omegat.core.threads.SaveThread;
import org.omegat.core.threads.VersionCheckThread;
import org.omegat.filters2.IFilter;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.filters2.master.PluginUtils;
import org.omegat.gui.comments.CommentsTextArea;
import org.omegat.gui.comments.IComments;
import org.omegat.gui.dictionaries.DictionariesTextArea;
import org.omegat.gui.dictionaries.IDictionaries;
import org.omegat.gui.editor.EditorController;
import org.omegat.gui.editor.IEditor;
import org.omegat.gui.editor.IEditorFilter;
import org.omegat.gui.editor.filter.ReplaceFilter;
import org.omegat.gui.editor.mark.BidiMarkerFactory;
import org.omegat.gui.editor.mark.ComesFromAutoTMMarker;
import org.omegat.gui.editor.mark.FontFallbackMarker;
import org.omegat.gui.editor.mark.IMarker;
import org.omegat.gui.editor.mark.NBSPMarker;
import org.omegat.gui.editor.mark.ProtectedPartsMarker;
import org.omegat.gui.editor.mark.RemoveTagMarker;
import org.omegat.gui.editor.mark.ReplaceMarker;
import org.omegat.gui.editor.mark.WhitespaceMarkerFactory;
import org.omegat.gui.exttrans.IMachineTranslation;
import org.omegat.gui.exttrans.MachineTranslateTextArea;
import org.omegat.gui.glossary.GlossaryManager;
import org.omegat.gui.glossary.GlossaryTextArea;
import org.omegat.gui.glossary.IGlossaries;
import org.omegat.gui.glossary.TransTipsMarker;
import org.omegat.gui.issues.IIssues;
import org.omegat.gui.issues.IssuesPanelController;
import org.omegat.gui.main.ConsoleWindow;
import org.omegat.gui.main.IMainWindow;
import org.omegat.gui.main.MainWindow;
import org.omegat.gui.matches.IMatcher;
import org.omegat.gui.matches.MatchesTextArea;
import org.omegat.gui.multtrans.MultipleTransPane;
import org.omegat.gui.notes.INotes;
import org.omegat.gui.notes.NotesTextArea;
import org.omegat.gui.properties.SegmentPropertiesArea;
import org.omegat.languagetools.LanguageToolWrapper;
import org.omegat.tokenizer.ITokenizer;
import org.omegat.util.Preferences;

/**
 * Class which contains all components instances.
 *
 * Note about threads synchronization: each component must have only local
 * synchronization. It mustn't synchronize around other components or some other
 * objects.
 *
 * Components which works in Swing UI thread can have other synchronization
 * idea: it can not be synchronized to access to some data which changed only in
 * UI thread.
 *
 * @author Alex Buloichik (alex73mail@gmail.com)
 * @author Wildrich Fourie
 */
public final class Core {

    private Core() {
    }

    private static IProject currentProject;
    private static IMainWindow mainWindow;
    protected static IEditor editor;
    private static ITagValidation tagValidation;
    private static IIssues issuesWindow;
    private static IMatcher matcher;
    private static ISpellChecker spellChecker;
    private static FilterMaster filterMaster;

    protected static IAutoSave saveThread;
    private static final ReentrantLock EXCLUSIVE_RUN_LOCK = new ReentrantLock();

    protected static IGlossaries glossary;
    private static GlossaryManager glossaryManager;
    private static MachineTranslateTextArea machineTranslatePane;
    private static DictionariesTextArea dictionaries;
    @SuppressWarnings("unused")
    private static MultipleTransPane multiple;
    private static INotes notes;
    private static IComments comments;
    private static Segmenter segmenter;

    private static Map<String, String> cmdLineParams = Collections.emptyMap();

    private static final List<String> PLUGINS_LOADING_ERRORS = Collections
            .synchronizedList(new ArrayList<String>());

    private static final List<IMarker> MARKERS = new ArrayList<IMarker>();

    /** Get project instance. */
//    nanti coba buat subset kelas Project ini
    public static IProject getProject() {
        return currentProject;
    }

    public static ProjectProperties getProjectProperties(){
        return currentProject.getProjectProperties();
    }

    public static File getParentFileProject(){
        return getProjectProperties().getProjectRootDir().getParentFile();
    }

    public static String getProjectInternal(){
        return getProjectProperties().getProjectInternal();
    }
    public static boolean isProjectLoaded() {
        return currentProject.isProjectLoaded();
    }
    public static boolean isProjectModified() {
        return currentProject.isProjectModified();
    }

    public static TMXEntry getTranslationInfo(SourceTextEntry entry){
        return currentProject.getTranslationInfo(entry);
    }

    public static boolean isTranslated(SourceTextEntry entry){
        return getTranslationInfo(entry).isTranslated();
    }

    public static IProject.FileInfo getProjectFile(int indexFile){
        return getProjectFiles().get(indexFile);
    }

    public static List<IProject.FileInfo> getProjectFiles(){
        return currentProject.getProjectFiles();
    }

    public static String getProjectFilePath(int indexFile){
        return getProjectFile(indexFile).filePath;
    }
    /** Set new current project. */
    public static void setProject(final IProject newCurrentProject) {
        currentProject = newCurrentProject;
    }

    /** Get main window instance. */
    public static IMainWindow getMainWindow() {
        return mainWindow;
    }

    /** Get editor instance. */
    public static IEditor getEditor() {
        return editor;
    }
    public static boolean isMarkGlossaryMatches(){
        return editor.getSettings().isMarkGlossaryMatches();
    }
    public static void remarkOneMarker(String markerClassName){
        editor.remarkOneMarker(markerClassName);
    }
    /** Get editor instance. */
    public static void commitSetFilter(IEditorFilter filter) {
        editor.commitAndLeave();
        editor.setFilter(filter);
    }

    /** Get tag validation component instance. */
    public static ITagValidation getTagValidation() {
        return tagValidation;
    }

    public static IIssues getIssues() {
        return issuesWindow;
    }

    /** Get matcher component instance. */
    public static IMatcher getMatcher() {
        return matcher;
    }

    /** Get spell checker instance. */
    public static ISpellChecker getSpellChecker() {
        return spellChecker;
    }

    public static FilterMaster getFilterMaster() {
        return filterMaster;
    }

    public static void setFilterMaster(FilterMaster newFilterMaster) {
        filterMaster = newFilterMaster;
        EntryKey.setIgnoreFileContext(newFilterMaster.getConfig().isIgnoreFileContext());
    }

    public static MachineTranslateTextArea getMachineTranslatePane() {
        return machineTranslatePane;
    }

    public static IAutoSave getAutoSave() {
        return saveThread;
    }

    /** Get glossary instance. */
    public static IGlossaries getGlossary() {
        return glossary;
    }

    public static GlossaryManager getGlossaryManager() {
        return glossaryManager;
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

    public static Segmenter getSegmenter() {
        return segmenter;
    }

    public static void setSegmenter(Segmenter newSegmenter) {
        segmenter = newSegmenter;
    }

    public static void showStatusMessageRB(String messageKey, Object... params){
        getMainWindow().showStatusMessageRB(messageKey, params);
    }
    /**
     * Initialize application components.
     */
//    sudah: Core & initializeGUI & 37 & 0.972 & 1 & 11 & 0.909 & 1
    public static void initializeGUI(final Map<String, String> params) throws Exception {
//        clscalled = IProject,LanguageToolWrapper,3+1 ,+4 = 10
//        cint = NotLoadedProject,init,3+2+6 = 11
        cmdLineParams = params;
        // 1. Initialize project
        currentProject = new NotLoadedProject();
        // 2. Initialize application frame
        regMarker();
        LanguageToolWrapper.init();
//       clscalled +3, Preferences +1
//        cint 3+2
        segmenter = new Segmenter(Preferences.getSRX());
        filterMaster = new FilterMaster(Preferences.getFilters());
        MainWindow me = new MainWindow();
        mainWindow = me;
        // 3. Initialize other components. They add themselves to the main window.
        addOnMainWindow(me);
//         6 cint, 4 clscalled
        tagValidation = new TagValidationTool();
        spellChecker = new SpellChecker();
        SaveThread th = new SaveThread();
        saveThread = th;
        th.start();
        new VersionCheckThread(10).start();
    }


    private static void addOnMainWindow(MainWindow me) {
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

    private static void regMarker() throws Exception {
        registerMarker(new ProtectedPartsMarker());
        registerMarker(new RemoveTagMarker());
        registerMarker(new NBSPMarker());
        registerMarker(new TransTipsMarker());

        registerMarker(new WhitespaceMarkerFactory.SpaceMarker());
        registerMarker(new WhitespaceMarkerFactory.TabMarker());
        registerMarker(new WhitespaceMarkerFactory.LFMarker());

        registerMarker(new BidiMarkerFactory.RLMMarker());
        registerMarker(new BidiMarkerFactory.LRMMarker());
        registerMarker(new BidiMarkerFactory.PDFMarker());
        registerMarker(new BidiMarkerFactory.LROMarker());
        registerMarker(new BidiMarkerFactory.RLOMarker());

        registerMarker(new ReplaceMarker());
        registerMarker(new ComesFromAutoTMMarker());
        registerMarker(new FontFallbackMarker());
    }
    /**
     * Initialize application components.
     */
    public static void initializeConsole(final Map<String, String> params) throws Exception {
        cmdLineParams = params;
        tagValidation = new TagValidationTool();
        currentProject = new NotLoadedProject();
        mainWindow = new ConsoleWindow();
    }

    /**
     * Set main window instance for unit tests.
     *
     * @param mainWindow
     */
    protected static void setMainWindow(IMainWindow mainWindow) {
        Core.mainWindow = mainWindow;
    }

    /**
     * Set project instance for unit tests.
     *
     * @param currentProject
     */
    protected static void setCurrentProject(IProject currentProject) {
        Core.currentProject = currentProject;
    }

    /**
     * Register class for calculate marks.
     *
     * @param marker
     *            marker implementation
     */
    public static void registerMarker(IMarker marker) {
        MARKERS.add(marker);
    }

    public static List<IMarker> getMarkers() {
        return MARKERS;
    }

    public static Map<String, String> getParams() {
        return cmdLineParams;
    }

    public static void registerFilterClass(Class<? extends IFilter> clazz) {
        PluginUtils.getFilterClasses().add(clazz);
    }

    public static void registerMachineTranslationClass(Class<? extends IMachineTranslation> clazz) {
        PluginUtils.getMachineTranslationClasses().add(clazz);
    }

    public static void registerMarkerClass(Class<? extends IMarker> clazz) {
        PluginUtils.getMarkerClasses().add(clazz);
    }

    public static void registerTokenizerClass(Class<? extends ITokenizer> clazz) {
        PluginUtils.getTokenizerClasses().add(clazz);
    }

    /**
     * Get all plugin loading errors.
     */
    public static List<String> getPluginsLoadingErrors() {
        return PLUGINS_LOADING_ERRORS;
    }

    /**
     * Any plugin can call this method for say about error on loading.
     */
    public static void pluginLoadingError(String errorText) {
        PLUGINS_LOADING_ERRORS.add(errorText);
    }

    /**
     * Use this to perform operations that must not be run concurrently.
     * <p>
     * For instance project load/save/compile/autosave operations must not be executed in parallel because it will break
     * project files, especially during team synchronization. For guaranteed non-parallel execution, all such operations
     * must be executed via this method.
     *
     * @param waitForUnlock
     *            should execution wait for unlock 3 minutes
     * @param run
     *            code for execute
     * @throws Exception
     */
    public static void executeExclusively(boolean waitForUnlock, RunnableWithException run)
            throws Exception {
        if (!EXCLUSIVE_RUN_LOCK.tryLock(waitForUnlock ? 180000 : 1, TimeUnit.MILLISECONDS)) {
            Exception ex = new TimeoutException("Timeout waiting for previous exclusive execution");
            Exception cause = new Exception("Previous exclusive execution");
            if (runningStackTrace != null) {
                cause.setStackTrace(runningStackTrace);
                ex.initCause(cause);
            }
            throw ex;
        }
        try {
            runningStackTrace = new Exception().getStackTrace();
            run.run();
        } finally {
            runningStackTrace = null;
            EXCLUSIVE_RUN_LOCK.unlock();
        }
    }

    private static StackTraceElement[] runningStackTrace;

    public interface RunnableWithException {
        void run() throws Exception;
    }
}
