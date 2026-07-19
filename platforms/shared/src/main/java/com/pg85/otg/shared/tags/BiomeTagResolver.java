package com.pg85.otg.shared.tags;

import com.pg85.otg.config.settings.biome.BiomeTagSettings;
import com.pg85.otg.constants.settings.BiomeType;
import com.pg85.otg.util.minecraft.WoodType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves a biome config's {@link BiomeTagSettings} (the `BiomeTags` and
 * `BiomeType` settings of a .bc file) into the MC tag keys the biome should be
 * bound to: vanilla `minecraft:is_*` tags where one exists, plus the `c:`
 * convention tags ({@link OTGBiomeTagPath}) that tag-targeted biome modifiers
 * and mob spawn selectors of other mods match against.
 *
 * Climate/vegetation families (cold, hot, wet, dry, temperate, dense/sparse
 * vegetation) are bound both as the parent tag and the per-dimension sub-tag
 * (e.g. c:is_cold + c:is_cold/overworld) — tag resolution has already happened
 * by the time these are injected, so parent tags don't expand automatically.
 */
public final class BiomeTagResolver {

    private static final String CONVENTION_NAMESPACE = "c";

    private BiomeTagResolver() {}

    public static Set<TagKey<Biome>> resolve(BiomeTagSettings s) {
        Set<TagKey<Biome>> tags = new LinkedHashSet<>();

        BiomeType type = s.getBiomeType() == null ? BiomeType.OVERWORLD : s.getBiomeType();
        String dimension = switch (type) {
            case OVERWORLD -> "overworld";
            case NETHER -> "nether";
            case END -> "end";
        };
        switch (type) {
            case OVERWORLD -> { tags.add(BiomeTags.IS_OVERWORLD); conv(tags, OTGBiomeTagPath.IS_OVERWORLD); }
            case NETHER -> { tags.add(BiomeTags.IS_NETHER); conv(tags, OTGBiomeTagPath.IS_NETHER); }
            case END -> { tags.add(BiomeTags.IS_END); conv(tags, OTGBiomeTagPath.IS_END); }
        }

        // Climate/vegetation families: parent + per-dimension sub-tag
        if (s.isCold()) climate(tags, OTGBiomeTagPath.IS_COLD, dimension);
        if (s.isHot()) climate(tags, OTGBiomeTagPath.IS_HOT, dimension);
        if (s.isWet()) climate(tags, OTGBiomeTagPath.IS_WET, dimension);
        if (s.isDry()) climate(tags, OTGBiomeTagPath.IS_DRY, dimension);
        if (s.isTemperate()) climate(tags, OTGBiomeTagPath.IS_TEMPERATE, dimension);
        if (s.isDenseVegetation()) climate(tags, OTGBiomeTagPath.IS_DENSE_VEGETATION, dimension);
        if (s.isSparseVegetation()) climate(tags, OTGBiomeTagPath.IS_SPARSE_VEGETATION, dimension);

        // Categories with a vanilla counterpart
        if (s.isBadlands()) { tags.add(BiomeTags.IS_BADLANDS); conv(tags, OTGBiomeTagPath.IS_BADLANDS); }
        if (s.isBeach()) { tags.add(BiomeTags.IS_BEACH); conv(tags, OTGBiomeTagPath.IS_BEACH); }
        if (s.isForest()) { tags.add(BiomeTags.IS_FOREST); conv(tags, OTGBiomeTagPath.IS_FOREST); }
        if (s.isHill()) { tags.add(BiomeTags.IS_HILL); conv(tags, OTGBiomeTagPath.IS_HILL); }
        if (s.isJungle()) { tags.add(BiomeTags.IS_JUNGLE); conv(tags, OTGBiomeTagPath.IS_JUNGLE); }
        if (s.isMountain()) { tags.add(BiomeTags.IS_MOUNTAIN); conv(tags, OTGBiomeTagPath.IS_MOUNTAIN); }
        if (s.isRiver()) { tags.add(BiomeTags.IS_RIVER); conv(tags, OTGBiomeTagPath.IS_RIVER); }
        if (s.isSavanna()) { tags.add(BiomeTags.IS_SAVANNA); conv(tags, OTGBiomeTagPath.IS_SAVANNA); }
        if (s.isTaiga()) { tags.add(BiomeTags.IS_TAIGA); conv(tags, OTGBiomeTagPath.IS_TAIGA); }
        if (s.isOcean() || s.isShallowOcean() || s.isDeepOcean()) {
            tags.add(BiomeTags.IS_OCEAN);
            conv(tags, OTGBiomeTagPath.IS_OCEAN);
        }
        if (s.isDeepOcean()) { tags.add(BiomeTags.IS_DEEP_OCEAN); conv(tags, OTGBiomeTagPath.IS_DEEP_OCEAN); }
        if (s.isShallowOcean()) conv(tags, OTGBiomeTagPath.IS_SHALLOW_OCEAN);

        // Mountain sub-tags imply the parent
        if (s.isPeak()) { tags.add(BiomeTags.IS_MOUNTAIN); conv(tags, OTGBiomeTagPath.IS_MOUNTAIN); conv(tags, OTGBiomeTagPath.IS_MOUNTAIN_PEAK); }
        if (s.isSlope()) { tags.add(BiomeTags.IS_MOUNTAIN); conv(tags, OTGBiomeTagPath.IS_MOUNTAIN); conv(tags, OTGBiomeTagPath.IS_MOUNTAIN_SLOPE); }

        // Convention-only categories
        if (s.isAquatic()) conv(tags, OTGBiomeTagPath.IS_AQUATIC);
        if (s.isAquaticIcy()) conv(tags, OTGBiomeTagPath.IS_AQUATIC_ICY);
        if (s.isBirchForest()) conv(tags, OTGBiomeTagPath.IS_BIRCH_FOREST);
        if (s.isCave()) conv(tags, OTGBiomeTagPath.IS_CAVE);
        if (s.isDarkForest()) conv(tags, OTGBiomeTagPath.IS_DARK_FOREST);
        if (s.isDead()) conv(tags, OTGBiomeTagPath.IS_DEAD);
        if (s.isDesert()) conv(tags, OTGBiomeTagPath.IS_DESERT);
        if (s.isFloral() || s.isFlowerForest()) conv(tags, OTGBiomeTagPath.IS_FLORAL);
        if (s.isFlowerForest()) conv(tags, OTGBiomeTagPath.IS_FLOWER_FOREST);
        if (s.isIcy()) conv(tags, OTGBiomeTagPath.IS_ICY);
        if (s.isLush()) conv(tags, OTGBiomeTagPath.IS_LUSH);
        if (s.isMagical()) conv(tags, OTGBiomeTagPath.IS_MAGICAL);
        if (s.isMushroom()) conv(tags, OTGBiomeTagPath.IS_MUSHROOM);
        if (s.isNetherForest()) conv(tags, OTGBiomeTagPath.IS_NETHER_FOREST);
        if (s.isOldGrowth()) conv(tags, OTGBiomeTagPath.IS_OLD_GROWTH);
        if (s.isOuterEndIsland()) conv(tags, OTGBiomeTagPath.IS_OUTER_END_ISLAND);
        if (s.isPlains()) conv(tags, OTGBiomeTagPath.IS_PLAINS);
        if (s.isPlateau()) conv(tags, OTGBiomeTagPath.IS_PLATEAU);
        if (s.isRare()) conv(tags, OTGBiomeTagPath.IS_RARE);
        if (s.isSandy()) conv(tags, OTGBiomeTagPath.IS_SANDY);
        if (s.isSnowy() || s.isSnowyPlains()) conv(tags, OTGBiomeTagPath.IS_SNOWY);
        if (s.isSnowyPlains()) conv(tags, OTGBiomeTagPath.IS_SNOWY_PLAINS);
        if (s.isSpooky()) conv(tags, OTGBiomeTagPath.IS_SPOOKY);
        if (s.isStonyShores()) conv(tags, OTGBiomeTagPath.IS_STONY_SHORES);
        if (s.isSwamp()) conv(tags, OTGBiomeTagPath.IS_SWAMP);
        if (s.isTheVoid()) conv(tags, OTGBiomeTagPath.IS_VOID);
        if (s.isUnderground()) conv(tags, OTGBiomeTagPath.IS_UNDERGROUND);
        if (s.isWasteland()) conv(tags, OTGBiomeTagPath.IS_WASTELAND);
        if (s.isWindswept()) conv(tags, OTGBiomeTagPath.IS_WINDSWEPT);
        if (s.isNoDefaultMonsters()) conv(tags, OTGBiomeTagPath.NO_DEFAULT_MONSTERS);
        if (s.isHiddenFromLocatorSelection()) conv(tags, OTGBiomeTagPath.HIDDEN_FROM_LOCATOR_SELECTION);

        // Tree types
        if (s.isConiferousTrees()) conv(tags, OTGBiomeTagPath.IS_TREE_CONIFEROUS);
        if (s.isDeciduousTrees()) conv(tags, OTGBiomeTagPath.IS_TREE_DECIDUOUS);
        if (s.isJungleTrees()) conv(tags, OTGBiomeTagPath.IS_TREE_JUNGLE);
        if (s.isSavannaTrees()) conv(tags, OTGBiomeTagPath.IS_TREE_SAVANNA);

        WoodType woodType = s.getPrimaryWoodType();
        if (woodType != null && woodType != WoodType.NONE) {
            convPath(tags, OTGBiomeTagPath.PRIMARY_WOOD_TYPE.getPath() + "/" + woodType.name().toLowerCase());
        }

        return tags;
    }

    private static void climate(Set<TagKey<Biome>> out, OTGBiomeTagPath family, String dimension) {
        conv(out, family);
        convPath(out, family.getPath() + "/" + dimension);
    }

    private static void conv(Set<TagKey<Biome>> out, OTGBiomeTagPath path) {
        convPath(out, path.getPath());
    }

    private static void convPath(Set<TagKey<Biome>> out, String path) {
        out.add(TagKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(CONVENTION_NAMESPACE, path)));
    }
}
