package org.omegat.core;

import org.omegat.gui.editor.mark.*;
import org.omegat.gui.glossary.TransTipsMarker;

import java.util.ArrayList;
import java.util.List;

public class RegMarker {
    static final List<IMarker> MARKERS = new ArrayList<IMarker>();

    static void rgMarker() throws Exception {
        // rG Marker CBO nya 9
        /*
extract kode dibawah ini ke kelas baru
        public static void registerMarker(IMarker marker) {
            MARKERS.add(marker);
        }
        yg terpengaruh oleh MARKERS apa saja?
    */
        registerMarker(new ProtectedPartsMarker());
        registerMarker(new RemoveTagMarker());
        registerMarker(new NBSPMarker());
        registerMarker(new TransTipsMarker());

        registerMarker(new WhitespaceMarkerFactory.SpaceMarker());
        registerMarker(new WhitespaceMarkerFactory.TabMarker());
        registerMarker(new WhitespaceMarkerFactory.LFMarker());

        registerMarker(new BidiMarkerFactory.RLMMarker());
        registerMarker(new BidiMarkerFactory.LRMMarker());
        registerMarker(new BidiMarkerFactory.PDFMarker());
        registerMarker(new BidiMarkerFactory.LROMarker());
        registerMarker(new BidiMarkerFactory.RLOMarker());

        registerMarker(new ReplaceMarker());
        registerMarker(new ComesFromAutoTMMarker());
        registerMarker(new FontFallbackMarker());
    }

    /**
     * Register class for calculate marks.
     *
     * @param marker
     *            marker implementation
     */
    public static void registerMarker(IMarker marker) {
        MARKERS.add(marker);
    }

    public static List<IMarker> getMarkers() {
        return MARKERS;
    }
}