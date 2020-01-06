package org.omegat.filters3.xml;

import org.omegat.filters2.TranslationException;
import org.omegat.filters3.Attribute;
import org.omegat.filters3.Tag;
import org.omegat.util.StringUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.Stack;

public class TagHandler {
    private Handler handler;
    private EntryHandler entryHandler;
    private XMLDialect dialect;
    private Translator translator;

    /** Current path in XML. */
    private final Stack<String> currentTagPath = new Stack<String>();

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


    public TagHandler(XMLDialect dialect,Translator translator){
        this.dialect = dialect;
        this.translator = translator;
        this.entryHandler = new EntryHandler();
    }

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
    public boolean isPreformattingTag(String tag, org.omegat.filters3.Attributes atts) {
        if (dialect.getPreformatTags() != null && dialect.getPreformatTags().contains(tag)) {
            return true;
        } else {
            return dialect.validatePreformatTag(tag, atts);
        }
    }


    /**
     * Is called when the tag is started.
     */
    void start(String tag, Attributes attributes) throws SAXException, TranslationException {
        boolean prevIgnored = translator.isInIgnored();
        translatorTagStart(tag, attributes);

        if (!translator.isInIgnored()) {
            if (handler.isOutOfTurnTag(tag)) {
                XMLOutOfTurnTag ootTag = new XMLOutOfTurnTag(dialect, handler, tag, handler.getShortcut(tag), attributes);
                entryHandler.currEntry().add(ootTag);
                handler.getOutofturnEntries().push(ootTag.getEntry());
            } else {
                if (handler.isParagraphTag(tag, XMLUtils.convertAttributes(attributes)) 
                        && !handler.collectingOutOfTurnText()
                        && !entryHandler.collectingIntactText()) {
                    translateAndFlush();
                }
                queueTag(tag, attributes,dialect);
            }
        } else {
            if (!prevIgnored) {
                // start ignored from this tags - need to flush translation
                translateAndFlush();
            }
            queueIgnoredTag(tag, attributes);
        }
    }
    /** Returns a shortcut for a tag. Queries dialect first, else returns null. */
    private String getShortcut(String tag) {
        if (dialect.getShortcuts() != null) {
            return dialect.getShortcuts().get(tag);
        } else {
            return null;
        }
    }

    void translatorTagStart(String tag, Attributes atts) {
        currentTagPath.push(tag);
        translator.tagStart(constructCurrentPath(), atts);
    }

    void translatorTagEnd(String tag) {
        translator.tagEnd(constructCurrentPath());
        while (!currentTagPath.pop().equals(tag)) {
        }
    }
    /** Now we collect intact text. */

    private void queueTag(String tag, Attributes attributes,XMLDialect dialect) {
        Tag xmltag = null;
        XMLIntactTag intacttag = null;
        new TranslateableTag().setTranslatableTag(tag, XMLUtils.convertAttributes(attributes),dialect);
        setSpacePreservingTag(XMLUtils.convertAttributes(attributes));
        if (!collectingIntactText()) {
            if (isContentBasedTag(tag, XMLUtils.convertAttributes(attributes))) {
                intacttag = new XMLContentBasedTag(dialect, this, tag, getShortcut(tag), dialect.getContentBasedTags().get(tag), attributes);
                xmltag = intacttag;
                intacttagName = tag;
                intacttagAttributes = XMLUtils.convertAttributes(attributes);
            } else if (isIntactTag(tag, XMLUtils.convertAttributes(attributes))) {
                intacttag = new XMLIntactTag(dialect, this, tag, getShortcut(tag), attributes);
                xmltag = intacttag;
                intacttagName = tag;
                intacttagAttributes = XMLUtils.convertAttributes(attributes);
            }
        }
        if (xmltag == null) {
            xmltag = new XMLTag(tag, getShortcut(tag), Tag.Type.BEGIN, attributes, this.translator.getTargetLanguage());
            xmlTagName.push(xmltag.getTag());
            xmlTagAttributes.push(xmltag.getAttributes());
        }
        entryHandler.currEntry().add(xmltag);

        if (intacttag != null) {
            entryHandler.setIntacttagEntry(intacttag.getIntactContents());
        }

        if (!collectingIntactText()) {
            for (int i = 0; i < xmltag.getAttributes().size(); i++) {
                Attribute attr = xmltag.getAttributes().get(i);
                if ((dialect.getTranslatableAttributes().contains(attr.getName()) || dialect
                        .getTranslatableTagAttributes().containsPair(tag, attr.getName()))
                        && dialect.validateTranslatableTagAttribute(tag, attr.getName(),
                        xmltag.getAttributes())) {
                    attr.setValue(StringUtil.makeValidXML(
                            translator.translate(StringUtil.unescapeXMLEntities(attr.getValue()), null)));
                }
            }
        }
    }

    /**
     * Is called when the tag is ended.
     */
    void end(String tag) throws SAXException, TranslationException {
        boolean prevIgnored = translator.isInIgnored();
        if (!translator.isInIgnored()) {
            if (entryHandler.collectingIntactText() && tag.equals(handler.getIntacttagName())
                    && (isIntactTag(tag, null) || isContentBasedTag(tag, null))) {
                entryHandler.setIntacttagEntry(null);
                intacttagName = null;
                intacttagAttributes = null;
                handler.removeTranslatableTag();
            } else if (handler.collectingOutOfTurnText() && handler.isOutOfTurnTag(tag)) {
                translateButDontFlash();
                handler.getOutofturnEntries().pop();
            } else {
                queueEndTag(tag);
                // TODO: If a file doesn't contain any paragraph tag,
                // the translatable content will be lost
                if (handler.isParagraphTag(tag) && !handler.collectingOutOfTurnText() && !entryHandler.collectingIntactText()) {
                    translateAndFlush();
                }
                handler.removeTranslatableTag();
            }
        } else {
            queueEndTag(tag);
        }

        translatorTagEnd(tag);
        if (!translator.isInIgnored() && prevIgnored) {
            // stop ignored from this tag - need to flush without translate
            handler.flushButDontTranslate();
        }
    }



    private void queueEndTag(String tag) {
        int len = entryHandler.currEntry().size();
        if (len > 0
                && (entryHandler.currEntry().get(len - 1) instanceof XMLTag)
                && (((XMLTag) entryHandler.currEntry().get(len - 1)).getTag().equals(tag) && ((XMLTag) entryHandler.currEntry().get(
                len - 1)).getType() == Tag.Type.BEGIN) && !isClosingTagRequired()) {
            if (((XMLTag) entryHandler.currEntry().get(len - 1)).getTag().equals(xmlTagName.lastElement())) {
                xmlTagName.pop();
                xmlTagAttributes.pop();
            }
            ((XMLTag) entryHandler.currEntry().get(len - 1)).setType(Tag.Type.ALONE);
        } else {
            XMLTag xmltag = new XMLTag(tag, getShortcut(tag), Tag.Type.END, null, this.translator.getTargetLanguage());
            if (xmltag.getTag().equals(xmlTagName.lastElement())) {
                xmlTagName.pop();
                xmltag.setStartAttributes(xmlTagAttributes.pop()); // Restore attributes
            }
            entryHandler.currEntry().add(xmltag);
        }
    }

    private boolean isClosingTagRequired() {
        return dialect.getClosingTagRequired();
    }


    /** Returns whether we face out of turn tag we should collect separately. */
    private boolean isOutOfTurnTag(String tag) {
        return dialect.getOutOfTurnTags() != null && dialect.getOutOfTurnTags().contains(tag);
    }


}
