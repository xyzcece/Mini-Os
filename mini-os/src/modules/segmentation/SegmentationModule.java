package modules.segmentation;

import shared.ModulePanel;
import shared.Theme;

import javax.swing.*;

/**
 * SEGMENTATION  —  OWNER: Member 1
 * TODO:
 *   [ ] Segment table input (limit, base), segment number, offset
 *   [ ] Logical space + segment table + physical memory map
 *   [ ] Translation: offset < limit ? base + offset : invalid
 * CHECK: Segment 2, offset 53 -> physical 4353
 *
 * Tip: copy the pattern in modules/paging/PagingModule.java
 */
public class SegmentationModule extends ModulePanel {

    @Override public String title()    { return "Segmentation"; }
    @Override public String subtitle() { return "Validate offsets against base and limit pairs."; }

    @Override protected void buildInputs(JPanel p) {
        // TODO: add your inputs, e.g.  JTextField f = inputRow(p, "Label", "default value");
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(kicker("Simulation output · Segmentation"));
        r.add(text("Coming soon — Member 1 is building this module.", 14, Theme.muted()));
        // TODO: read inputs, compute, then add results:  r.add(table(headers, rows));
    }
}
