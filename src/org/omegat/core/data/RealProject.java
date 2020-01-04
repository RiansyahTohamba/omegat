/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2000-2006 Keith Godfrey, Maxym Mykhalchuk, and Henry Pijffers
               2007 Zoltan Bartko
               2008-2016 Alex Buloichik
               2009-2010 Didier Briel
               2012 Guido Leenders, Didier Briel, Martin Fleurke
               2013 Aaron Madlon-Kay, Didier Briel
               2014 Aaron Madlon-Kay, Didier Briel
               2015 Aaron Madlon-Kay
               2017-2018 Didier Briel
               2019 Thomas Cordonnier
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

package org.omegat.core.data;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import org.jetbrains.annotations.NotNull;
import org.omegat.CLIParameters;
import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.data.TMXEntry.ExternalLinked;
import org.omegat.core.events.IProjectEventListener;
import org.omegat.core.segmentation.SRX;
import org.omegat.core.statistics.StatisticsInfo;
import org.omegat.core.team2.RebaseAndCommit;
import org.omegat.core.team2.RemoteRepositoryProvider;
import org.omegat.filters2.FilterContext;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.tokenizer.ITokenizer;
import org.omegat.util.DirectoryMonitor;
import org.omegat.util.Language;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.PatternConsts;
import org.omegat.util.Preferences;
import org.omegat.util.ProjectFileStorage;
import org.omegat.util.RuntimePreferences;
import org.omegat.util.StringUtil;
import org.omegat.util.TagUtil;
import org.omegat.util.gui.UIThreadsUtil;

/**
 * Loaded project implementation. Only translation could be changed after project will be loaded and set by
 * Core.setProject.
 *
 * All components can read all data directly without synchronization. All synchronization implemented inside
 * RealProject.
 *
 * Since team sync is long operation, autosaving was splitted into 3 phrases: get remote data in background, then rebase
 * during segment deactivation, then commit in background.
 *
 * @author Keith Godfrey
 * @author Henry Pijffers (henry.pijffers@saxnot.com)
 * @author Maxym Mykhalchuk
 * @author Bartko Zoltan (bartkozoltan@bartkozoltan.com)
 * @author Alex Buloichik (alex73mail@gmail.com)
 * @author Didier Briel
 * @author Guido Leenders
 * @author Martin Fleurke
 * @author Aaron Madlon-Kay
 */
public class RealProject implements IProject {

    protected final ProjectProperties config;
    protected RemoteRepositoryProvider remoteRepositoryProvider;
    private final BigLoad bigLoad = new BigLoad(this);
    private final BuildTMX buildTMX = new BuildTMX(this);
    private final SavingProject savingProject = new SavingProject(this);
    private final CreatingProject creatingProject = new CreatingProject(this);
    private final CompilingProject compilingProject = new CompilingProject(this);
    private final VersioningProject versioningProject = new VersioningProject(this);
    private final AligningProject aligningProject = new AligningProject(this);
    private final TranslationProject translationProject = new TranslationProject(this);
    private DirectoryMonitor tmMonitor;

    private DirectoryMonitor tmOtherLanguagesMonitor;

    public List<FileInfo> getProjectFilesList() {
        return projectFilesList;
    }

    public List<SourceTextEntry> getAllProjectEntries() {
        return allProjectEntries;
    }

    public StatisticsInfo getHotStat() {
        return hotStat;
    }

    public boolean isOnlineMode() {
        return isOnlineMode;
    }

    public RebaseAndCommit.Prepared getGlossaryPrepared() {
        return glossaryPrepared;
    }

    public RebaseAndCommit.Prepared getTmxPrepared() {
        return tmxPrepared;
    }

    public boolean isLoaded() {
        return loaded;
    }


    public ProjectTMX getProjectTMX() {
        return projectTMX;
    }

    public ProjectProperties getConfig() {
        return config;
    }

    public void setAllProjectEntries(List<SourceTextEntry> allProjectEntries) {
        this.allProjectEntries = allProjectEntries;
    }

    public void setOnlineMode(boolean onlineMode) {
        isOnlineMode = onlineMode;
    }

    public void setGlossaryPrepared(RebaseAndCommit.Prepared glossaryPrepared) {
        this.glossaryPrepared = glossaryPrepared;
    }

