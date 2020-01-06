package org.omegat.filters3.xml;

import org.omegat.filters2.TranslationException;
import org.omegat.filters3.Attribute;
import org.omegat.filters3.Tag;
import org.omegat.util.StringUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class TagHandler {

    /**
     * Is called when the tag is started.
     */
    void start(String tag, Attributes attributes,Handler handler) throws SAXException, TranslationException {
        boolean prevIgnored = handler.getTranslator().isInIgnored();
        translatorTagStart(tag, attributes);

        if (!handler.getTranslator().isInIgnored()) {
            if (handler.isOutOfTurnTag(tag)) {
                XMLOutOfTurnTag ootTag = new XMLOutOfTurnTag(handler.getDialect(), handler, tag, handler.getShortcut(tag), attributes);
                currEntry().add(ootTag);
                handler.getOutofturnEntries().push(ootTag.getEntry());
            } else {
                if (handler.isParagraphTag(tag, XMLUtils.convertAttributes(attributes)) && !handler.collectingOutOfTurnText()
                        && !handler.collectingIntactText()) {
                    translateAndFlush();
                }
                queueTag(tag, attributes,handler.getDialect());
            }
        } else {
            if (!prevIgnored) {
                // start ignored from this tags - need to flush translation
                translateAndFlush();
            }
            queueIgnoredTag(tag, attributes);
        }
    }
    void translatorTagStart(String tag, Attributes atts) {
        handler.getCurrentTagPath().push(tag);
        handler.getTranslator().tagStart(constructCurrentPath(), atts);
    }

    void translatorTagEnd(String tag) {
        handler.getTranslator().tagEnd(constructCurrentPath());
        while (!handler.getCurrentTagPath().pop().equals(tag)) {
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
                intacttag = new XMLContentBasedTag(dialect, this, tag, getShortcut(tag), dialect
                        .getContentBasedTags().get(tag), attributes);
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
        currEntry().add(xmltag);

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
        boolean prevIgnored = handler.getTranslator().isInIgnored();
        if (!handler.getTranslator().isInIgnored()) {
            if (handler.collectingIntactText() && tag.equals(handler.getIntacttagName())
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
                if (handler.isParagraphTag(tag) && !handler.collectingOutOfTurnText() && !handler.collectingIntactText()) {
                    translateAndFlush();
                }
                handler.removeTranslatableTag();
            }
        } else {
            queueEndTag(tag);
        }

        translatorTagEnd(tag);
        if (!handler.getTranslator().isInIgnored() && prevIgnored) {
            // stop ignored from this tag - need to flush without translate
            handler.flushButDontTranslate();
        }
    }



    private void queueEndTag(String tag) {
        int len = currEntry().size();
        if (len > 0
                && (currEntry().get(len - 1) instanceof XMLTag)
                && (((XMLTag) currEntry().get(len - 1)).getTag().equals(tag) && ((XMLTag) currEntry().get(
                len - 1)).getType() == Tag.Type.BEGIN) && !isClosingTagRequired()) {
            if (((XMLTag) currEntry().get(len - 1)).getTag().equals(xmlTagName.lastElement())) {
                xmlTagName.pop();
                xmlTagAttributes.pop();
            }
            ((XMLTag) currEntry().get(len - 1)).setType(Tag.Type.ALONE);
        } else {
            XMLTag xmltag = new XMLTag(tag, getShortcut(tag), Tag.Type.END, null, this.translator.getTargetLanguage());
            if (xmltag.getTag().equals(xmlTagName.lastElement())) {
                xmlTagName.pop();
                xmltag.setStartAttributes(xmlTagAttributes.pop()); // Restore attributes
            }
            currEntry().add(xmltag);
        }
    }
}
