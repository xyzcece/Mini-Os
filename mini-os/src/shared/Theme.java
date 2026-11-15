package shared;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Colors from the Figma "Arcade Theme" variables (Dark + Light modes).
 * OWNER: Member 3. Other members: read from Theme, never hard-code colors.
 */
public final class Theme {
    private Theme() {}

    public static boolean dark = false;
    private static final List<Runnable> listeners = new ArrayList<>();

    public static void toggle() { dark = !dark; listeners.forEach(Runnable::run); }
    public static void onChange(Runnable r) { listeners.add(r); }

    private static Color c(String light, String darkHex) { return Color.decode(dark ? darkHex : light); }

    public static Color bg()       { return c("#fff3fa", "#120c28"); }
    public static Color panel()    { return c("#ffffff", "#1c1438"); }
    public static Color panel2()   { return c("#fbf1ff", "#261c4a"); }
    public static Color line()     { return c("#ead0ee", "#4b3a85"); }
    public static Color text()     { return c("#3d3150", "#f1ecff"); }
    public static Color muted()    { return c("#8b7c99", "#a69ccc"); }
    public static Color pink()     { return c("#ff4fa8", "#ff4fb0"); }
    public static Color cyan()     { return c("#3fc6e8", "#5fd3ef"); }
    public static Color yellow()   { return c("#ffd95e", "#ffe27a"); }
    public static Color green()    { return c("#8ad85a", "#9be36a"); }
    public static Color purple()   { return c("#a98bff", "#b99bff"); }
    public static Color orange()   { return c("#ffa95c", "#ffb36b"); }
    public static Color input()    { return c("#ffffff", "#120c28"); }
    public static Color inputLine(){ return c("#e6c7ea", "#4b3a85"); }

    /** Title bar / taskbar pink gradient (top, bottom). */
    public static Color barTop()   { return c("#ffa6d5", "#ff6fc0"); }
    public static Color barBottom(){ return c("#d82b85", "#7a1256"); }
    /** Sidebar gradient. */
    public static Color sideTop()  { return c("#f6ecff", "#2a1a4a"); }
    public static Color sideBottom(){ return c("#e3f6ff", "#132440"); }
}
