package org.omegat.core.data;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.events.IProjectEventListener;
import org.omegat.filters2.FilterContext;
import org.omegat.filters2.TranslationException;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.util.FileUtil;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.OStrings;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildTMX {
    private final RealProject realProject;

    public BuildTMX(RealProject realProject) {
        this.realProject = realProject;
    }

    void buildTMXFiles(String sourcePattern, boolean commitTargetFiles) throws IOException, TranslationException {
        Pattern filePattern = Pattern.compile(sourcePattern);

        // build 3 TMX files:
        // - OmegaT-specific, with inline OmegaT formatting tags
        // - TMX Level 1, without formatting tags
        // - TMX Level 2, with OmegaT formatting tags wrapped in TMX inline tags
        try {
            // build TMX with OmegaT tags
            String fname = realProject.getConfig().getProjectRoot() + realProject.getConfig().getProjectName() + OConsts.OMEGAT_TMX
                    + OConsts.TMX_EXTENSION;

            realProject.getProjectTMX().exportTMX(realProject.getConfig(), new File(fname), false, false, false);

            // build TMX level 1 compliant file
            fname = realProject.getConfig().getProjectRoot() + realProject.getConfig().getProjectName() + OConsts.LEVEL1_TMX
                    + OConsts.TMX_EXTENSION;
            realProject.getProjectTMX().exportTMX(realProject.getConfig(), new File(fname), true, false, false);

            // build three-quarter-assed TMX level 2 file
            fname = realProject.getConfig().getProjectRoot() + realProject.getConfig().getProjectName() + OConsts.LEVEL2_TMX
                    + OConsts.TMX_EXTENSION;
            realProject.getProjectTMX().exportTMX(realProject.getConfig(), new File(fname), false, true, false);
        } catch (Exception e) {
            Log.logErrorRB("CT_ERROR_CREATING_TMX");
            Log.log(e);
            throw new IOException(OStrings.getString("CT_ERROR_CREATING_TMX") + "\n" + e.getMessage());
        }

        String srcRoot = realProject.getConfig().getSourceRoot();
        String locRoot = realProject.getConfig().getTargetRoot();

        // build translated files
        FilterMaster fm = Core.getFilterMaster();

        List<String> pathList = FileUtil.buildRelativeFilesList(new File(srcRoot), Collections.emptyList(),
                realProject.getConfig().getSourceRootExcludes());

        RealProject.TranslateFilesCallback translateFilesCallback = new RealProject.TranslateFilesCallback();

        int numberOfCompiled = 0;

        for (String midName : pathList) {
            // shorten filename to that which is relative to src root
            Matcher fileMatch = filePattern.matcher(midName);
            if (fileMatch.matches()) {
                File fn = new File(locRoot, midName);
                if (!fn.getParentFile().exists()) {
                    // target directory doesn't exist - create it
                    if (!fn.getParentFile().mkdirs()) {
                        throw new IOException(OStrings.getString("CT_ERROR_CREATING_TARGET_DIR") + fn.getParentFile());
                    }
                }
                Core.getMainWindow().showStatusMessageRB("CT_COMPILE_FILE_MX", midName);
                translateFilesCallback.fileStarted(midName);
                fm.translateFile(srcRoot, midName, locRoot, new FilterContext(realProject.getConfig()),
                        translateFilesCallback);
                translateFilesCallback.fileFinished();
                numberOfCompiled++;
            }
        }
        if (realProject.getRemoteRepositoryProvider() != null && realProject.getConfig().getTargetDir().isUnderRoot() && commitTargetFiles && realProject.isOnlineMode()) {
            realProject.setTmxPrepared(null);
            realProject.setGlossaryPrepared(null);
            // commit translations
            try {
                Core.getMainWindow().showStatusMessageRB("TF_COMMIT_TARGET_START");
                realProject.getRemoteRepositoryProvider().switchAllToLatest();
                realProject.getRemoteRepositoryProvider().copyFilesFromProjectToRepo(realProject.getConfig().getTargetDir().getUnderRoot(), null);
                realProject.getRemoteRepositoryProvider().commitFiles(realProject.getConfig().getTargetDir().getUnderRoot(), "Project translation");
                Core.getMainWindow().showStatusMessageRB("TF_COMMIT_TARGET_DONE");
            } catch (Exception e) {
                Log.logErrorRB("TF_COMMIT_TARGET_ERROR");
                Log.log(e);
                throw new IOException(OStrings.getString("TF_COMMIT_TARGET_ERROR") + "\n"
                        + e.getMessage());
            }
        }

        if (numberOfCompiled == 1) {
            Core.getMainWindow().showStatusMessageRB("CT_COMPILE_DONE_MX_SINGULAR");
        } else {
            Core.getMainWindow().showStatusMessageRB("CT_COMPILE_DONE_MX");
        }

        CoreEvents.fireProjectChange(IProjectEventListener.PROJECT_CHANGE_TYPE.COMPILE);
    }
}