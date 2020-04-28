package org.omegat.core.data;

import org.madlonkay.supertmxmerge.StmProperties;
import org.madlonkay.supertmxmerge.SuperTmxMerge;
import org.omegat.CLIParameters;
import org.omegat.core.Core;
import org.omegat.core.team2.IRemoteRepository2;
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
    protected RemoteRepositoryProvider remoteRepositoryProvider;
    private volatile RebaseAndCommit.Prepared tmxPrepared;
    private volatile RebaseAndCommit.Prepared glossaryPrepared;
    
    public VersioningProject(RealProject realProject,ProjectProperties config) {
        this.realProject = realProject;
        setRemoteRepo(config);
    }

    private void setRemoteRepo(ProjectProperties config) {
        if (config.getRepositories() != null && !Core.getParams().containsKey(CLIParameters.NO_TEAM)) {
            try {
                remoteRepositoryProvider = new RemoteRepositoryProvider(config.getProjectRootDir(),
                        config.getRepositories());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            remoteRepositoryProvider = null;
        }
    }

    public RemoteRepositoryProvider getRemoteRepositoryProvider() {
        return remoteRepositoryProvider;
    }

    public void afterLoadTranslations(boolean isOnlineMode,ProjectProperties config) throws Exception {
        // This MUST happen after calling loadTranslations()
        if (remoteRepositoryProvider != null && isOnlineMode) {
            Core.getMainWindow().showStatusMessageRB("TEAM_REBASE_AND_COMMIT");
            rebaseAndCommitProject(true,config);
        }
    }

    public void saveTeamSync(boolean doTeamSync,ProjectProperties config) throws Exception {
        if (remoteRepositoryProvider != null && doTeamSync) {
            tmxPrepared = null;
            glossaryPrepared = null;
            remoteRepositoryProvider.cleanPrepared();
            Core.getMainWindow().showStatusMessageRB("TEAM_SYNCHRONIZE");
            rebaseAndCommitProject(true,config);
            realProject.setOnlineMode();
        }
    }

    public void configLoad(ProjectProperties config) throws Exception {
        if (remoteRepositoryProvider != null) {
            try {
                tmxPrepared = null;
                glossaryPrepared = null;

                remoteRepositoryProvider.switchAllToLatest();
            } catch (IRemoteRepository2.NetworkException e) {
                Log.logErrorRB("TEAM_NETWORK_ERROR", e.getCause());
                realProject.setOfflineMode();
            } catch (Exception e) {
                e.printStackTrace();
            }

            remoteRepositoryProvider.copyFilesFromRepoToProject("", '/' + RemoteRepositoryProvider.REPO_SUBDIR,
                    '/' + RemoteRepositoryProvider.REPO_GIT_SUBDIR, '/' + RemoteRepositoryProvider.REPO_SVN_SUBDIR,
                    '/' + OConsts.FILE_PROJECT,
                    '/' + config.getProjectInternalRelative() + OConsts.STATUS_EXTENSION,
                    '/' + config.getWritableGlossaryFile().getUnderRoot(),
                    '/' + config.getTargetDir().getUnderRoot());

            // After adding filters.xml and segmentation.conf, we must reload them again
            config.loadProjectFilters();
            config.loadProjectSRX();
        }
    }
    public void commitTarget(boolean commitTargetFiles,boolean isOnlineMode,ProjectProperties config) throws IOException {
        if (remoteRepositoryProvider != null && config.getTargetDir().isUnderRoot() && commitTargetFiles && isOnlineMode) {
            tmxPrepared = null;
            glossaryPrepared = null;
            // commit translations
            try {
                Core.getMainWindow().showStatusMessageRB("TF_COMMIT_TARGET_START");
                remoteRepositoryProvider.switchAllToLatest();
                remoteRepositoryProvider.copyFilesFromProjectToRepo(config.getTargetDir().getUnderRoot(), null);
                remoteRepositoryProvider.commitFiles(config.getTargetDir().getUnderRoot(), "Project translation");
                Core.getMainWindow().showStatusMessageRB("TF_COMMIT_TARGET_DONE");
            } catch (Exception e) {
                Log.logErrorRB("TF_COMMIT_TARGET_ERROR");
                Log.log(e);
                throw new IOException(OStrings.getString("TF_COMMIT_TARGET_ERROR") + "\n"
                        + e.getMessage());
            }
        }
    }

    public void commitSourceFiles(ProjectProperties config) throws Exception {
        if (realProject.isRemoteProject() && config.getSourceDir().isUnderRoot())  {
            try {
                Core.getMainWindow().showStatusMessageRB("TF_COMMIT_START");
                remoteRepositoryProvider.switchAllToLatest();
                remoteRepositoryProvider.copyFilesFromProjectToRepo(config.getSourceDir().getUnderRoot(), null);
                remoteRepositoryProvider.commitFiles(config.getSourceDir().getUnderRoot(), "Commit source files");
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
     * Prepare for future team sync.
     * <p>
     * This method must be executed in the Core.executeExclusively.
     */
    public void teamSyncPrepare(boolean isOnlineMode, ProjectProperties config) throws Exception {
        if (remoteRepositoryProvider == null || realProject.getPreparedStatus() != RealProject.PreparedStatus.NONE || !isOnlineMode) {
            return;
        }
        LOGGER.fine("Prepare team sync");
        tmxPrepared = null;
        glossaryPrepared = null;
        remoteRepositoryProvider.cleanPrepared();

        String tmxPath = config.getProjectInternalRelative() + OConsts.STATUS_EXTENSION;
        if (remoteRepositoryProvider.isUnderMapping(tmxPath)) {
            tmxPrepared = RebaseAndCommit.prepare(remoteRepositoryProvider, config.getProjectRootDir(), tmxPath);
        }

        final String glossaryPath = config.getWritableGlossaryFile().getUnderRoot();
        if (glossaryPath != null && remoteRepositoryProvider.isUnderMapping(glossaryPath)) {
            glossaryPrepared = RebaseAndCommit.prepare(remoteRepositoryProvider, config.getProjectRootDir(),
                    glossaryPath);
        }
        realProject.setPreparedStatus(RealProject.PreparedStatus.PREPARED);
    }

    /**
     * Fast team sync for execute from SaveThread.
     * <p>
     * This method must be executed in the Core.executeExclusively.
     */
    public void teamSync(ProjectProperties config) {
        if (remoteRepositoryProvider == null || realProject.getPreparedStatus() != RealProject.PreparedStatus.PREPARED) {
            return;
        }
        LOGGER.fine("Rebase team sync");
        try {
            realProject.setPreparedStatus(RealProject.PreparedStatus.PREPARED2);
            synchronized (realProject) {
                realProject.getProjectTMX().save(config, config.getProjectInternal() + OConsts.STATUS_EXTENSION,
                        realProject.isProjectModified());
            }
            rebaseAndCommitProject(glossaryPrepared != null,config);
            realProject.setPreparedStatus(RealProject.PreparedStatus.REBASED);

            new Thread(() -> {
                try {
                    Core.executeExclusively(true, () -> {
                        if (realProject.getPreparedStatus() != RealProject.PreparedStatus.REBASED) {
                            return;
                        }
                        LOGGER.fine("Commit team sync");
                        try {
                            String newVersion = RebaseAndCommit.commitPrepared(tmxPrepared, remoteRepositoryProvider,
                                    null);
                            if (glossaryPrepared != null) {
                                RebaseAndCommit.commitPrepared(glossaryPrepared, remoteRepositoryProvider, newVersion);
                            }

                            tmxPrepared = null;
                            glossaryPrepared = null;

                            remoteRepositoryProvider.cleanPrepared();
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
    void rebaseAndCommitProject(boolean processGlossary,ProjectProperties config) throws Exception {
        Log.logInfoRB("TEAM_REBASE_START");

        final String author = Preferences.getPreferenceDefault(Preferences.TEAM_AUTHOR,
                System.getProperty("user.name"));
        final StringBuilder commitDetails = new StringBuilder("Translated by " + author);
        String tmxPath = config.getProjectInternalRelative() + OConsts.STATUS_EXTENSION;
        if (remoteRepositoryProvider.isUnderMapping(tmxPath)) {
            RebaseAndCommit.rebaseAndCommit(tmxPrepared, remoteRepositoryProvider, config.getProjectRootDir(),
                    tmxPath, new RebaseAndCommit.IRebase() {
                        ProjectTMX baseTMX, headTMX;

                        @Override
                        public void parseBaseFile(File file) throws Exception {
                            baseTMX = new ProjectTMX(config.getSourceLanguage(), config
                                    .getTargetLanguage(), config.isSentenceSegmentingEnabled(), file, null);
                        }

                        @Override
                        public void parseHeadFile(File file) throws Exception {
                            headTMX = new ProjectTMX(config.getSourceLanguage(), config
                                    .getTargetLanguage(), config.isSentenceSegmentingEnabled(), file, null);
                        }

                        @Override
                        public void rebaseAndSave(File out) throws Exception {
                            realProject.mergeTMX(baseTMX, headTMX, commitDetails,LOGGER);
                            realProject.getProjectTMX().exportTMX(config, out, false, false, true);
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
            realProject.replaceContentTMX(config);
        }

        if (processGlossary) {
            final String glossaryPath = config.getWritableGlossaryFile().getUnderRoot();
            final File glossaryFile = config.getWritableGlossaryFile().getAsFile();
            new File(config.getProjectRootDir(), glossaryPath);
            if (glossaryPath != null && remoteRepositoryProvider.isUnderMapping(glossaryPath)) {
                final List<GlossaryEntry> glossaryEntries;
                if (glossaryFile.exists()) {
                    glossaryEntries = GlossaryReaderTSV.read(glossaryFile, true);
                } else {
                    glossaryEntries = Collections.emptyList();
                }
                RebaseAndCommit.rebaseAndCommit(glossaryPrepared, remoteRepositoryProvider,
                        config.getProjectRootDir(), glossaryPath, new RebaseAndCommit.IRebase() {
                            List<GlossaryEntry> baseGlossaryEntries, headGlossaryEntries;

                            @Override
                            public void parseBaseFile(File file) throws Exception {
                                if (file.exists()) {
                                    baseGlossaryEntries = GlossaryReaderTSV.read(file, true);
                                } else {
                                    baseGlossaryEntries = new ArrayList<GlossaryEntry>();
                                }
                            }

                            @Override
                            public void parseHeadFile(File file) throws Exception {
                                if (file.exists()) {
                                    headGlossaryEntries = GlossaryReaderTSV.read(file, true);
                                } else {
                                    headGlossaryEntries = new ArrayList<GlossaryEntry>();
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