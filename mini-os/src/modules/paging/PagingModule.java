package modules.paging;

import shared.ModulePanel;
import shared.Theme;

import javax.swing.*;

/**
 * PAGING LAB  —  OWNER: Member 1
 * Number of page table entries = Address space ÷ Page size
 * CHECK: 1 GB ÷ 4 KB = 262,144 entries
 *
 * This module is FINISHED and is the example the team can copy.
 */
public class PagingModule extends ModulePanel {
    private JTextField addressSpace, pageSize;

    @Override public String title()    { return "Paging Lab"; }
    @Override public String subtitle() { return "Number of page table entries = address space ÷ page size."; }

    @Override protected void buildInputs(JPanel p) {
        addressSpace = inputRow(p, "Address space (e.g. 1 GB, 512 MB)", addressSpace == null ? "1 GB" : addressSpace.getText());
        pageSize     = inputRow(p, "Page size (e.g. 4 KB)",            pageSize == null ? "4 KB" : pageSize.getText());
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(kicker("Simulation output · Page table entries"));
        try {
            long spaceKb = toKb(addressSpace.getText());
            long pageKb  = toKb(pageSize.getText());
            long entries = spaceKb / pageKb;
            r.add(text("Number of page table entries = Address space ÷ Page size", 15, Theme.text()));
            r.add(text(String.format("%,d KB ÷ %,d KB = %,d entries", spaceKb, pageKb, entries), 20, Theme.green()));
            r.add(Box.createVerticalStrut(10));
            r.add(kicker("Practice set"));
            r.add(table(new String[]{"#", "Address space", "Page size", "Solution", "Entries"}, new Object[][]{
                    {1, "1 GB", "4 KB", "1,048,576 KB ÷ 4 KB", "262,144"},
                    {2, "2 GB", "8 KB", "2,097,152 KB ÷ 8 KB", "262,144"},
                    {3, "512 MB", "4 KB", "524,288 KB ÷ 4 KB", "131,072"}}));
        } catch (Exception e) {
            r.add(text("Input check: use a number + KB, MB or GB (e.g. 1 GB, 4 KB).", 14, Theme.pink()));
        }
    }

    /** "1 GB" -> 1,048,576 ; "512 MB" -> 524,288 ; "4 KB" -> 4 */
    static long toKb(String s) {
        String[] parts = s.trim().toUpperCase().split("\\s+");
        double n = Double.parseDouble(parts[0]);
        String unit = parts.length > 1 ? parts[1] : "KB";
        switch (unit) {
            case "GB": return (long) (n * 1024 * 1024);
            case "MB": return (long) (n * 1024);
            case "KB": return (long) n;
            default: throw new IllegalArgumentException(unit);
        }
    }
}
