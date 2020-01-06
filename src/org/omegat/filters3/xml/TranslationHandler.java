package org.omegat.filters3.xml;

import org.omegat.core.Core;
import org.omegat.core.data.ProtectedPart;
import org.omegat.filters2.TranslationException;
import org.omegat.filters3.Attribute;
import org.omegat.filters3.Element;
import org.omegat.filters3.Entry;
import org.omegat.filters3.Tag;
import org.omegat.util.StringUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TranslationHandler {
    private final Handler handler;
    private EntryHandler entryHandler;

    public TranslationHandler(Handler handler) {
        this.handler = handler;
    }
    /** Status of the xml:space="preserve" flag */
    private boolean spacePreserve = false;

    /** Current entry that collects the text surrounded by intact tag. */
    String intacttagName = null;

    /** Keep the attributes of an intact tag. */
    org.omegat.filters3.Attributes intacttagAttributes = null;




    /**
     * One of the main methods of the XML filter: it collects all the data,
     * adjusts it, and sends for translation.
     *
     * @see #translateAndFlush()
     */
    void translateButDontFlash() throws TranslationException {
        if (currEntry().isEmpty()) {
            return;
        }

        List<ProtectedPart> shortcutDetails = new ArrayList<ProtectedPart>();
        boolean tagsAggregation = handler.isTagsAggregationEnabled();
        String src = currEntry().sourceToShortcut(tagsAggregation, handler.getDialect(), shortcutDetails);
        Element lead = currEntry().get(0);
        String translation = src;
        if ((lead instanceof Tag)
                && (handler.isPreformattingTag(((Tag) lead).getTag(), ((Tag) lead).getAttributes())
                || handler.isSpacePreservingTag())
                && isTranslatableTag()
                && !StringUtil.isEmpty(src)) {
            handler.resetSpacePreservingTag();
            translation = handler.getTranslator().translate(src, shortcutDetails);
        } else {
            String compressed = src;
            if (Core.getFilterMaster().getConfig().isRemoveSpacesNonseg()) {
                compressed = StringUtil.compressSpaces(src);
            }
            if (isTranslatableTag()) {
                translation = handler.getTranslator().translate(compressed, shortcutDetails);
            }
            // untranslated is written out uncompressed
            if (compressed.equals(translation)) {
                translation = src;
            }
        }

        currEntry().setTranslation(translation, handler.getDialect(), new ArrayList<ProtectedPart>());
    }

    /**
     * One of the main methods of the XML filter: it collects all the data,
     * adjusts it, sends for translation, writes out the translated data and
     * clears the entry.
     *
     * @see #translateButDontFlash()
     */
    void translateAndFlush() throws SAXException, TranslationException {
        translateButDontFlash();
        try {
            handler.currWriter().write(currEntry().translationToOriginal());
        } catch (IOException e) {
            throw new SAXException(e);
        }
        currEntry().clear();
    }

    private String constructCurrentPath() {
        StringBuilder path = new StringBuilder(256);
        for (String t : currentTagPath) {
            path.append('/').append(t);
        }
        return path.toString();
    }





    private boolean isSpacePreservingTag() {
        if (Core.getFilterMaster().getConfig().isPreserveSpaces()) { // Preserve spaces for all tags
            return true;
        } else {
            return spacePreserve;
        }
    }

    private void resetSpacePreservingTag() {
        spacePreserve = false;
    }

    /**
     * Queue tag that should be ignored by editor, including content and all subtags.
     */
    private void queueIgnoredTag(String tag, Attributes attributes) {
        Tag xmltag = null;
        setSpacePreservingTag(XMLUtils.convertAttributes(attributes));
        if (xmltag == null) {
            xmltag = new XMLTag(tag, getShortcut(tag), Tag.Type.BEGIN, attributes,
                    this.translator.getTargetLanguage());
            xmlTagName.push(xmltag.getTag());
            xmlTagAttributes.push(xmltag.getAttributes());
        }
        currEntry().add(xmltag);
    }

    private void queueComment(String comment) {
        if (!translator.isInIgnored()) {
            translator.comment(comment);
        }
        currEntry().add(new Comment(comment));
    }

    private void queueProcessingInstruction(String data, String target) {
        currEntry().add(new ProcessingInstruction(data, target));
    }

    private void queueDTD(DTD dtd) {
        currEntry().add(dtd);
    }


    private void queueText(String s) {
        if (!translator.isInIgnored()) {
            translator.text(s);
        }

        // TODO: ideally, xml:space=preserved would be handled at this level, but that would suppose
        // knowing here whether we're inside a preformatted tag, etc.
        if (internalEntityStarted != null && s.equals(internalEntityStarted.getValue())) {
            currEntry().add(new XMLEntityText(internalEntityStarted));
        } else {
            boolean added = false;
            if (!currEntry().isEmpty()) {
                Element elem = currEntry().get(currEntry().size() - 1);
                if (elem instanceof XMLText) {
                    XMLText text = (XMLText) elem;
                    if (text.isInCDATA() == inCDATA) {
                        currEntry().resetTagDetected();
                        text.append(s);
                        added = true;
                    }
                }
            }
            if (!added) {
                currEntry().add(new XMLText(s, inCDATA));
            }
        }
    }

    /**
     * Returns whether the tag is content based.
     *
     * @param tag
     *            A tag
     * @return <code>true</code> or <code>false</false>
     */
    private boolean isContentBasedTag(String tag, org.omegat.filters3.Attributes atts) {
        if (dialect.getContentBasedTags() != null && dialect.getContentBasedTags().containsKey(tag)) {
            return true;
        } else {
            if (atts == null) {
                if (tag.equals(intacttagName)) {
                    atts = intacttagAttributes; // Restore attributes
                }
            }
            return dialect.validateContentBasedTag(tag, atts);
        }
    }


    /**
     * Returns whether the tag surrounds intact block of text which we shouldn't
     * translate.
     */
    private boolean isIntactTag(String tag, org.omegat.filters3.Attributes atts) {
        if (dialect.getIntactTags() != null && dialect.getIntactTags().contains(tag)) {
            return true;
        } else {
            if (atts == null) {
                if (tag.equals(intacttagName)) {
                    atts = intacttagAttributes; // Restore attributes
                }
            }

            return dialect.validateIntactTag(tag, atts);
        }
    }

    /**
     * If the space-preserving flag is not set, and the attributes say it is one, set it
     *
     * @param atts
     *            The attributes of the current tag
     */
    private void setSpacePreservingTag(org.omegat.filters3.Attributes atts) {

        if (isSpacePreservingSet(atts)) {
            spacePreserve = true;
        }
    }

}