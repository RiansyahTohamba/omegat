package org.omegat.filters3.xml;

import java.util.Stack;

public class TranslateableTag {
    /** Name of the current variable translatable tag */
    Stack<String> translatableTagName = new Stack<String>();

    /**
     * If we are not inside a translatable tag, and if the dialect says the new
     * one is translatable, add the new tag to the stack
     *
     * @param tag
     *            The current opening tag
     * @param atts
     *            The attributes of the current tag
     */
    // TODO: The concept works only perfectly if the first tag with
    // translatable content inside the translatable tag is a paragraph
    // tag
    void setTranslatableTag(String tag, org.omegat.filters3.Attributes atts,XMLDialect dialect) {

        if (!isTranslatableTag()) { // If stack is empty
            if (dialect.validateTranslatableTag(tag, atts)) {
                translatableTagName.push(tag);
            }
        } else {
            translatableTagName.push(tag);
        }
    }

    /**
     * Remove a tag from the stack of translatable tags
     */
    void removeTranslatableTag() {
        if (isTranslatableTag()) { // If there is something in the stack
            translatableTagName.pop(); // Remove it
        }
    }
    public boolean isTranslatableTag() {
        return !translatableTagName.empty();
    }
}
