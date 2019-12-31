package org.omegat.core.data;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.KnownException;
import org.omegat.core.events.IProjectEventListener;
import org.omegat.core.statistics.CalcStandardStatistics;
import org.omegat.core.statistics.Statistics;
import org.omegat.core.team2.IRemoteRepository2;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.Preferences;

public class SavingProject {
    private final RealProject realProject;

    public SavingProject(RealProject realProject) {
        this.realProject = realProject;
    }

    void save(boolean doTeamSync) {
        Core.getAutoSave().disable();
        try {

            Core.getMainWindow().getMainMenu().getProjectMenu().setEnabled(false);
            try {
                Preferences.save();

                try {
                    realProject.saveProjectProperties();

                    realProject.getProjectTMX().save(realProject.getConfig(), realProject.getConfig().getProjectInternal() + OConsts.STATUS_EXTENSION,
                            realProject.isProjectModified());

                    if (realProject.getRemoteRepositoryProvider() != null && doTeamSync) {
                        realProject.setTmxPrepared(null);
                        realProject.setGlossaryPrepared(null);
                        realProject.getRemoteRepositoryProvider().cleanPrepared();
                        Core.getMainWindow().showStatusMessageRB("TEAM_SYNCHRONIZE");
                        realProject.rebaseAndCommitProject(true);
                        realProject.setOnlineMode();
                    }

                    realProject.setProjectModified(false);
                } catch (KnownException ex) {
                    throw ex;
                } catch (IRemoteRepository2.NetworkException e) {
                    if (realProject.isOnlineMode()) {
                        Log.logErrorRB("TEAM_NETWORK_ERROR", e.getCause());
                        realProject.setOfflineMode();
                    }
                } catch (Exception e) {
                    Log.logErrorRB(e, "CT_ERROR_SAVING_PROJ");
                    Core.getMainWindow().displayErrorRB(e, "CT_ERROR_SAVING_PROJ");
                }

                LastSegmentManager.saveLastSegment();

                // update statistics
                String stat = CalcStandardStatistics.buildProjectStats(realProject, realProject.getHotStat());
                String fn = realProject.getConfig().getProjectInternal() + OConsts.STATS_FILENAME;
                Statistics.writeStat(fn, stat);
            } finally {
                Core.getMainWindow().getMainMenu().getProjectMenu().setEnabled(true);
            }

            CoreEvents.fireProjectChange(IProjectEventListener.PROJECT_CHANGE_TYPE.SAVE);
        } finally {
            Core.getAutoSave().enable();
        }
    }
}