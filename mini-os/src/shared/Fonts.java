package shared;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the 6 Mini OS fonts from the /fonts folder (bundled, so it looks the same on every laptop).
 * OWNER: Member 3.
 */
public final class Fonts {
    private Fonts() {}
    private static final Map<String, Font> cache = new HashMap<>();

    private static Font load(String file) {
        return cache.computeIfAbsent(file, f -> {
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, new File("fonts/" + f));
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            } catch (Exception e) {
                System.err.println("Font missing: " + f + " (using SansSerif)");
                return new Font("SansSerif", Font.PLAIN, 12);
            }
        });
    }

    /** Brush-script module titles. */
    public static Font title(float size)  { return load("Yellowtail-Regular.ttf").deriveFont(size); }
    /** Small arcade labels, RUN SIMULATION, HYPERPUFF. */
    public static Font arcade(float size) { return load("PressStart2P-Regular.ttf").deriveFont(size); }
    /** Table headers and field labels. */
    public static Font label(float size)  { return load("Silkscreen-Regular.ttf").deriveFont(size); }
    /** Body text, inputs, results, numbers. */
    public static Font body(float size)   { return load("DotGothic16-Regular.ttf").deriveFont(size); }
    /** "OS Lab" and sidebar module names. */
    public static Font pixel(float size)  { return load("PixelifySans-Bold.ttf").deriveFont(size); }
    /** Clean bold UI text. */
    public static Font ui(float size)     { return load("Nunito-VariableFont_wght.ttf").deriveFont(Font.BOLD, size); }
    /** Sidebar module names (Nunito Bold). */
    public static Font uiBold(float size)  { return load("Nunito-Bold.ttf").deriveFont(size); }
    /** The open module in the sidebar (Nunito ExtraBold). */
    public static Font uiExtraBold(float size) { return load("Nunito-ExtraBold.ttf").deriveFont(size); }
}
