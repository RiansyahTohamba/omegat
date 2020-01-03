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
import org.xml.sax.SAXParseException;

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

            createDirectory(realProject.getConfig().getProjectRoot(), null);
            createDirectory(realProject.getConfig().getProjectInternal(), OConsts.DEFAULT_INTERNAL);
            createDirectory(realProject.getConfig().getSourceRoot(), OConsts.DEFAULT_SOURCE);
            createDirectory(realProject.getConfig().getGlossaryRoot(), OConsts.DEFAULT_GLOSSARY);
            createDirectory(realProject.getConfig().getTMRoot(), OConsts.DEFAULT_TM);
            createDirectory(realProject.getConfig().getTMAutoRoot(), OConsts.AUTO_TM);
            createDirectory(realProject.getConfig().getDictRoot(), OConsts.DEFAULT_DICT);
            createDirectory(realProject.getConfig().getTargetRoot(), OConsts.DEFAULT_TARGET);
            //createDirectory(m_realProject.getConfig().getTMOtherLangRoot(), OConsts.DEFAULT_OTHERLANG);

            realProject.saveProjectProperties();

            // Set project specific segmentation rules if they exist, or
            // defaults otherwise.
            SRX srx = realProject.getConfig().getProjectSRX();
            Core.setSegmenter(new Segmenter(srx == null ? Preferences.getSRX() : srx));

            loadTranslations();
            realProject.setProjectModified(true);
            realProject.saveProject(false);

            loadSourceFiles();

            realProject.setAllProjectEntries(Collections.unmodifiableList(realProject.getAllProjectEntries()));
            realProject.setImportHandler(new ImportFromAutoTMX(realProject, realProject.getAllProjectEntries()));

            importTranslationsFromSources();

            loadTM();

            loadOtherLanguages();

            realProject.setLoaded(true);

            // clear status message
            Core.getMainWindow().showStatusMessageRB(null);
        } catch (Exception e) {
            // trouble in tinsletown...
            Log.logErrorRB(e, "CT_ERROR_CREATING_PROJECT");
            Core.getMainWindow().displayErrorRB(e, "CT_ERROR_CREATING_PROJECT");
        }
    }

    /**
     * This method imports translation from source files into ProjectTMX.
     *
     * If there are multiple segments with equals source, then first
     * translations will be loaded as default, all other translations will be
     * loaded as alternative.
     *
     * We shouldn't load translation from source file(even as alternative) when
     * default translation already exists in project_save.tmx. So, only first
     * load will be possible.
     */
    void importTranslationsFromSources() {
        // which default translations we added - allow to add alternatives
        // except the same translation
        Map<String, String> allowToImport = new HashMap<String, String>();

        for (IProject.FileInfo fi : projectFilesList) {
            for (int i = 0; i < fi.entries.size(); i++) {
                SourceTextEntry ste = fi.entries.get(i);
                if (ste.getSourceTranslation() == null || ste.isSourceTranslationFuzzy()
                        || ste.getSrcText().equals(ste.getSourceTranslation()) && !allowTranslationEqualToSource) {
                    // There is no translation in source file, or translation is fuzzy
                    // or translation = source and Allow translation to be equal to source is false
                    continue;
                }

                PrepareTMXEntry prepare = new PrepareTMXEntry();
                prepare.source = ste.getSrcText();
                // project with default translations
                if (config.isSupportDefaultTranslations()) {
                    // can we import as default translation ?
                    TMXEntry enDefault = projectTMX.getDefaultTranslation(ste.getSrcText());
                    if (enDefault == null) {
                        // default not exist yet - yes, we can
                        prepare.translation = ste.getSourceTranslation();
                        projectTMX.setTranslation(ste, new TMXEntry(prepare, true, null), true);
                        allowToImport.put(ste.getSrcText(), ste.getSourceTranslation());
                    } else {
                        // default translation already exist - did we just
                        // imported it ?
                        String justImported = allowToImport.get(ste.getSrcText());
                        // can we import as alternative translation ?
                        if (justImported != null && !ste.getSourceTranslation().equals(justImported)) {
                            // we just imported default and it doesn't equals to
                            // current - import as alternative
                            prepare.translation = ste.getSourceTranslation();
                            projectTMX.setTranslation(ste, new TMXEntry(prepare, false, null), false);
                        }
                    }
                } else { // project without default translations
                    // can we import as alternative translation ?
                    TMXEntry en = projectTMX.getMultipleTranslation(ste.getKey());
                    if (en == null) {
                        // not exist yet - yes, we can
                        prepare.translation = ste.getSourceTranslation();
                        projectTMX.setTranslation(ste, new TMXEntry(prepare, false, null), false);
                    }
                }
            }
        }
    }
    /**
     * Create the given directory if it does not exist yet.
     *
     * @param dir the directory path to create
     * @param dirType the directory name to show in IOException
     * @throws IOException when directory could not be created.
     */
    private void createDirectory(final String dir, final String dirType) throws IOException {
        File d = new File(dir);
        if (!d.isDirectory()) {
            if (!d.mkdirs()) {
                StringBuilder msg = new StringBuilder(OStrings.getString("CT_ERROR_CREATE"));
                if (dirType != null) {
                    msg.append("\n(.../").append(dirType).append("/)");
                }
                throw new IOException(msg.toString());
            }
        }
    }
    private void loadSourceFiles() throws IOException, TranslationException {
        long st = System.currentTimeMillis();

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
        long en = System.currentTimeMillis();
        Log.log("Load project source files: " + (en - st) + "ms");
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
        Filters filters = Optional.ofNullable(realProject.getConfig().getProjectFilters()).orElse(Preferences.getFilters());
        Core.setFilterMaster(new FilterMaster(filters));
    }

    /**
     * Load segmentation settings, either from the project or from global options
     */
    private void loadSegmentationSettings(ProjectProperties config) {
        // Set project specific segmentation rules if they exist, or defaults otherwise.
        // This MUST happen before calling loadTranslations(), because projectTMX needs a segmenter.
        SRX srx = Optional.ofNullable(realProject.getConfig().getProjectSRX()).orElse(Preferences.getSRX());
        Core.setSegmenter(new Segmenter(srx));
    }

    /** Finds and loads project's TMX file with translations (project_save.tmx). */
    private void loadTranslations() throws Exception {
        File file = new File(
                realProject.getConfig().getProjectInternalDir(), OConsts.STATUS_EXTENSION);

        try {
            Core.getMainWindow().showStatusMessageRB("CT_LOAD_TMX");

            projectTMX = new ProjectTMX(realProject.getConfig().getSourceLanguage(), realProject.getConfig().getTargetLanguage(),
                    realProject.getConfig().isSentenceSegmentingEnabled(), file, checkOrphanedCallback);
            if (file.exists()) {
                // RFE 1001918 - backing up project's TMX upon successful read
                // TODO check for repositories
                FileUtil.backupFile(file);
                FileUtil.removeOldBackups(file, OConsts.MAX_BACKUPS);
            }
        } catch (SAXParseException ex) {
            Log.logErrorRB(ex, "TMXR_FATAL_ERROR_WHILE_PARSING", ex.getLineNumber(), ex.getColumnNumber());
            throw ex;
        } catch (Exception ex) {
            Log.logErrorRB(ex, "TMXR_EXCEPTION_WHILE_PARSING", file.getAbsolutePath(), Log.getLogLocation());
            throw ex;
        }
    }

    /**
     * Locates and loads external TMX files with legacy translations. Uses directory monitor for check file
     * updates.
     */
    private void loadTM(ProjectProperties config) throws IOException {
        File tmRoot = new File(config.getTMRoot());
        tmMonitor = new DirectoryMonitor(tmRoot, file -> {
            if (!ExternalTMFactory.isSupported(file)) {
                // not a TMX file
                return;
            }
            if (file.getPath().replace('\\', '/').startsWith(config.getTMOtherLangRoot())) {
                // tmx in other language, which is already shown in editor. Skip it.
                return;
            }
            // create new translation memories map
            Map<String, ExternalTMX> newTransMemories = new TreeMap<>(transMemories);
            if (file.exists()) {
                try {
                    ExternalTMX newTMX = ExternalTMFactory.load(file);
                    newTransMemories.put(file.getPath(), newTMX);

                    // Please note the use of "/". FileUtil.computeRelativePath rewrites all other
                    // directory separators into "/".
                    if (FileUtil.computeRelativePath(tmRoot, file).startsWith(OConsts.AUTO_TM + "/")) {
                        appendFromAutoTMX(newTMX, false);
                    } else if (FileUtil.computeRelativePath(tmRoot, file)
                            .startsWith(OConsts.AUTO_ENFORCE_TM + '/')) {
                        appendFromAutoTMX(newTMX, true);
                    }

                } catch (Exception e) {
                    String filename = file.getPath();
                    Log.logErrorRB(e, "TF_TM_LOAD_ERROR", filename);
                    Core.getMainWindow().displayErrorRB(e, "TF_TM_LOAD_ERROR", filename);
                }
            } else {
                newTransMemories.remove(file.getPath());
            }
            transMemories = newTransMemories;
        });
        tmMonitor.checkChanges();
        tmMonitor.start();
    }

    /**
     * Locates and loads external TMX files with legacy translations. Uses directory monitor for check file
     * updates.
     */
    private void loadOtherLanguages(ProjectProperties config) throws IOException {
        File tmOtherLanguagesRoot = new File(config.getTMOtherLangRoot());
        tmOtherLanguagesMonitor = new DirectoryMonitor(tmOtherLanguagesRoot, file -> {
            String name = file.getName();
            if (!name.matches("[A-Z]{2}([-_][A-Z]{2})?\\.tmx")) {
                // not a TMX file in XX_XX.tmx format
                return;
            }
            Language targetLanguage = new Language(name.substring(0, name.length() - ".tmx".length()));
            // create new translation memories map
            Map<Language, ProjectTMX> newOtherTargetLangTMs = new TreeMap<>(otherTargetLangTMs);
            if (file.exists()) {
                try {
                    ProjectTMX newTMX = new ProjectTMX(config.getSourceLanguage(), targetLanguage,
                            config.isSentenceSegmentingEnabled(), file, checkOrphanedCallback);
                    newOtherTargetLangTMs.put(targetLanguage, newTMX);
                } catch (Exception e) {
                    String filename = file.getPath();
                    Log.logErrorRB(e, "TF_TM_LOAD_ERROR", filename);
                    Core.getMainWindow().displayErrorRB(e, "TF_TM_LOAD_ERROR", filename);
                }
            } else {
                newOtherTargetLangTMs.remove(targetLanguage);
            }
            otherTargetLangTMs = newOtherTargetLangTMs;
        });
        tmOtherLanguagesMonitor.checkChanges();
        tmOtherLanguagesMonitor.start();
    }

}