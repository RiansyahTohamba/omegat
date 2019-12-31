package org.omegat.core.data;

import org.omegat.core.Core;
import org.omegat.core.KnownException;
import org.omegat.core.segmentation.SRX;
import org.omegat.core.segmentation.Segmenter;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.Preferences;

import java.util.Collections;

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

            realProject.loadSourceFiles();

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
}