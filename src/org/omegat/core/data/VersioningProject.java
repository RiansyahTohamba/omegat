package org.omegat.core.data;

import org.madlonkay.supertmxmerge.StmProperties;
import org.madlonkay.supertmxmerge.SuperTmxMerge;
import org.omegat.core.Core;
import org.omegat.core.team2.RebaseAndCommit;
import org.omegat.core.team2.RemoteRepositoryProvider;
import org.omegat.gui.glossary.GlossaryEntry;
import org.omegat.gui.glossary.GlossaryReaderTSV;
import org.omegat.util.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class VersioningProject {
    private final RealProject realProject;
    private static final Logger LOGGER = Logger.getLogger(RealProject.class.getName());

    public VersioningProject(RealProject realProject) {
        this.realProject = realProject;
    }

    public RemoteRepositoryProvider getRemoteRepositoryProvider() {
        return realProject.getRemoteRepositoryProvider();
    }
    public void syncSetTranslation(SourceTextEntry entry, PrepareTMXEntry trans, boolean defaultTranslation, TMXEntry.ExternalLinked externalLinked, IProject.AllTranslations previous) throws IProject.OptimisticLockingFail {
        IProject.AllTranslations current = getAllTranslations(entry);
        boolean wasAlternative = current.alternativeTranslation.isTranslated();
        if (defaultTranslation) {
            if (!current.defaultTranslation.equals(previous.defaultTranslation)) {
                throw new IProject.OptimisticLockingFail(previous.getDefaultTranslation().translation,
                        current.getDefaultTranslation().translation, current);
            }
            if (wasAlternative) {
                // alternative -> default
                if (!current.alternativeTranslation.equals(previous.alternativeTranslation)) {
                    throw new IProject.OptimisticLockingFail(previous.getAlternativeTranslation().translation,
                            current.getAlternativeTranslation().translation, current);
                }
                // remove alternative
                realProject.setTranslation(entry, new PrepareTMXEntry(), false, null);
            }
        } else {
            // new is alternative translation
            if (!current.alternativeTranslation.equals(previous.alternativeTranslation)) {
                throw new IProject.OptimisticLockingFail(previous.getAlternativeTranslation().translation,
                        current.getAlternativeTranslation().translation, current);
            }
        }
        realProject.setTranslation(entry, trans, defaultTranslation, externalLinked);
    }

    public void commitSourceFiles() throws Exception {
        if (realProject.isRemoteProject() && realProject.getConfig().getSourceDir().isUnderRoot())  {
            try {
                Core.getMainWindow().showStatusMessageRB("TF_COMMIT_START");
                realProject.getRemoteRepositoryProvider().switchAllToLatest();
                realProject.getRemoteRepositoryProvider().copyFilesFromProjectToRepo(realProject.getConfig().getSourceDir().getUnderRoot(), null);
                realProject.getRemoteRepositoryProvider().commitFiles(realProject.getConfig().getSourceDir().getUnderRoot(), "Commit source files");
                Core.getMainWindow().showStatusMessageRB("TF_COMMIT_DONE");
            } catch (Exception e) {
                Log.logErrorRB("TF_COMMIT_ERROR");
                Log.log(e);
                throw new IOException(OStrings.getString("TF_COMMIT_ERROR") + "\n"
                        + e.getMessage(), e);
            }
        }
    }
    /**
     * Do 3-way merge of:
     *
     * Base: baseTMX
     *
     * File 1: projectTMX (mine)
     *
     * File 2: headTMX (theirs)
     */
    public void mergeTMX(ProjectTMX baseTMX, ProjectTMX headTMX, StringBuilder commitDetails) {
        StmProperties props = new StmProperties()
                .setLanguageResource(OStrings.getResourceBundle())
                .setParentWindow(Core.getMainWindow().getApplicationFrame())
                // More than this number of conflicts will trigger List View by default.
                .setListViewThreshold(5);
        String srcLang = realProject.getConfig().getSourceLanguage().getLanguage();
        String trgLang = realProject.getConfig().getTargetLanguage().getLanguage();
        ProjectTMX mergedTMX = SuperTmxMerge.merge(
                new SyncTMX(baseTMX, OStrings.getString("TMX_MERGE_BASE"), srcLang, trgLang),
                new SyncTMX(projectTMX, OStrings.getString("TMX_MERGE_MINE"), srcLang, trgLang),
                new SyncTMX(headTMX, OStrings.getString("TMX_MERGE_THEIRS"), srcLang, trgLang), props);
        projectTMX.replaceContent(mergedTMX);
        Log.logDebug(LOGGER, "Merge report: {0}", props.getReport());
        commitDetails.append('\n');
        commitDetails.append(props.getReport().toString());
    }
    /**
     * Prepare for future team sync.
     * <p>
     * This method must be executed in the Core.executeExclusively.
     */
    @Override
    public void teamSyncPrepare() throws Exception {
        if (realProject.getRemoteRepositoryProvider() == null || realProject.getPreparedStatus() != RealProject.PreparedStatus.NONE || !realProject.isOnlineMode()) {
            return;
        }
        LOGGER.fine("Prepare team sync");
        realProject.setTmxPrepared(null);
        realProject.setGlossaryPrepared(null);
        realProject.getRemoteRepositoryProvider().cleanPrepared();

        String tmxPath = realProject.getConfig().getProjectInternalRelative() + OConsts.STATUS_EXTENSION;
        if (realProject.getRemoteRepositoryProvider().isUnderMapping(tmxPath)) {
            realProject.setTmxPrepared(RebaseAndCommit.prepare(realProject.getRemoteRepositoryProvider(), realProject.getConfig().getProjectRootDir(), tmxPath));
        }

        final String glossaryPath = realProject.getConfig().getWritableGlossaryFile().getUnderRoot();
        if (glossaryPath != null && realProject.getRemoteRepositoryProvider().isUnderMapping(glossaryPath)) {
            realProject.setGlossaryPrepared(RebaseAndCommit.prepare(realProject.getRemoteRepositoryProvider(), realProject.getConfig().getProjectRootDir(),
                    glossaryPath));
        }
        realProject.setPreparedStatus(RealProject.PreparedStatus.PREPARED);
    }

    @Override
    public boolean isTeamSyncPrepared() {
        return realProject.getPreparedStatus() == RealProject.PreparedStatus.PREPARED;
    }

    /**
     * Fast team sync for execute from SaveThread.
     * <p>
     * This method must be executed in the Core.executeExclusively.
     */
    @Override
    public void teamSync() {
        if (realProject.getRemoteRepositoryProvider() == null || realProject.getPreparedStatus() != RealProject.PreparedStatus.PREPARED) {
            return;
        }
        LOGGER.fine("Rebase team sync");
        try {
            realProject.setPreparedStatus(RealProject.PreparedStatus.PREPARED2);
            synchronized (realProject) {
                realProject.getProjectTMX().save(realProject.getConfig(), realProject.getConfig().getProjectInternal() + OConsts.STATUS_EXTENSION,
                        realProject.isProjectModified());
            }
            rebaseAndCommitProject(realProject.getGlossaryPrepared() != null);
            realProject.setPreparedStatus(RealProject.PreparedStatus.REBASED);

            new Thread(() -> {
                try {
                    Core.executeExclusively(true, () -> {
                        if (realProject.getPreparedStatus() != RealProject.PreparedStatus.REBASED) {
                            return;
                        }
                        LOGGER.fine("Commit team sync");
                        try {
                            String newVersion = RebaseAndCommit.commitPrepared(realProject.getTmxPrepared(), realProject.getRemoteRepositoryProvider(),
                                    null);
                            if (realProject.getGlossaryPrepared() != null) {
                                RebaseAndCommit.commitPrepared(realProject.getGlossaryPrepared(), realProject.getRemoteRepositoryProvider(), newVersion);
                            }

                            realProject.setTmxPrepared(null);
                            realProject.setGlossaryPrepared(null);

                            realProject.getRemoteRepositoryProvider().cleanPrepared();
                        } catch (Exception ex) {
                            Log.logErrorRB(ex, "CT_ERROR_SAVING_PROJ");
                        }
                        realProject.setPreparedStatus(RealProject.PreparedStatus.NONE);
                    });
                } catch (Exception ex) {
                    Log.logErrorRB(ex, "CT_ERROR_SAVING_PROJ");
                }
            }).start();
        } catch (Exception ex) {
            Log.logErrorRB(ex, "CT_ERROR_SAVING_PROJ");
            realProject.setPreparedStatus(RealProject.PreparedStatus.NONE);
        }
    }

    /**
     * Rebase changes in project to remote HEAD and upload changes to remote if possible.
     * <p>
     * How it works.
     * <p>
     * At each moment we have 3 versions of translation (project_save.tmx file) or writable glossary:
     * <ol>
     * <li>BASE - version which current translator downloaded from remote repository previously(on previous
     * synchronization or startup).
     *
     * <li>WORKING - current version in translator's OmegaT. It doesn't exist it remote repository yet. It's
     * inherited from BASE version, i.e. BASE + local changes.
     *
     * <li>HEAD - latest version in repository, which other translators committed. It's also inherited from
     * BASE version, i.e. BASE + remote changes.
     * </ol>
     * In an ideal world, we could just calculate diff between WORKING and BASE - it will be our local changes
     * after latest synchronization, then rebase these changes on the HEAD revision, then commit into remote
     * repository.
     * <p>
     * But we have some real world limitations:
     * <ul>
     * <li>Computers and networks work slowly, i.e. this synchronization will require some seconds, but
     * translator should be able to edit translation in this time.
     * <li>We have to handle network errors
     * <li>Other translators can commit own data in the same time.
     * </ul>
     * So, in the real world synchronization works by these steps:
     * <ol>
     * <li>Download HEAD revision from remote repository and load it in memory.
     * <li>Load BASE revision from local disk.
     * <li>Calculate diff between WORKING and BASE, then rebase it on the top of HEAD revision. This step
     * synchronized around memory TMX, so, all edits are stopped. Since it's enough fast step, it's okay.
     * <li>Upload new revision into repository.
     * </ol>
     */
    void rebaseAndCommitProject(boolean processGlossary) throws Exception {
        Log.logInfoRB("TEAM_REBASE_START");

        final String author = Preferences.getPreferenceDefault(Preferences.TEAM_AUTHOR,
                System.getProperty("user.name"));
        final StringBuilder commitDetails = new StringBuilder("Translated by " + author);
        String tmxPath = realProject.getConfig().getProjectInternalRelative() + OConsts.STATUS_EXTENSION;
        if (realProject.getRemoteRepositoryProvider().isUnderMapping(tmxPath)) {
            RebaseAndCommit.rebaseAndCommit(realProject.getTmxPrepared(), realProject.getRemoteRepositoryProvider(), realProject.getConfig().getProjectRootDir(),
                    tmxPath, new RebaseAndCommit.IRebase() {
                        ProjectTMX baseTMX, headTMX;

                        @Override
                        public void parseBaseFile(File file) throws Exception {
                            baseTMX = new ProjectTMX(realProject.getConfig().getSourceLanguage(), realProject.getConfig()
                                    .getTargetLanguage(), realProject.getConfig().isSentenceSegmentingEnabled(), file, null);
                        }

                        @Override
                        public void parseHeadFile(File file) throws Exception {
                            headTMX = new ProjectTMX(realProject.getConfig().getSourceLanguage(), realProject.getConfig()
                                    .getTargetLanguage(), realProject.getConfig().isSentenceSegmentingEnabled(), file, null);
                        }

                        @Override
                        public void rebaseAndSave(File out) throws Exception {
                            mergeTMX(baseTMX, headTMX, commitDetails);
                            realProject.getProjectTMX().exportTMX(realProject.getConfig(), out, false, false, true);
                        }

                        @Override
                        public String getCommentForCommit() {
                            return commitDetails.toString();
                        }

                        @Override
                        public String getFileCharset(File file) throws Exception {
                            return TMXReader2.detectCharset(file);
                        }
                    });
            if (realProject.getProjectTMX() != null) {
                // it can be not loaded yet
                ProjectTMX newTMX = new ProjectTMX(realProject.getConfig().getSourceLanguage(),
                        realProject.getConfig().getTargetLanguage(), realProject.getConfig().isSentenceSegmentingEnabled(), new File(
                        realProject.getConfig().getProjectInternalDir(), OConsts.STATUS_EXTENSION), null);
                realProject.getProjectTMX().replaceContent(newTMX);
            }
        }

        if (processGlossary) {
            final String glossaryPath = realProject.getConfig().getWritableGlossaryFile().getUnderRoot();
            final File glossaryFile = realProject.getConfig().getWritableGlossaryFile().getAsFile();
            new File(realProject.getConfig().getProjectRootDir(), glossaryPath);
            if (glossaryPath != null && realProject.getRemoteRepositoryProvider().isUnderMapping(glossaryPath)) {
                final List<GlossaryEntry> glossaryEntries;
                if (glossaryFile.exists()) {
                    glossaryEntries = GlossaryReaderTSV.read(glossaryFile, true);
                } else {
                    glossaryEntries = Collections.emptyList();
                }
                RebaseAndCommit.rebaseAndCommit(realProject.getGlossaryPrepared(), realProject.getRemoteRepositoryProvider(),
                        realProject.getConfig().getProjectRootDir(), glossaryPath, new RebaseAndCommit.IRebase() {
                            List<GlossaryEntry> baseGlossaryEntries, headGlossaryEntries;

                            @Override
                            public void parseBaseFile(File file) throws Exception {
                                if (file.exists()) {
                                    baseGlossaryEntries = GlossaryReaderTSV.read(file, true));
                                } else {
                                    baseGlossaryEntries = new ArrayList<GlossaryEntry>());
                                }
                            }

                            @Override
                            public void parseHeadFile(File file) throws Exception {
                                if (file.exists()) {
                                    headGlossaryEntries = GlossaryReaderTSV.read(file, true));
                                } else {
                                    headGlossaryEntries = new ArrayList<GlossaryEntry>());
                                }
                            }

                            @Override
                            public void rebaseAndSave(File out) throws Exception {
                                List<GlossaryEntry> deltaAddedGlossaryLocal = new ArrayList<GlossaryEntry>(
                                        glossaryEntries);
                                deltaAddedGlossaryLocal.removeAll(baseGlossaryEntries);
                                List<GlossaryEntry> deltaRemovedGlossaryLocal = new ArrayList<GlossaryEntry>(
                                        baseGlossaryEntries);
                                deltaRemovedGlossaryLocal.removeAll(glossaryEntries);
                                headGlossaryEntries.addAll(deltaAddedGlossaryLocal);
                                headGlossaryEntries.removeAll(deltaRemovedGlossaryLocal);

                                for (GlossaryEntry ge : headGlossaryEntries) {
                                    GlossaryReaderTSV.append(out, ge);
                                }
                            }

                            @Override
                            public String getCommentForCommit() {
                                final String author = Preferences.getPreferenceDefault(Preferences.TEAM_AUTHOR,
                                        System.getProperty("user.name"));
                                return "Glossary changes by " + author;
                            }

                            @Override
                            public String getFileCharset(File file) throws Exception {
                                return GlossaryReaderTSV.getFileEncoding(file);
                            }
                        });
            }
        }
        Log.logInfoRB("TEAM_REBASE_END");
    }
}