package org.omegat.core.data;

import org.omegat.core.Core;
import org.omegat.core.KnownException;
import org.omegat.core.statistics.CalcStandardStatistics;
import org.omegat.core.statistics.Statistics;
import org.omegat.core.team2.IRemoteRepository2;
import org.omegat.core.team2.RemoteRepositoryProvider;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.Preferences;
import org.omegat.util.RuntimePreferences;
import org.omegat.util.gui.UIThreadsUtil;

import java.io.File;
import java.util.Collections;

public class BigLoad {
    private final RealProject realProject;

    public BigLoad(RealProject realProject) {
        this.realProject = realProject;
    }

    /**
     * Load exist project in a "big" sense -- loads project's properties, glossaries, tms, source files etc.
     */
    public synchronized void loadProject(boolean onlineMode) {
        Log.logInfoRB("LOG_DATAENGINE_LOAD_START");
        UIThreadsUtil.mustNotBeSwingThread();

        // load new project
        try {
            if (!realProject.lockProject()) {
                throw new KnownException("PROJECT_LOCKED");
            }
            realProject.setOnlineMode(onlineMode);

            if (RuntimePreferences.isLocationSaveEnabled()) {
                Preferences.setPreference(Preferences.CURRENT_FOLDER,
                        new File(realProject.getConfig().getProjectRoot()).getAbsoluteFile().getParent());
                Preferences.save();
            }

            Core.getMainWindow().showStatusMessageRB("CT_LOADING_PROJECT");

            if (realProject.getRemoteRepositoryProvider() != null) {
                try {
                    realProject.setTmxPrepared(null);
                    realProject.setGlossaryPrepared(null);

                    realProject.getRemoteRepositoryProvider().switchAllToLatest();
                } catch (IRemoteRepository2.NetworkException e) {
                    Log.logErrorRB("TEAM_NETWORK_ERROR", e.getCause());
                    realProject.setOfflineMode();
                }

                realProject.getRemoteRepositoryProvider().copyFilesFromRepoToProject("", '/' + RemoteRepositoryProvider.REPO_SUBDIR,
                        '/' + RemoteRepositoryProvider.REPO_GIT_SUBDIR, '/' + RemoteRepositoryProvider.REPO_SVN_SUBDIR,
                        '/' + OConsts.FILE_PROJECT,
                        '/' + realProject.getConfig().getProjectInternalRelative() + OConsts.STATUS_EXTENSION,
                        '/' + realProject.getConfig().getWritableGlossaryFile().getUnderRoot(),
                        '/' + realProject.getConfig().getTargetDir().getUnderRoot());

                // After adding filters.xml and segmentation.conf, we must reload them again
                realProject.getConfig().loadProjectFilters();
                realProject.getConfig().loadProjectSRX();
            }

            realProject.loadFilterSettings();
            realProject.loadSegmentationSettings();
            realProject.loadTranslations();
            realProject.loadSourceFiles();

            // This MUST happen after calling loadTranslations()
            if (realProject.getRemoteRepositoryProvider() != null && realProject.isOnlineMode()) {
                Core.getMainWindow().showStatusMessageRB("TEAM_REBASE_AND_COMMIT");
                realProject.rebaseAndCommitProject(true);
            }

            realProject.setAllProjectEntries(Collections.unmodifiableList(realProject.getAllProjectEntries()));
            realProject.setImportHandler(new ImportFromAutoTMX(realProject, realProject.getAllProjectEntries()));

            realProject.importTranslationsFromSources();

            realProject.loadTM();

            realProject.loadOtherLanguages();

            // build word count
            String stat = CalcStandardStatistics.buildProjectStats(realProject, realProject.getHotStat());
            String fn = realProject.getConfig().getProjectInternal() + OConsts.STATS_FILENAME;
            Statistics.writeStat(fn, stat);

            realProject.setLoaded(true);

            // Project Loaded...
            Core.getMainWindow().showStatusMessageRB(null);

            realProject.setProjectModified(false);
        } catch (Exception e) {
            Log.logErrorRB(e, "TF_LOAD_ERROR");
            Core.getMainWindow().displayErrorRB(e, "TF_LOAD_ERROR");
            if (!realProject.isLoaded()) {
                realProject.unlockProject();
            }
        } catch (OutOfMemoryError oome) {
            // Fix for bug 1571944 @author Henry Pijffers
            // (henry.pijffers@saxnot.com)

            // Oh shit, we're all out of storage space!
            // Of course we should've cleaned up after ourselves earlier,
            // but since we didn't, do a bit of cleaning up now, otherwise
            // we can't even inform the user about our slacking off.
            realProject.getAllProjectEntries().clear();
            realProject.getProjectFilesList().clear();
            realProject.getTransMemories().clear();
            realProject.setProjectTMX(null);

            // There, that should do it, now inform the user
            long memory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
            Log.logErrorRB("OUT_OF_MEMORY", memory);
            Log.log(oome);
            Core.getMainWindow().showErrorDialogRB("TF_ERROR", "OUT_OF_MEMORY", memory);
            // Just quit, we can't help it anyway
            System.exit(0);
        }

        Log.logInfoRB("LOG_DATAENGINE_LOAD_END");
    }
}