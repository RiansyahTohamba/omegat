package org.omegat.core.data;

import org.omegat.core.Core;
import org.omegat.filters2.FilterContext;
import org.omegat.filters2.IAlignCallback;
import org.omegat.filters2.IFilter;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.util.FileUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AligningProject {
    private final RealProject realProject;

    public AligningProject(RealProject realProject) {
        this.realProject = realProject;
    }

    /**
     * Align project.
     */
    public Map<String, TMXEntry> align(final ProjectProperties props, final File translatedDir)
            throws Exception {
        FilterMaster fm = Core.getFilterMaster();

        File root = new File(realProject.getConfig().getSourceRoot());
        List<File> srcFileList = FileUtil.buildFileList(root, true);

        RealProject.AlignFilesCallback alignFilesCallback = new RealProject.AlignFilesCallback(props);

        String srcRoot = realProject.getConfig().getSourceRoot();
        for (File file : srcFileList) {
            // shorten filename to that which is relative to src root
            String midName = file.getPath().substring(srcRoot.length());

            fm.alignFile(srcRoot, midName, translatedDir.getPath(), new FilterContext(props),
                    alignFilesCallback);
        }
        return alignFilesCallback.data;
    }

    static class AlignFilesCallback implements IAlignCallback {
        AlignFilesCallback(ProjectProperties props) {
            realProject.Object();
            this.config = props;
        }

        Map<String, TMXEntry> data = new HashMap<String, TMXEntry>();
        private ProjectProperties config;

        @Override
        public void addTranslation(String id, String source, String translation, boolean isFuzzy, String path,
                                   IFilter filter) {
            if (source != null && translation != null) {
                realProject.translate(source, translation, isFuzzy, filter);
            }
        }

        public void translate(String source, String translation, boolean isFuzzy, IFilter filter) {
            ParseEntry.ParseEntryResult spr = new ParseEntry.ParseEntryResult();
            boolean removeSpaces = Core.getFilterMaster().getConfig().isRemoveSpacesNonseg();
            String sourceS = ParseEntry.stripSomeChars(source, spr, config.isRemoveTags(), removeSpaces);
            String transS = ParseEntry.stripSomeChars(translation, spr, config.isRemoveTags(), removeSpaces);

            PrepareTMXEntry tr = new PrepareTMXEntry();
            if (config.isSentenceSegmentingEnabled()) {
                List<String> segmentsSource = Core.getSegmenter().segment(config.getSourceLanguage(), sourceS, null,
                        null);
                List<String> segmentsTranslation = Core.getSegmenter()
                        .segment(config.getTargetLanguage(), transS, null, null);
                if (segmentsTranslation.size() != segmentsSource.size()) {
                    if (isFuzzy) {
                        transS = "[" + filter.getFuzzyMark() + "] " + transS;
                    }
                    tr.source = sourceS;
                    tr.translation = transS;
                    data.put(sourceS, new TMXEntry(tr, true, null));
                } else {
                    for (short i = 0; i < segmentsSource.size(); i++) {
                        String oneSrc = segmentsSource.get(i);
                        String oneTrans = segmentsTranslation.get(i);
                        if (isFuzzy) {
                            oneTrans = "[" + filter.getFuzzyMark() + "] " + oneTrans;
                        }
                        tr.source = oneSrc;
                        tr.translation = oneTrans;
                        data.put(sourceS, new TMXEntry(tr, true, null));
                    }
                }
            } else {
                if (isFuzzy) {
                    transS = "[" + filter.getFuzzyMark() + "] " + transS;
                }
                tr.source = sourceS;
                tr.translation = transS;
                data.put(sourceS, new TMXEntry(tr, true, null));
            }
        }
    }
}