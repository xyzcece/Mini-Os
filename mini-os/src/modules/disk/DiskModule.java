package modules.disk;

import java.awt.BorderLayout;
import javax.swing.*;
import modules.common.FigmaModule;
import shared.Theme;

/**
 * DISK SCHEDULING  —  OWNER: Member 2
 * TODO:
 *   [ ] Requests, initial head, disk max, algorithm dropdown
 *   [ ] FCFS, SSTF, SCAN, C-SCAN, LOOK, C-LOOK
 *   [ ] Seek table (next track, traverse) + total cylinders + ave seek
 *   [ ] Seek-path graph
 * CHECK: SSTF (head 53): 236 cylinders
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 * Tip: see modules/paging/PagingModule.java for a finished module on the same design.
 */
public class DiskModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    static final String[] ALGOS = {"FCFS", "SSTF", "SCAN", "C-SCAN", "LOOK", "C-LOOK"};
    private JTextField requests, head, max;
    private String algo = "FCFS";

    @Override public String title()    { return "Disk Scheduling"; }
    @Override public String subtitle() { return "Compare seek order and total head movement."; }
    @Override protected String crumb() { return "DISK"; }
    @Override protected String lead()  { return "Order the disk requests and add up the head movement."; }

    @Override protected void buildInputs(JPanel p) {
        requests = new RoundField(requests == null ? "" : requests.getText());
        head     = new RoundField(head     == null ? "" : head.getText());
        max      = new RoundField(max      == null ? "" : max.getText());
        Dropdown pick = new Dropdown(algo, ALGOS, a -> { algo = a; rebuildInputs(); });
        p.add(columns(new double[]{2.4, 1, 1, 1.2},
                labeled("Requests", requests, "tracks separated by spaces"),
                labeled("Initial head", head, null),
                labeled("Disk max", max, null),
                labeled("Algorithm", pick, null)), BorderLayout.CENTER);
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(pixelText("SIMULATION OUTPUT · DISK SCHEDULING", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));
        r.add(Notice.prompt("Coming soon — Member 2 is building this module."));
        // TODO: read inputs, compute, then add results (seek table, total cylinders, seek-path graph).
    }
}
