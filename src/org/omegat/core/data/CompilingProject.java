package org.omegat.core.data;

import org.omegat.core.Core;
import org.omegat.core.threads.CommandMonitor;
import org.omegat.util.Log;
import org.omegat.util.Preferences;
import org.omegat.util.StaticUtils;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.UIThreadsUtil;

import java.io.IOException;

public class CompilingProject {
    private final RealProject realProject;

    public CompilingProject(RealProject realProject) {
        this.realProject = realProject;
    }

    /**
     * Builds translated files corresponding to sourcePattern and creates fresh TM files. Convenience method. Assumes we
     * want to run external post-processing commands.
     *
     * @param sourcePattern The regexp of files to create
     * @throws Exception
     */
    public void compileProject(String sourcePattern) throws Exception {
        compileProject(sourcePattern, true);
    }

    /**
     * Builds translated files corresponding to sourcePattern and creates fresh TM files.
     * Calls the actual compile project method, assumes we don't want to commit target files.
     *
     * @param sourcePattern    The regexp of files to create
     * @param doPostProcessing Whether or not we should perform external post-processing.
     * @throws Exception
     */
    public void compileProject(String sourcePattern, boolean doPostProcessing) throws Exception {
        realProject.compileProjectAndCommit(sourcePattern, doPostProcessing, false);
    }

    /**
     * Builds translated files corresponding to sourcePattern and creates fresh TM files.
     *
     * @param sourcePattern     The regexp of files to create
     * @param doPostProcessing  Whether or not we should perform external post-processing.
     * @param commitTargetFiles Whether or not we should commit target files
     * @throws Exception
     */
    public void compileProjectAndCommit(String sourcePattern, boolean doPostProcessing, boolean commitTargetFiles)
            throws Exception {
        Log.logInfoRB("LOG_DATAENGINE_COMPILE_START");
        UIThreadsUtil.mustNotBeSwingThread();

        realProject.getBuildTMX().buildTMXFiles(sourcePattern, commitTargetFiles);

        if (doPostProcessing) {

            // Kill any processes still not complete
            realProject.flushProcessCache();

            if (Preferences.isPreference(Preferences.ALLOW_PROJECT_EXTERN_CMD)) {
                doExternalCommand(realProject.getConfig().getExternalCommand());
            }
            doExternalCommand(Preferences.getPreference(Preferences.EXTERNAL_COMMAND));
        }

        Log.logInfoRB("LOG_DATAENGINE_COMPILE_END");
    }

    private void doExternalCommand(String command) {

        if (StringUtil.isEmpty(command)) {
            return;
        }

        Core.getMainWindow().showStatusMessageRB("CT_START_EXTERNAL_CMD");

        CommandVarExpansion expander = new CommandVarExpansion(command);
        command = expander.expandVariables(config);
        Log.log("Executing command: " + command);
        try {
            Process p = Runtime.getRuntime().exec(StaticUtils.parseCLICommand(command));
            processCache.push(p);
            CommandMonitor stdout = CommandMonitor.newStdoutMonitor(p);
            CommandMonitor stderr = CommandMonitor.newStderrMonitor(p);
            stdout.start();
            stderr.start();
        } catch (IOException e) {
            String message;
            Throwable cause = e.getCause();
            if (cause == null) {
                message = e.getLocalizedMessage();
            } else {
                message = cause.getLocalizedMessage();
            }
            Core.getMainWindow().showStatusMessageRB("CT_ERROR_STARTING_EXTERNAL_CMD", message);
        }
    }
}