    public void setTmxPrepared(RebaseAndCommit.Prepared tmxPrepared) {
        this.tmxPrepared = tmxPrepared;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public void setImportHandler(ImportFromAutoTMX importHandler) {
        this.importHandler = importHandler;
    }

    public void setProjectTMX(ProjectTMX projectTMX) {
        this.projectTMX = projectTMX;
    }

    public Set<EntryKey> getExistKeys() {
        return existKeys;
    }

    public Set<String> getExistSource() {
        return existSource;
    }

    public BuildTMX getBuildTMX() {
        return buildTMX;
    }

    public PreparedStatus getPreparedStatus() {
        return preparedStatus;
    }

    public RemoteRepositoryProvider getRemoteRepositoryProvider() {
        return remoteRepositoryProvider;
    }

    public void setPreparedStatus(PreparedStatus preparedStatus) {
        this.preparedStatus = preparedStatus;
    }

    public VersioningProject getVersioningProject() {
        return versioningProject;
    }

    public ITokenizer getSourceTokenizer() {
        return sourceTokenizer;
    }

    public ITokenizer getTargetTokenizer() {
        return targetTokenizer;
    }

    enum PreparedStatus {
        NONE, PREPARED, PREPARED2, REBASED
    };

    /**
     * Status required for execute prepare/rebase/commit in the correct order.
     */
    private volatile PreparedStatus preparedStatus = PreparedStatus.NONE;
    private volatile RebaseAndCommit.Prepared tmxPrepared;
    private volatile RebaseAndCommit.Prepared glossaryPrepared;

    private boolean isOnlineMode;

    private RandomAccessFile raFile;
    private FileChannel lockChannel;
    private FileLock lock;

    private boolean modified;

    /** List of all segments in project. */
    protected List<SourceTextEntry> allProjectEntries = new ArrayList<SourceTextEntry>(4096);

    protected ImportFromAutoTMX importHandler;

    private final StatisticsInfo hotStat = new StatisticsInfo();

    private ITokenizer sourceTokenizer, targetTokenizer;

    /**
     * Indicates when there is an ongoing save event. Saving might take a while during
     * team sync: if a merge is required the save might be postponed indefinitely while we
     * wait for the user to confirm the current segment.
     */
    private boolean isSaving = false;

    /**
     * Storage for all translation memories, which shouldn't be changed and saved, i.e. for /tm/*.tmx files,
     * aligned data from source files.
     *
     * This map recreated each time when files changed. So, you can free use it without thinking about
     * synchronization.
     */
    private Map<String, ExternalTMX> transMemories = new TreeMap<String, ExternalTMX>();

    /**
     * Storage for all translation memories of translations to other languages.
     */
    private Map<Language, ProjectTMX> otherTargetLangTMs = new TreeMap<Language, ProjectTMX>();

    protected ProjectTMX projectTMX;

    /**
     * True if project loaded successfully.
     */
    private boolean loaded = false;

    // Sets of exist entries for check orphaned
    private Set<String> existSource = new HashSet<String>();
    private Set<EntryKey> existKeys = new HashSet<EntryKey>();

    /** Segments count in project files. */
    protected List<FileInfo> projectFilesList = new ArrayList<FileInfo>();

    /** This instance returned if translation not exist. */
    private static final TMXEntry EMPTY_TRANSLATION;
    static {
        PrepareTMXEntry empty = new PrepareTMXEntry();
        empty.source = "";
        EMPTY_TRANSLATION = new TMXEntry(empty, true, null);
    }

    private boolean allowTranslationEqualToSource = Preferences.isPreference(Preferences.ALLOW_TRANS_EQUAL_TO_SRC);

    /**
     * A list of external processes. Allows previously-started, hung or long-running processes to be
     * forcibly terminated when compiling the project anew or when closing the project.
     */
    private Stack<Process> processCache = new Stack<Process>();

    /**
     * Create new project instance. It required to call {@link #createProject()}
     * or {@link #loadProject(boolean)} methods just after constructor before
     * use project.
     *
     * @param props
     *            project properties
     */
    public RealProject(final ProjectProperties props) {
        config = props;
        remoteRepositoryProvider = getRemoteRepoProvider(props);
        ProjectTokenizer pt = new ProjectTokenizer();
        sourceTokenizer = pt.getSource(props);
        targetTokenizer = pt.getTarget(props);
    }


    public RemoteRepositoryProvider getRemoteRepoProvider(ProjectProperties config) {
        RemoteRepositoryProvider remoteRepositoryProvider;
        if (config.getRepositories() != null && !Core.getParams().containsKey(CLIParameters.NO_TEAM)) {
            try {
                remoteRepositoryProvider = new RemoteRepositoryProvider(config.getProjectRootDir(),
                        config.getRepositories());
            } catch (Exception ex) {
                // TODO
                throw new RuntimeException(ex);
            }
        } else {
            remoteRepositoryProvider = null;
        }
        return remoteRepositoryProvider;
    }

    public void saveProjectProperties() throws Exception {
        unlockProject();
        try {
            SRX.saveTo(config.getProjectSRX(), new File(config.getProjectInternal(), SRX.CONF_SENTSEG));
            FilterMaster.saveConfig(config.getProjectFilters(),
                    new File(config.getProjectInternal(), FilterMaster.FILE_FILTERS));
            ProjectFileStorage.writeProjectFile(config);
        } finally {
            lockProject();
        }
        Preferences.setPreference(Preferences.SOURCE_LOCALE, config.getSourceLanguage().toString());
        Preferences.setPreference(Preferences.TARGET_LOCALE, config.getTargetLanguage().toString());
    }

    /**
     * Create new project.
     */
    public void createProject() {
        Log.logInfoRB("LOG_DATAENGINE_CREATE_START");
        UIThreadsUtil.mustNotBeSwingThread();

        creatingProject.create();
        Log.logInfoRB("LOG_DATAENGINE_CREATE_END");
    }

    /**
     * Load exist project in a "big" sense -- loads project's properties, glossaries, tms, source files etc.
     */
    public synchronized void loadProject(boolean onlineMode) {

        // load new project

        bigLoad.loadProject(onlineMode);
    }


    /**
     * Align project.
     */
    public Map<String, TMXEntry> align(final ProjectProperties props, final File translatedDir)
            throws Exception {

        return aligningProject.align(props, translatedDir);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isProjectLoaded() {
        return loaded;
    }

    /**
     * {@inheritDoc}
     */
    public StatisticsInfo getStatistics() {
        return hotStat;
    }

    /**
     * Signals to the core thread that a project is being closed now, and if it's still being loaded, core
     * thread shouldn't throw any error.
     */
    public void closeProject() {
        loaded = false;
        flushProcessCache();
        tmMonitor.fin();
        tmOtherLanguagesMonitor.fin();
        unlockProject();
        Log.logInfoRB("LOG_DATAENGINE_CLOSE");
    }

    /**
     * Lock omegat.project file against rename or move project.
     */
    protected boolean lockProject() {
        if (!RuntimePreferences.isProjectLockingEnabled()) {
            return true;
        }
        try {
            File lockFile = new File(config.getProjectRoot(), OConsts.FILE_PROJECT);
            raFile = new RandomAccessFile(lockFile, "rw");
            lockChannel = raFile.getChannel();
            lock = lockChannel.tryLock();
        } catch (Throwable ex) {
            Log.log(ex);
        }
        if (lock == null) {
            try {
                lockChannel.close();
            } catch (Throwable ignored) {
            }
            lockChannel = null;
            try {
                raFile.close();
            } catch (Throwable ignored) {
            }
            raFile = null;
            return false;
        } else {
            return true;
        }
    }

    /**
     * Unlock omegat.project file against rename or move project.
     */
    protected void unlockProject() {
        if (!RuntimePreferences.isProjectLockingEnabled()) {
            return;
        }
        try {
            if (lock != null) {
                lock.release();
            }
            if (lockChannel != null) {
                lockChannel.close();
            }
            if (raFile != null) {
                raFile.close();
            }
        } catch (Throwable ex) {
            Log.log(ex);
        } finally {
            try {
                lockChannel.close();
            } catch (Throwable ignored) {
            }
            try {
                raFile.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Builds translated files corresponding to sourcePattern and creates fresh TM files. Convenience method. Assumes we
     * want to run external post-processing commands.
     *
     * @param sourcePattern
     *            The regexp of files to create
     * @throws Exception
     */
    public void compileProject(String sourcePattern) throws Exception {
        compilingProject.compileProject(sourcePattern);
    }

    /**
     * Builds translated files corresponding to sourcePattern and creates fresh TM files.
     * Calls the actual compile project method, assumes we don't want to commit target files.
     *
     * @param sourcePattern
     *            The regexp of files to create
     * @param doPostProcessing
     *            Whether or not we should perform external post-processing.
     * @throws Exception
     */
    public void compileProject(String sourcePattern, boolean doPostProcessing) throws Exception {
        compilingProject.compileProject(sourcePattern, doPostProcessing);
    }

    /**
     * Builds translated files corresponding to sourcePattern and creates fresh TM files.
     *
     * @param sourcePattern
     *            The regexp of files to create
     * @param doPostProcessing
     *            Whether or not we should perform external post-processing.
     * @param commitTargetFiles
     *            Whether or not we should commit target files
     * @throws Exception
     */
    @Override
    public void compileProjectAndCommit(String sourcePattern, boolean doPostProcessing, boolean commitTargetFiles)
            throws Exception {

        compilingProject.compileProjectAndCommit(sourcePattern, doPostProcessing, commitTargetFiles);
    }


    /**
     * Clear cache of previously run external processes, terminating any that haven't finished.
     */
    private void flushProcessCache() {
        while (!processCache.isEmpty()) {
            Process p = processCache.pop();
            try {
                p.exitValue();
            } catch (IllegalThreadStateException ex) {
                p.destroy();
            }
        }
    }

    /**
     * Saves the translation memory and preferences.
     *
     * This method must be executed in the Core.executeExclusively.
     */
    public synchronized void saveProject(boolean doTeamSync) {
        if (isSaving) {
            return;
        }
        isSaving = true;

        Log.logInfoRB("LOG_DATAENGINE_SAVE_START");
        UIThreadsUtil.mustNotBeSwingThread();

        savingProject.save(doTeamSync);
        Log.logInfoRB("LOG_DATAENGINE_SAVE_END");

        isSaving = false;
    }

    /**
     * Prepare for future team sync.
     *
     * This method must be executed in the Core.executeExclusively.
     */
    @Override
    public void teamSyncPrepare() throws Exception {

        versioningProject.teamSyncPrepare();
    }

    @Override
    public boolean isTeamSyncPrepared() {
        return versioningProject.isTeamSyncPrepared();
    }

    /**
     * Fast team sync for execute from SaveThread.
     *
     * This method must be executed in the Core.executeExclusively.
     */
    @Override
    public void teamSync() {
        versioningProject.teamSync();
    }

    // ///////////////////////////////////////////////////////////////
    // ///////////////////////////////////////////////////////////////
    // protected functions

    /**
     * Append new translation from auto TMX.
     */
    void appendFromAutoTMX(ExternalTMX tmx, boolean isEnforcedTMX) {
        synchronized (projectTMX) {
            importHandler.process(tmx, isEnforcedTMX);
        }
    }

    /**
     * {@inheritDoc}
     */
    public List<SourceTextEntry> getAllEntries() {
        return allProjectEntries;
    }

    public TMXEntry getTranslationInfo(SourceTextEntry ste) {
        if (projectTMX == null) {
            return EMPTY_TRANSLATION;
        }
        TMXEntry r = projectTMX.getMultipleTranslation(ste.getKey());
        if (r == null) {
            r = projectTMX.getDefaultTranslation(ste.getSrcText());
        }
        if (r == null) {
            r = EMPTY_TRANSLATION;
        }
        return r;
    }

    public AllTranslations getAllTranslations(SourceTextEntry ste) {
        return versioningProject.getAllTranslationsRealProject(ste);
    }



    /**
     * Returns the active Project's Properties.
     */
    public ProjectProperties getProjectProperties() {
        return config;
    }

    /**
     * Returns whether the project was modified. I.e. translations were changed since last save.
     */
    public boolean isProjectModified() {
        return modified;
    }

    public void setProjectModified(boolean isModified) {
        modified = isModified;
        if (isModified) {
            CoreEvents.fireProjectChange(IProjectEventListener.PROJECT_CHANGE_TYPE.MODIFIED);
        }
    }

    @Override
    public void setTranslation(SourceTextEntry entry, PrepareTMXEntry trans, boolean defaultTranslation,
            ExternalLinked externalLinked, AllTranslations previous) throws OptimisticLockingFail {
        //Core.getProject().setTranslation();
        translationProject.setTranslation(entry, trans, defaultTranslation, externalLinked, previous);
    }


    @Override
    public void setTranslation(final SourceTextEntry entry, final PrepareTMXEntry trans, boolean defaultTranslation,
            TMXEntry.ExternalLinked externalLinked) {

        translationProject.setTranslation(entry, trans, defaultTranslation, externalLinked);
    }

    public void translateEntry(SourceTextEntry entry, PrepareTMXEntry trans, boolean defaultTranslation, ExternalLinked externalLinked) {

        /**
         * Calculate how to statistics should be changed.
         */
        translationProject.translateEntry(entry, trans, defaultTranslation, externalLinked);
    }

    @Override
    public void setNote(final SourceTextEntry entry, final TMXEntry oldTE, String note) {
        if (oldTE == null) {
            throw new IllegalArgumentException("RealProject.setNote(tr) can't be null");
        }

        // Disallow empty notes. Use null to represent lack of note.
        if (note != null && note.isEmpty()) {
            note = null;
        }

        TMXEntry prevTrEntry = oldTE.defaultTranslation ? projectTMX
                .getDefaultTranslation(entry.getSrcText()) : projectTMX
                .getMultipleTranslation(entry.getKey());
        if (prevTrEntry != null) {
            PrepareTMXEntry en = new PrepareTMXEntry(prevTrEntry);
            en.note = note;
            projectTMX.setTranslation(entry, new TMXEntry(en, prevTrEntry.defaultTranslation,
                    prevTrEntry.linked), prevTrEntry.defaultTranslation);
        } else {
            PrepareTMXEntry en = new PrepareTMXEntry();
            en.source = entry.getSrcText();
            en.note = note;
            en.translation = null;
            projectTMX.setTranslation(entry, new TMXEntry(en, true, null), true);
        }

        setProjectModified(true);
    }

    public void iterateByDefaultTranslations(DefaultTranslationsIterator it) {
        if (projectTMX == null) {
            return;
        }
        Map.Entry<String, TMXEntry>[] entries;
        synchronized (projectTMX) {
            entries = entrySetToArray(projectTMX.defaults.entrySet());
        }
        for (Map.Entry<String, TMXEntry> en : entries) {
            it.iterate(en.getKey(), en.getValue());
        }
    }

    public void iterateByMultipleTranslations(MultipleTranslationsIterator it) {
        if (projectTMX == null) {
            return;
        }
        Map.Entry<EntryKey, TMXEntry>[] entries;
        synchronized (projectTMX) {
            entries = entrySetToArray(projectTMX.alternatives.entrySet());
        }
        for (Map.Entry<EntryKey, TMXEntry> en : entries) {
            it.iterate(en.getKey(), en.getValue());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <K, V> Map.Entry<K, V>[] entrySetToArray(Set<Map.Entry<K, V>> set) {
        // Assign to variable to facilitate suppressing the rawtypes warning
        Map.Entry[] a = new Map.Entry[set.size()];
        return set.toArray(a);
    }

    public boolean isOrphaned(String source) {
        return !checkOrphanedCallback.existSourceInProject(source);
    }

    public boolean isOrphaned(EntryKey entry) {
        return !checkOrphanedCallback.existEntryInProject(entry);
    }

    public Map<String, ExternalTMX> getTransMemories() {
        return Collections.unmodifiableMap(transMemories);
    }

    public Map<Language, ProjectTMX> getOtherTargetLanguageTMs() {
        return Collections.unmodifiableMap(otherTargetLangTMs);
    }



    /**
     * {@inheritDoc}
     */
    public List<FileInfo> getProjectFiles() {
        return Collections.unmodifiableList(projectFilesList);
    }

    @Override
    public String getTargetPathForSourceFile(String currentSource) {
        if (StringUtil.isEmpty(currentSource)) {
            return null;
        }
        try {
            return Core.getFilterMaster().getTargetForSource(config.getSourceRoot(),
                    currentSource, new FilterContext(config));
        } catch (Exception e) {
            Log.log(e);
        }
        return null;
    }

    @Override
    public List<String> getSourceFilesOrder() {
        Path path = Paths.get(config.getProjectInternal(), OConsts.FILES_ORDER_FILENAME);
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void setSourceFilesOrder(List<String> filesList) {
        Path path = Paths.get(config.getProjectInternal(), OConsts.FILES_ORDER_FILENAME);
        try (Writer wr = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String f : filesList) {
                wr.write(f);
                wr.write('\n');
            }
        } catch (Exception ex) {
            Log.log(ex);
        }
    }

    /**
     * This method converts directory separators into Unix-style. It required to have the same filenames in
     * the alternative translation in Windows and Unix boxes.
     * <p>
     * Also it can use {@code --alternate-filename-from} and {@code --alternate-filename-to} command line
     * parameters for change filename in entry key. It allows to have many versions of one file in one
     * project.
     * <p>
     * Because the filename can be stored in the project TMX, it also removes any XML-unsafe chars.
     *
     * @param filename
     *            filesystem's filename
     * @return normalized filename
     */
    protected String patchFileNameForEntryKey(String filename) {
        String f = Core.getParams().get(CLIParameters.ALTERNATE_FILENAME_FROM);
        String t = Core.getParams().get(CLIParameters.ALTERNATE_FILENAME_TO);
        String fn = filename.replace('\\', '/');
        if (f != null && t != null) {
            fn = fn.replaceAll(f, t);
        }
        return StringUtil.removeXMLInvalidChars(fn);
    }

    protected class LoadFilesCallback extends ParseEntry {
        private FileInfo fileInfo;
        private String entryKeyFilename;

        private final Set<String> existSource;
        private final Set<EntryKey> existKeys;
        private final Map<String, ExternalTMX> externalTms;

        private ExternalTMFactory.Builder tmBuilder;

        public LoadFilesCallback(Set<String> existSource, Set<EntryKey> existKeys,
                Map<String, ExternalTMX> externalTms) {
            super(config);
            this.existSource = existSource;
            this.existKeys = existKeys;
            this.externalTms = externalTms;
        }

        public void setCurrentFile(FileInfo fi) {
            fileInfo = fi;
            super.setCurrentFile(fi);
            entryKeyFilename = patchFileNameForEntryKey(fileInfo.filePath);
        }

        public void fileFinished() {
            super.fileFinished();

            if (tmBuilder != null && externalTms != null) {
                externalTms.put(entryKeyFilename, tmBuilder.done());
            }

            fileInfo = null;
            tmBuilder = null;
        }

        /**
         * {@inheritDoc}
         */
        protected void addSegment(String id, short segmentIndex, String segmentSource,
                List<ProtectedPart> protectedParts, String segmentTranslation, boolean segmentTranslationFuzzy,
                String[] props, String prevSegment, String nextSegment, String path) {
            // if the source string is empty, don't add it to TM
            if (segmentSource.trim().isEmpty()) {
                throw new RuntimeException("Segment must not be empty");
            }

            EntryKey ek = new EntryKey(entryKeyFilename, segmentSource, id, prevSegment, nextSegment, path);

            protectedParts = TagUtil.applyCustomProtectedParts(segmentSource,
                    PatternConsts.getPlaceholderPattern(), protectedParts);

            //If Allow translation equals to source is not set, we ignore such existing translations
            if (ek.sourceText.equals(segmentTranslation) && !allowTranslationEqualToSource) {
                segmentTranslation = null;
            }
            SourceTextEntry srcTextEntry = new SourceTextEntry(ek, allProjectEntries.size() + 1, props,
                    segmentTranslation, protectedParts, segmentIndex == 0);
            srcTextEntry.setSourceTranslationFuzzy(segmentTranslationFuzzy);

            if (SegmentProperties.isReferenceEntry(props)) {
                if (tmBuilder == null) {
                    tmBuilder = new ExternalTMFactory.Builder(new File(entryKeyFilename).getName());
                }
                tmBuilder.addEntry(segmentSource, segmentTranslation, id, path, props);
            } else {
                allProjectEntries.add(srcTextEntry);
                fileInfo.entries.add(srcTextEntry);

                existSource.add(segmentSource);
                existKeys.add(srcTextEntry.getKey());
            }
        }
    }

    ProjectTMX.CheckOrphanedCallback checkOrphanedCallback = new ProjectTMX.CheckOrphanedCallback() {
        public boolean existSourceInProject(String src) {
            return existSource.contains(src);
        }

        public boolean existEntryInProject(EntryKey key) {
            return existKeys.contains(key);
        }
    };

    void setOnlineMode() {
        if (!isOnlineMode) {
            Log.logInfoRB("VCS_ONLINE");
            Core.getMainWindow().displayWarningRB("VCS_ONLINE", "VCS_OFFLINE");
        }
        isOnlineMode = true;
        preparedStatus = PreparedStatus.NONE;
    }

    void setOfflineMode() {
        if (isOnlineMode) {
            Log.logInfoRB("VCS_OFFLINE");
            Core.getMainWindow().displayWarningRB("VCS_OFFLINE", "VCS_ONLINE");
        }
        isOnlineMode = false;
        preparedStatus = PreparedStatus.NONE;
    }

    @Override
    public boolean isRemoteProject() {
        return remoteRepositoryProvider != null;
    }

    @Override
    public void commitSourceFiles() throws Exception {
        versioningProject.commitSourceFiles();
    }
}
