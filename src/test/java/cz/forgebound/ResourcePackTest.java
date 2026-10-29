package cz.forgebound;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every item in the config has a model in the resource pack, and every model's textures exist. */
class ResourcePackTest {

    private static final Path ASSETS = TestFiles.PACK.resolve("assets");

    /** All custom_model_data thresholds and model references in an item definition (any nesting). */
    private static void walk(Object node, Set<Integer> thresholds, Set<String> models) {
        if (node instanceof Map<?, ?> map) {
            Object threshold = map.get("threshold");
            if (threshold instanceof Number n) {
                thresholds.add((int) Math.round(n.doubleValue()));
            }
            if ("minecraft:model".equals(map.get("type")) || "model".equals(map.get("type"))) {
                Object model = map.get("model");
                if (model instanceof String s) {
                    models.add(s);
                }
            }
            map.values().forEach(v -> walk(v, thresholds, models));
        } else if (node instanceof List<?> list) {
            list.forEach(v -> walk(v, thresholds, models));
        }
    }

    @Test
    void packMetadataIsForgebound() {
        String meta = TestFiles.read(TestFiles.PACK.resolve("pack.mcmeta"));
        assertTrue(meta.contains("Forgebound"), "pack.mcmeta still has the old name: " + meta);
        assertFalse(meta.contains("CustomCraft"), "pack.mcmeta still says CustomCraft");
    }

    @Test
    void allJsonFilesParse() {
        List<String> broken = new ArrayList<>();
        for (Path file : TestFiles.files(ASSETS, ".json")) {
            try {
                TestFiles.yaml(file);
            } catch (RuntimeException ex) {
                broken.add(TestFiles.PACK.relativize(file) + ": " + ex.getMessage());
            }
        }
        assertTrue(broken.isEmpty(), "broken JSON: " + broken);
    }

    @Test
    void everyConfigItemHasAModel() {
        Map<String, Object> config = TestFiles.yaml(TestFiles.RESOURCES.resolve("config.yml"));
        List<String> missing = new ArrayList<>();
        for (String kind : List.of("weapons", "relics", "materials")) {
            TestFiles.map(config.get(kind)).forEach((id, raw) -> {
                Map<String, Object> s = TestFiles.map(raw);
                String material = String.valueOf(s.get("material")).toLowerCase(Locale.ROOT);
                Path definition = ASSETS.resolve("minecraft/items/" + material + ".json");
                if (!Files.isRegularFile(definition)) {
                    missing.add(kind + "." + id + " (no items/" + material + ".json)");
                    return;
                }
                Set<Integer> thresholds = new HashSet<>();
                walk(TestFiles.yaml(definition), thresholds, new HashSet<>());
                if (!thresholds.contains(((Number) s.get("custom-model-data")).intValue())) {
                    missing.add(kind + "." + id + " (cmd " + s.get("custom-model-data") + " not in items/" + material + ".json)");
                }
            });
        }
        assertTrue(missing.isEmpty(), "items without a model: " + missing);
    }

    @Test
    void referencedModelsAndTexturesExist() {
        Set<String> models = new HashSet<>();
        for (Path file : TestFiles.files(ASSETS.resolve("minecraft/items"), ".json")) {
            walk(TestFiles.yaml(file), new HashSet<>(), models);
        }
        List<String> missing = new ArrayList<>();
        Set<String> checked = new HashSet<>();
        List<String> queue = new ArrayList<>(models);
        while (!queue.isEmpty()) {
            String model = queue.removeLast();
            if (!model.startsWith("customcraft:") || !checked.add(model)) {
                continue;                                     // vanilla models are in the game itself
            }
            Path file = TestFiles.asset(model, "models", ".json");
            if (!Files.isRegularFile(file)) {
                missing.add("model " + model);
                continue;
            }
            Map<String, Object> json = TestFiles.yaml(file);
            if (json.get("parent") instanceof String parent) {
                queue.add(parent);
            }
            TestFiles.map(json.get("textures")).values().forEach(t -> {
                String texture = String.valueOf(t);
                if (texture.startsWith("customcraft:") && !Files.isRegularFile(TestFiles.asset(texture, "textures", ".png"))) {
                    missing.add("texture " + texture + " (used by " + model + ")");
                }
            });
        }
        assertTrue(missing.isEmpty(), "missing: " + missing);
    }

    @Test
    void equipmentTexturesExist() {
        List<String> missing = new ArrayList<>();
        for (Path file : TestFiles.files(ASSETS.resolve("customcraft/equipment"), ".json")) {
            TestFiles.map(TestFiles.yaml(file).get("layers")).forEach((layer, entries) -> {
                for (Object e : TestFiles.list(entries)) {
                    String texture = String.valueOf(TestFiles.map(e).get("texture"));
                    Path png = TestFiles.asset(texture, "textures/entity/equipment/" + layer, ".png");
                    if (!Files.isRegularFile(png)) {
                        missing.add(file.getFileName() + ": " + texture + " (" + layer + ")");
                    }
                }
            });
        }
        assertTrue(missing.isEmpty(), "missing worn textures: " + missing);
    }

    @Test
    void fontBitmapsExist() {
        List<String> missing = new ArrayList<>();
        for (Path file : TestFiles.files(ASSETS.resolve("customcraft/font"), ".json")) {
            for (Object p : TestFiles.list(TestFiles.yaml(file).get("providers"))) {
                Object bitmap = TestFiles.map(p).get("file");
                if (bitmap instanceof String s && !Files.isRegularFile(TestFiles.asset(s, "textures", ""))) {
                    missing.add(file.getFileName() + ": " + s);
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing font images: " + missing);
    }

    @Test
    void enchantmentNamesAreTranslated() {
        for (String lang : List.of("en_us", "cs_cz")) {
            Map<String, Object> entries = TestFiles.yaml(ASSETS.resolve("customcraft/lang/" + lang + ".json"));
            for (String id : List.of("forge_touch", "bounty_hunter", "lifesteal", "bleed", "execute", "cripple", "wisdom",
                    "overload", "dodge", "implants", "springs", "warding", "grounded", "cleanse", "telepathy", "vein_miner")) {
                assertTrue(entries.get("enchantment.forgebound." + id) instanceof String,
                        lang + " is missing the name of " + id);
            }
        }
    }
}
