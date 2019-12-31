package org.omegat.core.data;

import org.omegat.core.Core;
import org.omegat.filters2.FilterContext;
import org.omegat.filters2.IFilter;
import org.omegat.filters2.TranslationException;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.util.FileUtil;
import org.omegat.util.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LoadingProject {
    private final RealProject realProject;

    public LoadingProject(RealProject realProject) {
        this.realProject = realProject;
    }

    void load() throws IOException, TranslationException {
        FilterMaster fm = Core.getFilterMaster();

        File root = new File(realProject.getConfig().getSourceRoot());
        List<String> srcPathList = FileUtil
                .buildRelativeFilesList(root, Collections.emptyList(), realProject.getConfig().getSourceRootExcludes()).stream()
                .sorted(StreamUtil.comparatorByList(realProject.getSourceFilesOrder())).collect(Collectors.toList());

        for (String filepath : srcPathList) {
            Core.getMainWindow().showStatusMessageRB("CT_LOAD_FILE_MX", filepath);

            RealProject.LoadFilesCallback loadFilesCallback = new RealProject.LoadFilesCallback(realProject.getExistSource(), realProject.getExistKeys(), realProject.getTransMemories());

            IProject.FileInfo fi = new IProject.FileInfo();
            fi.filePath = filepath;

            loadFilesCallback.setCurrentFile(fi);

            IFilter filter = fm.loadFile(realProject.getConfig().getSourceRoot() + filepath, new FilterContext(realProject.getConfig()),
                    loadFilesCallback);

            loadFilesCallback.fileFinished();

            if (filter != null && !fi.entries.isEmpty()) {
                fi.filterClass = filter.getClass(); //Don't store the instance, because every file gets an instance and
                // then we consume a lot of memory for all instances.
                //See also IFilter "TODO: each filter should be stateless"
                fi.filterFileFormatName = filter.getFileFormatName();
                try {
                    fi.fileEncoding = filter.getInEncodingLastParsedFile();
                } catch (Error e) { // In case a filter doesn't have getInEncodingLastParsedFile() (e.g., Okapi plugin)
                    fi.fileEncoding = "";
                }
                realProject.getProjectFilesList().add(fi);
            }
        }

        realProject.findNonUniqueSegments();

        Core.getMainWindow().showStatusMessageRB("CT_LOAD_SRC_COMPLETE");
    }
}