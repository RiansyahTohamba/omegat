package org.omegat.filters3.xml;

import org.omegat.filters3.Entry;

import java.util.Stack;

public class EntryHandler {

    /** Current entry that collects normal text. */
    Entry entry;

    /** Current entry that collects the text surrounded by intact tag. */
    Entry intacttagEntry = null;

    public void setIntacttagEntry(Entry intacttagEntry) {
        this.intacttagEntry = intacttagEntry;
    }

    /** Stack of entries that collect out-of-turn text. */
    Stack<Entry> outofturnEntries = new Stack<Entry>();

    private boolean collectingIntactText() {
        return intacttagEntry != null;
    }

    public void initEntry(XMLDialect dialect,Handler handler) {
        entry = new Entry(dialect, handler);
    }

    /** Now we collect out-of-turn entry. */
    private boolean collectingOutOfTurnText() {
        return !outofturnEntries.empty();
    }
    /**
     * Returns current entry we collect text into. If we collect normal text,
     * returns {@link #entry}, else returns the last of
     * {@link #outofturnEntries}.
     */
//intacttagEntry siapa yg bisa modifikasi?
//    outofturnEntries siapa yg bisa modifikasi?
//    entry gimana?
    //    intacttagEntry, outofturnEntries, entry
    public Entry currEntry() {
        if (collectingIntactText()) {
            return intacttagEntry;
        } else if (collectingOutOfTurnText()) {
            return outofturnEntries.peek();
        } else {
            return entry;
        }
    }

}
