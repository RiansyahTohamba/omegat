/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2000-2006 Keith Godfrey and Maxym Mykhalchuk
               2008 Martin Fleurke, Alex Buloichik, Didier Briel
               2009 Didier Briel
               2010 Antonio Vilei
               2011 Didier Briel
               2013 Didier Briel, Alex Buloichik
               Home page: http://www.omegat.org/
               Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.filters3.xml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.omegat.filters2.FilterContext;
import org.omegat.filters2.TranslationException;
import org.omegat.util.OStrings;
import org.omegat.util.StringUtil;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The part of XML filter that actually does the job. This class is called back
 * by SAXParser.
 *
 * Entities described on
 * http://www.ibm.com/developerworks/xml/library/x-entities/
 * http://xmlwriter.net/xml_guide/entity_declaration.shtml
 *
 * @author Maxym Mykhalchuk
 * @author Martin Fleurke
 * @author Didier Briel
 * @author Alex Buloichik (alex73mail@gmail.com)
 */
public class Handler extends DefaultHandler implements LexicalHandler, DeclHandler {
    private final TranslationHandler translationHandler = new TranslationHandler(this);
    private Translator translator;
    private XMLDialect dialect;
    private File inFile;
    private File outFile;
    private FilterContext context;

    /** Main file writer to write translated text to. */
    private BufferedWriter mainWriter;
    /** Current writer for an external included file. */
    private BufferedWriter extWriter = null;

    /** Current path in XML. */
    private final Stack<String> currentTagPath = new Stack<String>();

    /**
     * Returns current writer we should write into. If we're in main file,
     * returns {@link #mainWriter}, else (if we're writing external file)
     * returns {@link #extWriter}.
     */
    private BufferedWriter currWriter() {
        if (extWriter != null) {
            return extWriter;
        } else {
            return mainWriter;
        }
    }

    /** Currently parsed external entity that has its own writer. */
    private Entity extEntity = null;

    /** Keep the attributes of paragraph tags. */
    Stack<org.omegat.filters3.Attributes> paragraphTagAttributes = new Stack<org.omegat.filters3.Attributes>();
    /** Keep the attributes of preformat tags. */
    Stack<org.omegat.filters3.Attributes> preformatTagAttributes = new Stack<org.omegat.filters3.Attributes>();
    /** Keep the attributes of xml tags. */
    Stack<org.omegat.filters3.Attributes> xmlTagAttributes = new Stack<org.omegat.filters3.Attributes>();

    /** Names of possible paragraph tags. */
    Stack<String> paragraphTagName = new Stack<String>();
    /** Names of possible preformat tags. */
    Stack<String> preformatTagName = new Stack<String>();

    /** Names of xml tags. */
    Stack<String> xmlTagName = new Stack<String>();


    /**
     * External entities declared in source file. Each entry is of type
     * {@link Entity}.
     */
    private List<Entity> externalEntities = new ArrayList<Entity>();

    /**
     * Internal entities declared in source file. A {@link Map} from
     * {@link String}/entity name/ to {@link Entity}.
     */
    private Map<String, Entity> internalEntities = new HashMap<String, Entity>();
    /** Internal entity just started. */
    private Entity internalEntityStarted = null;

    /** Currently collected text is wrapped in CDATA section. */
    private boolean inCDATA = false;

    /** Whether we're curren */
    // private boolean inPreformattingTag = false;

    /**
     * SAX parser encountered DTD declaration, so probably it will parse DTD
     * next, but some nice things may happen before.
     */
    private DTD dtd = null;
    /** SAX parser parses DTD -- we don't extract translatable text from there */
    private boolean inDTD = false;
    private EntryHandler entryHandler;
    /**
     * External files this handler has processed, because they were included
     * into main file. Each entry is of type {@link File}.
     */
    private List<File> processedFiles = new ArrayList<File>();

    /**
     * Returns external files this handler has processed, because they were
     * included into main file. Each entry is {@link File}.
     */
    public List<File> getProcessedFiles() {
        return processedFiles.isEmpty() ? null : processedFiles;
    }

    public XMLDialect getDialect() {
        return dialect;
    }

    public Translator getTranslator() {
        return translator;
    }

    /** Throws a nice error message when SAX parser encounders fastal error. */
    private void reportFatalError(SAXParseException e) throws SAXException, MalformedURLException,
            URISyntaxException {
        int linenum = e.getLineNumber();
        String filename;
        if (e.getSystemId() != null) {
            File errorfile = new File(inFile.getParentFile(), localizeSystemId(e.getSystemId()));
            if (errorfile.exists()) {
                filename = errorfile.getAbsolutePath();
            } else {
                filename = inFile.getAbsolutePath();
            }
        } else {
            filename = inFile.getAbsolutePath();
        }
        throw new SAXException("\n"
                + StringUtil.format(e.getMessage() + "\n" + OStrings.getString("XML_FATAL_ERROR"),
                        filename, linenum));
    }

