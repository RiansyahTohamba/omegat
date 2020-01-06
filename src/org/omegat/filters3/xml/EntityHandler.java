package org.omegat.filters3.xml;

import org.omegat.filters2.TranslationException;
import org.omegat.util.StringUtil;
import org.w3c.dom.DocumentType;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class EntityHandler {
    private static final String START_JARSCHEMA = "jar:";
    /** Internal entity just started. */
    private Entity internalEntityStarted = null;


    /**
     * Is called when the entity starts. Tries to find out whether it's an
     * internal entity, and if so, turns on the trigger to queue entity, and not
     * the text it represents, in {@link #characters(char[],int,int)}.
     */
    public void doStartEntity(String name) {
        if (inDTD) {
            return;
        }
        internalEntityStarted = internalEntities.get(name);
    }

    /** Finds external entity by publicId and systemId. */
    public Entity findExternalEntity(String publicId, String systemId) {
        if (publicId == null && systemId == null) {
            return null;
        }
        for (Entity entity : externalEntities) {
            if (entity.getType() != Entity.Type.EXTERNAL) {
                continue;
            }
            if (StringUtil.equal(publicId, entity.getPublicId())
                    && StringUtil.equal(systemId, entity.getSystemId())) {
                return entity;
            }
        }
        return null;
    }

    /** Whether the file with given systemId is in source folder. */
    private boolean isInSource(String systemId) throws URISyntaxException, MalformedURLException {
        if (systemId.startsWith(START_FILESCHEMA)) {
            File thisOutFile = new File(new URI(systemId));
            if (thisOutFile.getAbsolutePath().startsWith(getSourceFolderAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns external files this handler has processed, because they were
     * included into main file. Each entry is {@link File}.
     */
    public List<File> getProcessedFiles() {
        return processedFiles.isEmpty() ? null : processedFiles;
    }
    /**
     * External files this handler has processed, because they were included
     * into main file. Each entry is of type {@link File}.
     */
    private List<File> processedFiles = new ArrayList<File>();
    /**
     * Is called when the entity is ended. Tries to find out whether it's an
     * external entity we created a writer for, and if so, closes the writer and
     * nulls the entity.
     */
    public void doEndEntity(String name, DTD dtd) throws SAXException, TranslationException, IOException {
        if (inDTD || extEntity == null) {
            return;
        }
        if (extEntity.getOriginalName().equals(name)) {
            boolean parameterEntity = extEntity.isParameter();
            extEntity = null;
            if (dtd != null) {
                Entity entity = new Entity(name);
                dtd.addEntity(entity);
            } else {
                EntryHandler entryHandler = new EntryHandler();
                if (parameterEntity) {
                    entryHandler.currEntry().add(new XMLText(name + ';', inCDATA));
                } else {
                    entryHandler.currEntry().add(new XMLText('&' + name + ';', inCDATA));
                }
            }
            if (extWriter != null) {
                translationHandler.translateAndFlush();
                extWriter.close();
                extWriter = null;
            }
        }
    }

    /**
     * Resolves external entity and creates a new writer if it's an included
     * file.
     */
    public InputSource doResolve(String publicId, String systemId) throws SAXException, TranslationException,
            IOException, URISyntaxException {
        if (dtd != null
                && StringUtil.equal(publicId, dtd.getPublicId())
                && (StringUtil.equal(systemId, dtd.getSystemId()) || StringUtil.equal(
                localizeSystemId(systemId), dtd.getSystemId()))) {
            inDTD = true;
        }

        if (systemId != null
                && (systemId.startsWith(START_JARSCHEMA) || systemId.startsWith(START_FILESCHEMA))) {
            InputSource entity = new InputSource(systemId);
            // checking if f
            if (systemId.startsWith(START_FILESCHEMA)) {
                if (!new File(new URI(systemId)).exists()) {
                    entity = null;
                }
            }

            if (entity != null) {
                if (!inDTD && outFile != null && extEntity == null) {
                    extEntity = findExternalEntity(publicId, localizeSystemId(systemId));
                    if (extEntity != null && isInSource(systemId)) {
                        // if we resolved a new entity, and:
                        // 1. it's not a DTD
                        // 2. it's in project's source folder
                        // 3. it's not during project load
                        // then it's an external file, and we need to
                        // write it as an external file
                        translationHandler.translateAndFlush();
                        File extFile = new File(outFile.getParentFile(), localizeSystemId(systemId));
                        processedFiles.add(new File(inFile.getParent(), localizeSystemId(systemId)));
                        extWriter = translator.createWriter(extFile, context.getOutEncoding());
                        extWriter.write("<?xml version=\"1.0\"?>\n");
                    }
                }
                return entity;
            } else {
                return new InputSource(new java.io.StringReader(""));
            }
        } else {
            InputSource source = dialect.resolveEntity(publicId, systemId);
            if (source != null) {
                return source;
            } else {
                return new InputSource(new java.io.StringReader(""));
            }
        }
    }
}
