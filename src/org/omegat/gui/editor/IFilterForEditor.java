/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2008-2010 Alex Buloichik
               2011 Alex Buloichik, Didier Briel
               2012 Guido Leenders, Didier Briel
               2013 Zoltan Bartko, Aaron Madlon-Kay
               2014 Aaron Madlon-Kay
               2016 Didier Briel
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

package org.omegat.gui.editor;

import org.omegat.core.data.EntryKey;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.gui.editor.autocompleter.IAutoCompleter;
import org.omegat.gui.editor.mark.Mark;

import java.util.List;

/**
 * Interface for access to editor filter functionality.
 */
public interface IFilterForEditor {
    /**
     * Gets a filter for this editor, or null if filter doesn't applied.
     */
    IEditorFilter getFilter();

    /**
     * Sets a filter to this editor. The filter causes only the selected entries to be shown in the editor.
     *
     * @param filter
     *            Filter instance
     */
    void setFilter(IEditorFilter filter);

    /**
     * Removes the current filter.
     */
    void removeFilter();

}