    /**
     * Creates a new instance of Handler
     */
    public Handler(Translator translator, XMLDialect dialect, File inFile, File outFile, FilterContext fc)
            throws IOException {
        this.translator = translator;
        this.dialect = dialect;
        this.inFile = inFile;
        this.outFile = outFile;
        this.context = fc;
        this.mainWriter = translator.createWriter(outFile, fc.getOutEncoding());
        this.entryHandler = new EntryHandler();
    }

    public FilterContext getContext() {
        return context;
    }

    private static final String START_JARSCHEMA = "jar:";
    private static final String START_FILESCHEMA = "file:";

    // ////////////////////////////////////////////////////////////////////////
    // Utility methods
    // ////////////////////////////////////////////////////////////////////////

    private String sourceFolderAbsolutePath = null;

    /**
     * Returns source folder of the main file with trailing '/'
     * (File.separator).
     */
    private String getSourceFolderAbsolutePath() {
        if (sourceFolderAbsolutePath == null) {
            String res = inFile.getAbsoluteFile().getParent();
            try {
                res = inFile.getCanonicalFile().getParent();
            } catch (IOException ex) {
            }
            if (res.codePointBefore(res.length()) != File.separatorChar) {
                res = res + File.separatorChar;
            }
            sourceFolderAbsolutePath = res;
        }
        return sourceFolderAbsolutePath;
    }

    /** Makes System ID not an absolute, but a relative one. */
    private String localizeSystemId(String systemId) throws URISyntaxException, MalformedURLException {
        if (systemId.startsWith(START_FILESCHEMA)) {
            Path thisOutFile = new File(new URI(systemId)).toPath();
            Path sourceFolderFile = new File(getSourceFolderAbsolutePath()).toPath();
            try {
                String thisOutPath = sourceFolderFile.relativize(thisOutFile).toString();
                return thisOutPath.replace("\\", "/");
            }
            catch (IllegalArgumentException ex) {
                // Failed to relativize
            }
        }
        return systemId;
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

    /** Finds external entity by publicId and systemId. */
    private Entity findExternalEntity(String publicId, String systemId) {
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

    /**
     * Is called when the entity starts. Tries to find out whether it's an
     * internal entity, and if so, turns on the trigger to queue entity, and not
     * the text it represents, in {@link #characters(char[],int,int)}.
     */
    private void doStartEntity(String name) {
        if (inDTD) {
            return;
        }
        internalEntityStarted = internalEntities.get(name);
    }

    /**
     * Is called when the entity is ended. Tries to find out whether it's an
     * external entity we created a writer for, and if so, closes the writer and
     * nulls the entity.
     */
    private void doEndEntity(String name) throws SAXException, TranslationException, IOException {
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


    // /////////////////////////////////////////////////////////////////////////
    // Dialect Helper methods
    // /////////////////////////////////////////////////////////////////////////

    /**
     * Returns whether the tag starts a new paragraph.
     */
    public boolean isParagraphTag(Tag tag) {
        if ((dialect.getParagraphTags() != null && dialect.getParagraphTags().contains(tag.getTag()))
                || isPreformattingTag(tag.getTag(), tag.getAttributes())) {
            return true;
        } else if (tag.getType() == Tag.Type.END
                && isPreformattingTag(tag.getTag(), tag.getStartAttributes())) {
            return true;
        } else {
            return dialect.validateParagraphTag(tag.getTag(), tag.getAttributes());
        }
    }


    /**
     * Returns whether the tag surrounds preformatted block of text.
     *
     * @param tag
     *            A tag
     * @return <code>true</code> or <code>false</false>
     */
    private boolean isPreformattingTag(String tag, org.omegat.filters3.Attributes atts) {
        if (dialect.getPreformatTags() != null && dialect.getPreformatTags().contains(tag)) {
            return true;
        } else {
            return dialect.validatePreformatTag(tag, atts);
        }
    }

    private boolean isClosingTagRequired() {
        return dialect.getClosingTagRequired();
    }

    private boolean isTagsAggregationEnabled() {
        return dialect.getTagsAggregationEnabled();
    }

    /** Returns whether we face out of turn tag we should collect separately. */
    private boolean isOutOfTurnTag(String tag) {
        return dialect.getOutOfTurnTags() != null && dialect.getOutOfTurnTags().contains(tag);
    }

    /** Returns a shortcut for a tag. Queries dialect first, else returns null. */
    private String getShortcut(String tag) {
        if (dialect.getShortcuts() != null) {
            return dialect.getShortcuts().get(tag);
        } else {
            return null;
        }
    }

    /**
     * Checks whether the xml:space="preserve" attribute is present
     * @param currentAttributes The current Attributes
     * @return true or false
     */
    private boolean isSpacePreservingSet(org.omegat.filters3.Attributes currentAttributes) {

        if (dialect.getForceSpacePreserving()) {
            return true;
        }

        boolean preserve = false;

        for (int i = 0; i < currentAttributes.size(); i++) {
            Attribute oneAttribute = currentAttributes.get(i);
            if ((oneAttribute.getName().equalsIgnoreCase("xml:space")
                 && oneAttribute.getValue().equalsIgnoreCase("preserve"))) {
                preserve = true;
            }
        }

        return preserve;
    }

    // ////////////////////////////////////////////////////////////////////////
    // Callback methods
    // ////////////////////////////////////////////////////////////////////////

    /**
     * Resolves an external entity.
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
        try {
            return doResolve(publicId, systemId);
        } catch (URISyntaxException e) {
            throw new SAXException(e);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (TranslationException e) {
            throw new SAXException(e);
        }
    }

    /** Receive notification of the start of an element. */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        try {
            new TagHandler().start(qName, attributes,this);
        } catch (TranslationException e) {
            throw new SAXException(e);
        }
    }

    /** Receive notification of the end of an element. */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            translationHandler.end(qName);
        } catch (TranslationException e) {
            throw new SAXException(e);
        }
    }

