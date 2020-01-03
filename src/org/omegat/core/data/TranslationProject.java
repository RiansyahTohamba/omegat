package org.omegat.core.data;

import org.omegat.core.Core;
import org.omegat.util.*;
import org.xml.sax.SAXParseException;

import java.io.File;

public class TranslationProject {
    private final RealProject realProject;

    public TranslationProject(RealProject realProject) {
        this.realProject = realProject;
    }

    public void setTranslation(SourceTextEntry entry, PrepareTMXEntry trans, boolean defaultTranslation,
                               TMXEntry.ExternalLinked externalLinked, IProject.AllTranslations previous) throws IProject.OptimisticLockingFail {
        if (trans == null) {
            throw new IllegalArgumentException("RealProject.setTranslation(tr) can't be null");
        }

        synchronized (realProject.getProjectTMX()) {
            realProject.getVersioningProject().syncSetTranslation(entry, trans, defaultTranslation, externalLinked, previous);
        }
    }

    public void setTranslation(final SourceTextEntry entry, final PrepareTMXEntry trans, boolean defaultTranslation,
                               TMXEntry.ExternalLinked externalLinked) {
        if (trans == null) {
            throw new IllegalArgumentException("RealProject.setTranslation(tr) can't be null");
        }

        translateEntry(entry, trans, defaultTranslation, externalLinked);
    }

    public void translateEntry(SourceTextEntry entry, PrepareTMXEntry trans, boolean defaultTranslation, TMXEntry.ExternalLinked externalLinked) {
        TMXEntry prevTrEntry = defaultTranslation ? realProject.getProjectTMX().getDefaultTranslation(entry.getSrcText())
                : realProject.getProjectTMX().getMultipleTranslation(entry.getKey());

        trans.changer = Preferences.getPreferenceDefault(Preferences.TEAM_AUTHOR,
                System.getProperty("user.name"));
        trans.changeDate = System.currentTimeMillis();

        if (prevTrEntry == null) {
            // there was no translation yet
            prevTrEntry = RealProject.EMPTY_TRANSLATION;
            trans.creationDate = trans.changeDate;
            trans.creator = trans.changer;
        } else {
            trans.creationDate = prevTrEntry.creationDate;
            trans.creator = prevTrEntry.creator;
        }

        if (StringUtil.isEmpty(trans.note)) {
            trans.note = null;
        }

        trans.source = entry.getSrcText();

        TMXEntry newTrEntry;

        if (trans.translation == null && trans.note == null) {
            // no translation, no note
            newTrEntry = null;
        } else {
            newTrEntry = new TMXEntry(trans, defaultTranslation, externalLinked);
        }

        realProject.setProjectModified(true);

        realProject.getProjectTMX().setTranslation(entry, newTrEntry, defaultTranslation);

        /**
         * Calculate how to statistics should be changed.
         */
        int diff = prevTrEntry.translation == null ? 0 : -1;
        diff += trans.translation == null ? 0 : +1;
        realProject.getHotStat().numberofTranslatedSegments = Math.max(0,
                Math.min(realProject.getHotStat().numberOfUniqueSegments, realProject.getHotStat().numberofTranslatedSegments + diff));
    }

    private class TranslateFilesCallback extends TranslateEntry {
        private String currentFile;

        /**
         * Getter for currentFile
         *
         * @return the current file being processed
         */
        @Override
        protected String getCurrentFile() {
            return currentFile;
        }

        TranslateFilesCallback() {
            realProject.TranslateEntry(realProject.getConfig());
        }

        protected void fileStarted(String fn) {
            currentFile = realProject.patchFileNameForEntryKey(fn);
            super.fileStarted();
        }

        protected String getSegmentTranslation(String id, int segmentIndex, String segmentSource,
                                               String prevSegment, String nextSegment, String path) {
            EntryKey ek = new EntryKey(currentFile, segmentSource, id, prevSegment, nextSegment, path);
            TMXEntry tr = realProject.getProjectTMX().getMultipleTranslation(ek);
            if (tr == null) {
                tr = realProject.getProjectTMX().getDefaultTranslation(ek.sourceText);
            }
            return tr != null ? tr.translation : null;
        }
    }

}