package com.technnext.hrms.letter.service;

import com.lowagie.text.pdf.BaseFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the REAL Microsoft Times New Roman (regular + bold) for the
 * Internship Offer Letter.
 *
 * Times New Roman is a proprietary Microsoft font, so it is NOT bundled with
 * this project (the repo ships only Tinos — a metric-compatible, but different,
 * typeface — see src/main/resources/fonts/NOTICE.md). This class looks for the
 * genuine font files, in this order:
 *
 *   1. Classpath:  src/main/resources/fonts/  (drop times.ttf + timesbd.ttf here)
 *   2. A directory given by env var HRMS_LETTER_FONT_DIR, or JVM property
 *      hrms.letter.font-dir
 *   3. The usual OS font folders (Windows, macOS, Linux msttcorefonts)
 *
 * A candidate file is only accepted if the font's own name table says it is
 * "Times New Roman" — so a renamed Tinos/Liberation file can never be mistaken
 * for it. If no genuine Times New Roman is found, {@link #resolve} falls back to
 * the supplied fallback fonts (Tinos), reports {@code actual == false}, and logs
 * a WARNING on every call — the substitution is never silent and the fallback is
 * never described as Times New Roman.
 */
final class TimesNewRomanFonts {

    private static final Logger log = LoggerFactory.getLogger(TimesNewRomanFonts.class);

    /** Result of font resolution. */
    record Resolved(BaseFont regular, BaseFont bold, boolean actual, String source) {}

    // {regular, bold} file-name pairs used by Windows / macOS / Debian msttcorefonts.
    private static final String[][] FILE_PAIRS = {
            {"times.ttf", "timesbd.ttf"},
            {"Times New Roman.ttf", "Times New Roman Bold.ttf"},
            {"Times_New_Roman.ttf", "Times_New_Roman_Bold.ttf"},
    };

    private static volatile Resolved cachedActual; // only a genuine find is cached

    private TimesNewRomanFonts() {}

    static Resolved resolve(BaseFont fallbackRegular, BaseFont fallbackBold) {
        Resolved hit = cachedActual;
        if (hit != null) return hit;

        synchronized (TimesNewRomanFonts.class) {
            if (cachedActual != null) return cachedActual;
            Resolved found = search();
            if (found != null) {
                cachedActual = found;
                log.info("Internship Offer Letter: using genuine Times New Roman from {}", found.source());
                warnIfRupeeMissing(found.regular());
                return found;
            }
        }
        // Not cached: re-checked on the next request, so installing the font
        // does not require a restart to be noticed after the next deploy/clear.
        log.warn("Internship Offer Letter: genuine Times New Roman was NOT found — falling back to Tinos "
                + "(metric-compatible, NOT Times New Roman). Place times.ttf and timesbd.ttf in "
                + "src/main/resources/fonts/ or set HRMS_LETTER_FONT_DIR. Searched: {}", searchDirsDescription());
        return new Resolved(fallbackRegular, fallbackBold, false, "Tinos fallback");
    }

    // ── search ──────────────────────────────────────────────────────────────

    private static Resolved search() {
        // 1. classpath
        for (String[] pair : FILE_PAIRS) {
            byte[] reg = readClasspath(pair[0]);
            byte[] bold = readClasspath(pair[1]);
            Resolved r = tryLoad(reg, pair[0], bold, pair[1], "classpath:fonts/");
            if (r != null) return r;
        }
        // 2 + 3. directories
        for (Path dir : candidateDirs()) {
            for (String[] pair : FILE_PAIRS) {
                byte[] reg = readFile(dir.resolve(pair[0]));
                byte[] bold = readFile(dir.resolve(pair[1]));
                Resolved r = tryLoad(reg, pair[0], bold, pair[1], dir.toString());
                if (r != null) return r;
            }
        }
        return null;
    }

    private static Resolved tryLoad(byte[] reg, String regName, byte[] bold, String boldName, String where) {
        if (reg == null || bold == null) return null;
        try {
            BaseFont r = BaseFont.createFont(regName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, reg, null);
            BaseFont b = BaseFont.createFont(boldName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bold, null);
            if (isTimesNewRoman(r) && isTimesNewRoman(b)) {
                return new Resolved(r, b, true, where + " (" + regName + ", " + boldName + ")");
            }
            log.warn("Ignoring {} / {} in {}: font name table does not say \"Times New Roman\".", regName, boldName, where);
        } catch (Exception e) {
            log.warn("Could not load {} / {} from {}: {}", regName, boldName, where, e.getMessage());
        }
        return null;
    }

    /** True only if the font's own family/full-name records contain "Times New Roman". */
    private static boolean isTimesNewRoman(BaseFont f) {
        for (String[][] names : new String[][][]{f.getFamilyFontName(), f.getFullFontName()}) {
            if (names == null) continue;
            for (String[] n : names) {
                if (n != null && n.length > 3 && n[3] != null
                        && n[3].toLowerCase().contains("times new roman")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void warnIfRupeeMissing(BaseFont f) {
        if (!f.charExists('\u20B9')) {
            log.warn("The Times New Roman file in use has no \u20B9 (rupee) glyph — a Paid Internship stipend "
                    + "would print without the rupee sign. Use a current Times New Roman (Windows 8+/Office 2013+).");
        }
    }

    private static List<Path> candidateDirs() {
        List<Path> dirs = new ArrayList<>();
        addDir(dirs, System.getenv("HRMS_LETTER_FONT_DIR"));
        addDir(dirs, System.getProperty("hrms.letter.font-dir"));
        String winDir = System.getenv("WINDIR");
        if (winDir != null && !winDir.isBlank()) addDir(dirs, winDir + "/Fonts");
        addDir(dirs, "C:/Windows/Fonts");
        addDir(dirs, "/usr/share/fonts/truetype/msttcorefonts");
        addDir(dirs, "/usr/share/fonts/truetype/mscorefonts");
        addDir(dirs, "/usr/share/fonts/msttcorefonts");
        addDir(dirs, "/System/Library/Fonts/Supplemental");
        addDir(dirs, "/Library/Fonts");
        return dirs;
    }

    private static void addDir(List<Path> dirs, String dir) {
        if (dir == null || dir.isBlank()) return;
        try {
            Path p = Paths.get(dir.trim());
            if (Files.isDirectory(p)) dirs.add(p);
        } catch (Exception ignored) {
            // malformed path on this OS — just skip it
        }
    }

    private static String searchDirsDescription() {
        List<String> parts = new ArrayList<>();
        parts.add("classpath:fonts/");
        for (Path p : candidateDirs()) parts.add(p.toString());
        return String.join(", ", parts);
    }

    private static byte[] readClasspath(String fileName) {
        try {
            ClassPathResource res = new ClassPathResource("fonts/" + fileName);
            if (!res.exists()) return null;
            try (InputStream in = res.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readFile(Path p) {
        try {
            return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }
}