    /** Receive notification of character data inside an element. */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inDTD) {
            return;
        }
        queueText(new String(ch, start, length));
    }

    /** Receive notification of ignorable whitespace in element content. */
    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (inDTD) {
            return;
        }
        queueText(new String(ch, start, length));
    }

    /** Receive notification of an XML comment anywhere in the document. */
    public void comment(char[] ch, int start, int length) throws SAXException {
        if (inDTD) {
            return;
        }
        queueComment(new String(ch, start, length));
    }

    /**
     * Receive notification of an XML processing instruction anywhere in the
     * document.
     */
    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        if (inDTD) {
            return;
        }
        queueProcessingInstruction(target, data);
    }

    /** Receive notification of the beginning of the document. */
    @Override
    public void startDocument() throws SAXException {
        try {
            mainWriter.write("<?xml version=\"1.0\"?>\n");
        } catch (IOException e) {
            throw new SAXException(e);
        }

        entryHandler.initEntry(dialect,this);
    }



    /** Receive notification of the end of the document. */
    @Override
    public void endDocument() throws SAXException {
        try {
            translationHandler.translateAndFlush();
            if (extWriter != null) {
                extWriter.close();
                extWriter = null;
            }
            translationHandler.translateAndFlush();
            currWriter().close();
        } catch (TranslationException e) {
            throw new SAXException(e);
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    /**
     * Report a fatal XML parsing error. Is used to provide feedback.
     */
    @Override
    public void fatalError(org.xml.sax.SAXParseException e) throws SAXException {
        try {
            reportFatalError(e);
        } catch (MalformedURLException ex) {
            throw new SAXException(ex);
        } catch (URISyntaxException ex) {
            throw new SAXException(ex);
        }
    }

    /**
     * Report the start of DTD declarations, if any.
     */
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        dtd = new DTD(name, publicId, systemId);
    }

    /**
     * Report the end of DTD declarations. Queues the DTD declaration with all
     * the entities declared.
     */
    public void endDTD() throws SAXException {
        queueDTD(dtd);
        inDTD = false;
        dtd = null;
    }

    /**
     * Report the start of a CDATA section.
     */
    public void startCDATA() throws SAXException {
        inCDATA = true;
    }

    /**
     * Report the end of a CDATA section.
     */
    public void endCDATA() throws SAXException {
        inCDATA = false;
    }

    /**
     * Not used: Report the beginning of some internal and external XML
     * entities.
     */
    public void startEntity(String name) throws SAXException {
        doStartEntity(name);
    }

    /**
     * Report the end of an entity.
     *
     * @param name
     *            The name of the entity that is ending.
     * @exception SAXException
     *                The application may raise an exception.
     * @see #startEntity
     */
    public void endEntity(String name) throws SAXException {
        try {
            doEndEntity(name);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (TranslationException e) {
            throw new SAXException(e);
        }
    }

    /**
     * Report an internal entity declaration.
     */
    public void internalEntityDecl(String name, String value) throws SAXException {
        if (inDTD) {
            return;
        }
        Entity entity = new Entity(name, value);
        internalEntities.put(name, entity);
        if (extEntity != null) {
            if (extWriter != null) {
                StringBuilder res = new StringBuilder();
                res.append(entity.toString()).append('\n');
                try {
                    extWriter.write(res.toString());
                } catch (IOException e) {
                    throw new SAXException(e);
                }
            }
        } else {
            dtd.addEntity(entity);
        }
    }

    /**
     * Report a parsed external entity declaration.
     */
    public void externalEntityDecl(String name, String publicId, String systemId) throws SAXException {
        if (inDTD) {
            return;
        }
        try {
            Entity entity = new Entity(name, publicId, localizeSystemId(systemId));
            externalEntities.add(entity);
            dtd.addEntity(entity);
        } catch (MalformedURLException ex) {
            throw new SAXException(ex);
        } catch (URISyntaxException ex) {
            throw new SAXException(ex);
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // unused callbacks
    // /////////////////////////////////////////////////////////////////////////

    /** Not used: An element type declaration. */
    public void elementDecl(String name, String model) {
    }

    /** Not used: An attribute type declaration. */
    public void attributeDecl(String eName, String aName, String type, String valueDefault, String value) {
    }


}
