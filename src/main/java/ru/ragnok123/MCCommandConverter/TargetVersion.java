package ru.ragnok123.MCCommandConverter;

/**
 * Version-dependent output details. Pack format numbers are from memory (not re-verified this session):
 * check them against the wiki before shipping, especially for versions after 1.21.5.
 */
public record TargetVersion(String name, int packFormat, boolean pluralFolders, boolean hasRotate, boolean snbtComponents) {
    public static final TargetVersion V1_20_4 = new TargetVersion("1.20.4", 26, true, false, false);
    public static final TargetVersion V1_20_6 = new TargetVersion("1.20.6", 41, true, false, false);
    public static final TargetVersion V1_21 = new TargetVersion("1.21", 48, false, false, false);
    public static final TargetVersion V1_21_4 = new TargetVersion("1.21.4", 61, false, true, false);
    public static final TargetVersion V1_21_5 = new TargetVersion("1.21.5", 71, false, true, true);
    public static final TargetVersion LATEST = V1_21_5;

    /** hasRotate for 1.21.2+ is from memory; unverified. */
    public static TargetVersion byName(String n) {
        for (TargetVersion v : new TargetVersion[]{V1_20_4, V1_20_6, V1_21, V1_21_4, V1_21_5})
            if (v.name.equals(n)) return v;
        throw new IllegalArgumentException("unknown target version " + n);
    }
    public String functionDir() { return pluralFolders ? "functions" : "function"; }
}
