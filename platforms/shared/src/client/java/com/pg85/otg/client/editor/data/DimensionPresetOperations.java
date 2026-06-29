package com.pg85.otg.client.editor.data;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.shared.registry.WorldPresetRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * CRUD for DimensionPreset folders under {@code {otgRoot}/DimensionPresets/}.
 *
 * A preset is a directory containing {@code DimensionPresetConfig.ini} plus a
 * {@code Biomes/} subfolder. Operations work at the folder level — new presets
 * are seeded from an existing template directory (we don't synthesize a config
 * from scratch because the .ini is 600+ lines of tuned terrain settings).
 */
public final class DimensionPresetOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DimensionPresetOperations.class);

    /**
     * Legacy config keys that {@code DimensionPresetConfig.renameOldSettings()} maps onto a
     * canonical setting at load time. When patching the canonical key we must rewrite any
     * such alias line in place — otherwise the loader reads the stale alias value (which it
     * renames onto the canonical setting) in preference to our value, silently overriding it.
     * This is exactly how a DefaultPreset-templated preset kept {@code ShortPresetName: otg_default}
     * and collided with DefaultPreset's registry name.
     */
    private static final Map<String, String> LEGACY_KEY_ALIASES = Map.of("ShortPresetName", "RegistryName");

    private DimensionPresetOperations() {}

    /**
     * Copy {@code templateDir} into {@code DimensionPresets/{folderName}/} and rewrite
     * identity fields in the new {@code DimensionPresetConfig.ini}.
     *
     * @param otgRoot   OTG root folder (engine.getOTGRootFolder())
     * @param templateDir source preset directory to copy
     * @param folderName desired folder name (collision resolved by suffix)
     * @param displayName user-facing display name (written to DisplayName)
     * @param registryName MC registry id (written to RegistryName)
     * @param author author (written to Author)
     * @param description description (written to Description)
     * @return path of the new preset directory, or null on failure
     */
    public static Path newFromTemplate(Path otgRoot,
                                       Path templateDir,
                                       String folderName,
                                       String displayName,
                                       String registryName,
                                       String author,
                                       String description) {
        if (templateDir == null || !Files.isDirectory(templateDir)) {
            LOG.error("Template directory missing: {}", templateDir);
            return null;
        }
        Path target = resolveUniqueFolder(otgRoot, folderName);
        try {
            copyDirectory(templateDir, target);
        } catch (IOException e) {
            LOG.error("Failed to copy template {} → {}: {}", templateDir, target, e.getMessage());
            return null;
        }

        Path configFile = findConfigFile(target);
        if (configFile != null) {
            Map<String, String> patches = new LinkedHashMap<>();
            patches.put("DisplayName", nullSafe(displayName));
            patches.put("RegistryName", nullSafe(registryName));
            patches.put("Author", nullSafe(author));
            patches.put("Description", nullSafe(description));
            if (!patchIniSettings(configFile, patches)) {
                LOG.warn("Created {} but failed to patch identity fields", target.getFileName());
            }
        } else {
            LOG.warn("Created {} but no config file found — preset will use defaults",
                target.getFileName());
        }

        LOG.info("Created DimensionPreset: {} (from template {})",
            target.getFileName(), templateDir.getFileName());
        return target;
    }

    /**
     * Clone an existing preset directory. Same as {@link #newFromTemplate}, but
     * preserves the original Author/Description (only DisplayName/RegistryName
     * change). The new folder name is derived from the new display name.
     *
     * @param registryName MC registry id for the clone — must already be unique
     *                     (see {@link #resolveUniqueRegistryName}); a duplicate
     *                     makes the loader silently drop the clone.
     */
    public static Path cloneFrom(Path sourcePresetDir, Path otgRoot, String newDisplayName, String registryName) {
        if (sourcePresetDir == null || !Files.isDirectory(sourcePresetDir)) {
            LOG.error("Source preset directory missing: {}", sourcePresetDir);
            return null;
        }
        String folderName = WorldPresetRegistrar.normalizeId(newDisplayName);
        if (folderName == null || folderName.isEmpty()) folderName = "dimensionpreset";
        Path target = resolveUniqueFolder(otgRoot, folderName);
        try {
            copyDirectory(sourcePresetDir, target);
        } catch (IOException e) {
            LOG.error("Failed to clone {} → {}: {}", sourcePresetDir, target, e.getMessage());
            return null;
        }

        Path configFile = findConfigFile(target);
        if (configFile != null) {
            Map<String, String> patches = new LinkedHashMap<>();
            patches.put("DisplayName", newDisplayName);
            patches.put("RegistryName", registryName);
            patchIniSettings(configFile, patches);
        } else {
            LOG.warn("Cloned {} but no config file found — RegistryName collision likely",
                target.getFileName());
        }
        LOG.info("Cloned DimensionPreset: {} → {}", sourcePresetDir.getFileName(), target.getFileName());
        return target;
    }

    /**
     * Pick a registry id derived from {@code base} that none of {@code existing}
     * already uses (case-insensitive), appending {@code _1}, {@code _2}, ... on
     * collision. Mirrors {@link #resolveUniqueFolder} for the registry-name space,
     * so clones never collide into a silently-dropped preset.
     */
    public static String resolveUniqueRegistryName(Collection<String> existing, String base) {
        String candidate = (base == null || base.isEmpty()) ? "dimensionpreset" : base;
        Set<String> taken = new HashSet<>();
        if (existing != null) {
            for (String r : existing) {
                if (r != null) taken.add(r.toLowerCase(Locale.ROOT));
            }
        }
        String result = candidate;
        int counter = 1;
        while (taken.contains(result.toLowerCase(Locale.ROOT))) {
            result = candidate + "_" + counter;
            counter++;
        }
        return result;
    }

    /**
     * Resolve the preset's config file, preferring the modern name but falling
     * back to the legacy {@code WorldConfig.ini} used by pre-rename OTG presets
     * (Void, Biome Bundle, etc.). Returns null if neither exists.
     */
    private static Path findConfigFile(Path presetDir) {
        Path modern = presetDir.resolve(Constants.DIMENSION_PRESET_CONFIG_FILE);
        if (Files.exists(modern)) return modern;
        Path legacy = presetDir.resolve(Constants.LEGACY_WORLD_CONFIG_FILE);
        if (Files.exists(legacy)) return legacy;
        return null;
    }

    /**
     * Recursively delete a preset directory. Refuses to delete {@code DefaultPreset}.
     */
    public static boolean delete(Path presetDir) {
        if (presetDir == null) return false;
        if (Constants.DEFAULT_PRESET_NAME.equals(presetDir.getFileName().toString())) {
            LOG.warn("Refusing to delete DefaultPreset");
            return false;
        }
        try {
            deleteRecursive(presetDir);
            LOG.info("Deleted DimensionPreset: {}", presetDir.getFileName());
            return true;
        } catch (IOException e) {
            LOG.error("Failed to delete {}: {}", presetDir.getFileName(), e.getMessage());
            return false;
        }
    }

    /**
     * Find every WorldPreset YAML that references the given DimensionPreset folder name.
     * Used by the manage screen to warn before delete.
     */
    public static List<Path> findWorldPresetsReferencing(Path otgRoot, String folderName) {
        List<Path> result = new ArrayList<>();
        if (folderName == null || folderName.isBlank()) return result;
        var entries = WorldPresetFileScanner.scan(otgRoot);
        for (var e : entries) {
            if (e.config() == null) continue;
            if (slotMatches(e.config().Overworld, folderName)
                || slotMatches(e.config().Nether, folderName)
                || slotMatches(e.config().End, folderName)
                || dimensionsMatch(e.config().Dimensions, folderName)) {
                result.add(e.path());
            }
        }
        return result;
    }

    private static boolean slotMatches(WorldPresetConfig.OTGDimension slot, String folderName) {
        if (slot == null) return false;
        return folderName.equalsIgnoreCase(slot.PresetFolderName);
    }

    private static boolean dimensionsMatch(List<WorldPresetConfig.OTGDimension> dimensions, String folderName) {
        if (dimensions == null) return false;
        for (var dim : dimensions) {
            if (slotMatches(dim, folderName)) return true;
        }
        return false;
    }

    /**
     * Resolve a unique folder path. If {@code baseName} is taken, append {@code _1}, {@code _2}, ...
     */
    public static Path resolveUniqueFolder(Path otgRoot, String baseName) {
        Path dir = otgRoot.resolve(Constants.DIMENSION_PRESETS_FOLDER);
        Path candidate = dir.resolve(baseName);
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(baseName + "_" + counter);
            counter++;
        }
        return candidate;
    }

    /**
     * Suggest a folder name from a display name (alphanumeric only, no underscores
     * because folder names are visible to the user and underscores look ugly).
     * Falls back to {@code DimensionPreset} if input is empty.
     */
    public static String suggestFolderName(String displayName) {
        if (displayName == null) return "DimensionPreset";
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < displayName.length(); i++) {
            char c = displayName.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            } else {
                capitalize = true;
            }
        }
        return sb.length() == 0 ? "DimensionPreset" : sb.toString();
    }

    /**
     * In-place line-by-line rewrite of given INI settings. Preserves comments,
     * blank lines, and unknown keys. Only top-level {@code Key: Value} lines
     * matching one of the supplied keys are replaced. ConfigFunction lines
     * (containing {@code (}) and section headers (starting with {@code <})
     * are left alone.
     */
    static boolean patchIniSettings(Path iniFile, Map<String, String> patches) {
        try {
            List<String> lines = Files.readAllLines(iniFile);
            List<String> output = new ArrayList<>(lines.size());
            Map<String, String> remaining = new LinkedHashMap<>(patches);

            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("<")
                        && !trimmed.contains("(") && trimmed.contains(":")) {
                    int colonIdx = trimmed.indexOf(':');
                    String key = trimmed.substring(0, colonIdx).trim();
                    // Resolve legacy aliases (e.g. ShortPresetName) to the canonical patch key,
                    // so we overwrite the alias line in place rather than appending a duplicate
                    // the loader would then override via renameOldSetting().
                    String canonical = LEGACY_KEY_ALIASES.getOrDefault(key, key);
                    if (remaining.containsKey(canonical)) {
                        String indent = line.substring(0, line.indexOf(trimmed));
                        output.add(indent + canonical + ": " + remaining.remove(canonical));
                        continue;
                    }
                }
                output.add(line);
            }

            if (!remaining.isEmpty()) {
                output.add("");
                output.add("# Added by OTG Editor");
                for (var entry : remaining.entrySet()) {
                    output.add(entry.getKey() + ": " + entry.getValue());
                }
            }

            Files.write(iniFile, output);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to patch {}: {}", iniFile.getFileName(), e.getMessage());
            return false;
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
