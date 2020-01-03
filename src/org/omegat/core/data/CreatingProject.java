package org.omegat.core.data;

import gen.core.filters.Filters;
import org.omegat.core.Core;
import org.omegat.core.KnownException;
import org.omegat.core.segmentation.SRX;
import org.omegat.core.segmentation.Segmenter;
import org.omegat.filters2.FilterContext;
import org.omegat.filters2.IFilter;
import org.omegat.filters2.TranslationException;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.util.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class CreatingProject {
    private final RealProject realProject;

    public CreatingProject(RealProject realProject) {
        this.realProject = realProject;
    }

    void create() {
        try {
            if (!realProject.lockProject()) {
                throw new KnownException("PROJECT_LOCKED");
            }

            realProject.createDirectory(realProject.getConfig().getProjectRoot(), null);
            realProject.createDirectory(realProject.getConfig().getProjectInternal(), OConsts.DEFAULT_INTERNAL);
            realProject.createDirectory(realProject.getConfig().getSourceRoot(), OConsts.DEFAULT_SOURCE);
            realProject.createDirectory(realProject.getConfig().getGlossaryRoot(), OConsts.DEFAULT_GLOSSARY);
            realProject.createDirectory(realProject.getConfig().getTMRoot(), OConsts.DEFAULT_TM);
            realProject.createDirectory(realProject.getConfig().getTMAutoRoot(), OConsts.AUTO_TM);
            realProject.createDirectory(realProject.getConfig().getDictRoot(), OConsts.DEFAULT_DICT);
            realProject.createDirectory(realProject.getConfig().getTargetRoot(), OConsts.DEFAULT_TARGET);
            //createDirectory(m_config.getTMOtherLangRoot(), OConsts.DEFAULT_OTHERLANG);

            realProject.saveProjectProperties();

            // Set project specific segmentation rules if they exist, or
            // defaults otherwise.
            SRX srx = realProject.getConfig().getProjectSRX();
            Core.setSegmenter(new Segmenter(srx == null ? Preferences.getSRX() : srx));

            realProject.loadTranslations();
            realProject.setProjectModified(true);
            realProject.saveProject(false);

            loadSourceFiles();

            realProject.setAllProjectEntries(Collections.unmodifiableList(realProject.getAllProjectEntries()));
            realProject.setImportHandler(new ImportFromAutoTMX(realProject, realProject.getAllProjectEntries()));

            realProject.importTranslationsFromSources();

            realProject.loadTM();

            realProject.loadOtherLanguages();

            realProject.setLoaded(true);

            // clear status message
            Core.getMainWindow().showStatusMessageRB(null);
        } catch (Exception e) {
            // trouble in tinsletown...
            Log.logErrorRB(e, "CT_ERROR_CREATING_PROJECT");
            Core.getMainWindow().displayErrorRB(e, "CT_ERROR_CREATING_PROJECT");
        }
    }
    void loadSourceFiles() throws IOException, TranslationException {
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

        findNonUniqueSegments();

        Core.getMainWindow().showStatusMessageRB("CT_LOAD_SRC_COMPLETE");
    }

    protected void findNonUniqueSegments() {
        Map<String, SourceTextEntry> exists = new HashMap<String, SourceTextEntry>(16384);

        for (IProject.FileInfo fi : realProject.getProjectFilesList()) {
            for (int i = 0; i < fi.entries.size(); i++) {
                SourceTextEntry ste = fi.entries.get(i);
                SourceTextEntry prevSte = exists.get(ste.getSrcText());

                if (prevSte == null) {
                    // Note first appearance of this STE
                    exists.put(ste.getSrcText(), ste);
                } else {
                    // Note duplicate of already-seen STE
                    if (prevSte.duplicates == null) {
                        prevSte.duplicates = new ArrayList<SourceTextEntry>();
                    }
                    prevSte.duplicates.add(ste);
                    ste.firstInstance = prevSte;
                }
            }
        }
    }

    /**
     * Load filter settings, either from the project or from global options
     */
    private void loadFilterSettings(ProjectProperties config) {
        // Set project specific file filters if they exist, or defaults otherwise.
        // This MUST happen before calling loadTranslations() because the setting to ignore file context
        // for alt translations is a filter setting, and it affects how alt translations are hashed.
        Filters filters = Optional.ofNullable(config.getProjectFilters()).orElse(Preferences.getFilters());
        Core.setFilterMaster(new FilterMaster(filters));
    }

    /**
     * Load segmentation settings, either from the project or from global options
     */
    private void loadSegmentationSettings(ProjectProperties config) {
        // Set project specific segmentation rules if they exist, or defaults otherwise.
        // This MUST happen before calling loadTranslations(), because projectTMX needs a segmenter.
        SRX srx = Optional.ofNullable(config.getProjectSRX()).orElse(Preferences.getSRX());
        Core.setSegmenter(new Segmenter(srx));
    }
}