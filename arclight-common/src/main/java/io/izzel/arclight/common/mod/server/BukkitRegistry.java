package io.izzel.arclight.common.mod.server;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.izzel.arclight.api.ArclightVersion;
import io.izzel.arclight.api.EnumHelper;
import io.izzel.arclight.api.Unsafe;
import io.izzel.arclight.common.bridge.bukkit.EntityTypeBridge;
import io.izzel.arclight.common.bridge.bukkit.MaterialBridge;
import io.izzel.arclight.common.bridge.bukkit.SimpleRegistryBridge;
import io.izzel.arclight.common.mod.ArclightMod;
import io.izzel.arclight.common.mod.util.ResourceLocationUtil;
import io.izzel.arclight.common.mod.util.types.ArclightEnchantment;
import io.izzel.arclight.common.mod.util.types.ArclightPotionEffect;
import io.izzel.arclight.i18n.ArclightConfig;
import io.izzel.arclight.i18n.conf.EntityPropertySpec;
import io.izzel.arclight.i18n.conf.MaterialPropertySpec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.CrashReportCallables;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import org.bukkit.Art;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v.CraftCrashReport;
import org.bukkit.craftbukkit.v.CraftStatistic;
import org.bukkit.craftbukkit.v.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v.potion.CraftPotionUtil;
import org.bukkit.craftbukkit.v.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.v.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.v.util.CraftSpawnCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pose;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"ConstantConditions", "deprecation"})
public class BukkitRegistry {

    private static final List<Class<?>> MAT_CTOR = ImmutableList.of(int.class);
    private static final List<Class<?>> ENTITY_CTOR = ImmutableList.of(String.class, Class.class, int.class);
    private static final List<Class<?>> ENV_CTOR = ImmutableList.of(int.class);
    private static final Map<String, Material> BY_NAME = Unsafe.getStatic(Material.class, "BY_NAME");
    private static final Map<Block, Material> BLOCK_MATERIAL = Unsafe.getStatic(CraftMagicNumbers.class, "BLOCK_MATERIAL");
    private static final Map<Item, Material> ITEM_MATERIAL = Unsafe.getStatic(CraftMagicNumbers.class, "ITEM_MATERIAL");
    private static final Map<Material, Item> MATERIAL_ITEM = Unsafe.getStatic(CraftMagicNumbers.class, "MATERIAL_ITEM");
    private static final Map<Material, Block> MATERIAL_BLOCK = Unsafe.getStatic(CraftMagicNumbers.class, "MATERIAL_BLOCK");
    private static final Map<String, EntityType> ENTITY_NAME_MAP = Unsafe.getStatic(EntityType.class, "NAME_MAP");
    private static final Map<Integer, World.Environment> ENVIRONMENT_MAP = Unsafe.getStatic(World.Environment.class, "lookup");
    static final BiMap<ResourceKey<LevelStem>, World.Environment> DIM_MAP =
        HashBiMap.create(ImmutableMap.<ResourceKey<LevelStem>, World.Environment>builder()
            .put(LevelStem.OVERWORLD, World.Environment.NORMAL)
            .put(LevelStem.NETHER, World.Environment.NETHER)
            .put(LevelStem.END, World.Environment.THE_END)
            .build());
    private static final Map<String, Art> ART_BY_NAME = Unsafe.getStatic(Art.class, "BY_NAME");
    private static final Map<Integer, Art> ART_BY_ID = Unsafe.getStatic(Art.class, "BY_ID");
    private static final BiMap<ResourceLocation, Statistic> STATS = HashBiMap.create(Unsafe.getStatic(CraftStatistic.class, "statistics"));
    private static final BiMap<Fluid, org.bukkit.Fluid> FLUIDTYPE_FLUID = Unsafe.getStatic(CraftMagicNumbers.class, "FLUIDTYPE_FLUID");

    public static void registerAll(DedicatedServer console) {
        CrashReportCallables.registerCrashCallable("Arclight Release", ArclightVersion.current()::getReleaseName);
        CrashReportCallables.registerCrashCallable("Arclight", new CraftCrashReport());
        loadMaterials();
        loadPotions();
        loadEnchantments();
        loadEntities();
        loadVillagerProfessions();
        loadBiomes(console);
        loadArts();
        loadStats();
        loadSpawnCategory();
        loadEndDragonPhase();
        loadCookingBookCategory();
        loadFluids();
        try {
            for (var field : org.bukkit.Registry.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof org.bukkit.Registry.SimpleRegistry<?> registry) {
                    ((SimpleRegistryBridge) (Object) registry).bridge$reload();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void loadFluids() {
        var id = org.bukkit.Fluid.values().length;
        var newTypes = new ArrayList<org.bukkit.Fluid>();
        Field keyField = Arrays.stream(org.bukkit.Fluid.class.getDeclaredFields()).filter(it -> it.getName().equals("key")).findAny().orElse(null);
        long keyOffset = Unsafe.objectFieldOffset(keyField);
        for (var fluidType : ForgeRegistries.FLUIDS) {
            if (!FLUIDTYPE_FLUID.containsKey(fluidType)) {
                var key = ForgeRegistries.FLUIDS.getKey(fluidType);
                var name = ResourceLocationUtil.standardize(key);
                var bukkit = EnumHelper.makeEnum(org.bukkit.Fluid.class, name, id++, List.of(), List.of());
                Unsafe.putObject(bukkit, keyOffset, CraftNamespacedKey.fromMinecraft(key));
                newTypes.add(bukkit);
                FLUIDTYPE_FLUID.put(fluidType, bukkit);
                ArclightMod.LOGGER.debug("Registered {} as fluid {}", key, bukkit);
            }
        }
        EnumHelper.addEnums(org.bukkit.Fluid.class, newTypes);
    }

    private static void loadCookingBookCategory() {
        var id = CookingBookCategory.values().length;
        var newTypes = new ArrayList<org.bukkit.inventory.recipe.CookingBookCategory>();
        for (CookingBookCategory category : CookingBookCategory.values()) {
            try {
                CraftRecipe.getCategory(category);
            } catch (Exception e) {
                var name = category.name();
                var bukkit = EnumHelper.makeEnum(org.bukkit.inventory.recipe.CookingBookCategory.class, name, id++, List.of(), List.of());
                newTypes.add(bukkit);
                ArclightMod.LOGGER.debug("Registered {} as cooking category {}", name, bukkit);
            }
        }
        EnumHelper.addEnums(org.bukkit.inventory.recipe.CookingBookCategory.class, newTypes);
    }

    private static void loadEndDragonPhase() {
        var max = EnderDragonPhase.getCount();
        var newTypes = new ArrayList<EnderDragon.Phase>();
        for (var id = EnderDragon.Phase.values().length; id < max; id++) {
            var name = "MOD_PHASE_" + id;
            var newPhase = EnumHelper.makeEnum(EnderDragon.Phase.class, name, id, List.of(), List.of());
            newTypes.add(newPhase);
            ArclightMod.LOGGER.debug("Registered {} as ender dragon phase {}", name, newPhase);
        }
        EnumHelper.addEnums(EnderDragon.Phase.class, newTypes);
    }

    private static void loadSpawnCategory() {
        var id = SpawnCategory.values().length;
        var newTypes = new ArrayList<SpawnCategory>();
        for (var category : MobCategory.values()) {
            try {
                CraftSpawnCategory.toBukkit(category);
            } catch (Exception e) {
                var name = category.name();
                var spawnCategory = EnumHelper.makeEnum(SpawnCategory.class, name, id++, List.of(), List.of());
                newTypes.add(spawnCategory);
                ArclightMod.LOGGER.debug("Registered {} as spawn category {}", name, spawnCategory);
            }
        }
        EnumHelper.addEnums(SpawnCategory.class, newTypes);
    }

    private static void loadStats() {
        int i = Statistic.values().length;
        List<Statistic> newTypes = new ArrayList<>();
        Field key = Arrays.stream(Statistic.class.getDeclaredFields()).filter(it -> it.getName().equals("key")).findAny().orElse(null);
        long keyOffset = Unsafe.objectFieldOffset(key);
        for (StatType<?> statType : ForgeRegistries.STAT_TYPES) {
            if (statType == Stats.CUSTOM) continue;
            var location = ForgeRegistries.STAT_TYPES.getKey(statType);
            Statistic statistic = STATS.get(location);
            if (statistic == null) {
                String standardName = ResourceLocationUtil.standardize(location);
                Statistic.Type type;
                if (statType.getRegistry() == BuiltInRegistries.ENTITY_TYPE) {
                    type = Statistic.Type.ENTITY;
                } else if (statType.getRegistry() == BuiltInRegistries.BLOCK) {
                    type = Statistic.Type.BLOCK;
                } else if (statType.getRegistry() == BuiltInRegistries.ITEM) {
                    type = Statistic.Type.ITEM;
                } else {
                    type = Statistic.Type.UNTYPED;
                }
                statistic = EnumHelper.makeEnum(Statistic.class, standardName, i, ImmutableList.of(Statistic.Type.class), ImmutableList.of(type));
                Unsafe.putObject(statistic, keyOffset, location);
                newTypes.add(statistic);
                STATS.put(location, statistic);
                ArclightMod.LOGGER.debug("Registered {} as stats {}", location, statistic);
                i++;
            }
        }
        for (ResourceLocation location : BuiltInRegistries.CUSTOM_STAT) {
            Statistic statistic = STATS.get(location);
            if (statistic == null) {
                String standardName = ResourceLocationUtil.standardize(location);
                statistic = EnumHelper.makeEnum(Statistic.class, standardName, i, ImmutableList.of(), ImmutableList.of());
                Unsafe.putObject(statistic, keyOffset, location);
                newTypes.add(statistic);
                STATS.put(location, statistic);
                ArclightMod.LOGGER.debug("Registered {} as custom stats {}", location, statistic);
                i++;
            }
        }
        EnumHelper.addEnums(Statistic.class, newTypes);
        putStatic(CraftStatistic.class, "statistics", STATS);
    }

    private static void loadArts() {
        int i = Art.values().length;
        List<Art> newTypes = new ArrayList<>();
        Field key = Arrays.stream(Art.class.getDeclaredFields()).filter(it -> it.getName().equals("key")).findAny().orElse(null);
        long keyOffset = Unsafe.objectFieldOffset(key);
        for (var paintingType : ForgeRegistries.PAINTING_VARIANTS) {
            var location = ForgeRegistries.PAINTING_VARIANTS.getKey(paintingType);
            String lookupName = location.getPath().toLowerCase(Locale.ROOT);
            Art bukkit = Art.getByName(lookupName);
            if (bukkit == null) {
                String standardName = ResourceLocationUtil.standardize(location);
                bukkit = EnumHelper.makeEnum(Art.class, standardName, i, ImmutableList.of(int.class, int.class, int.class), ImmutableList.of(i, paintingType.getWidth(), paintingType.getHeight()));
                newTypes.add(bukkit);
                Unsafe.putObject(bukkit, keyOffset, CraftNamespacedKey.fromMinecraft(location));
                ART_BY_ID.put(i, bukkit);
                ART_BY_NAME.put(lookupName, bukkit);
                ArclightMod.LOGGER.debug("Registered {} as art {}", location, bukkit);
                i++;
            }
        }
        EnumHelper.addEnums(Art.class, newTypes);
    }

    private static void loadBiomes(DedicatedServer console) {
        int i = Biome.values().length;
        List<Biome> newTypes = new ArrayList<>();
        Field key = Arrays.stream(Biome.class.getDeclaredFields()).filter(it -> it.getName().equals("key")).findAny().orElse(null);
        long keyOffset = Unsafe.objectFieldOffset(key);
        var registry = console.registryAccess().registryOrThrow(Registries.BIOME);
        for (net.minecraft.world.level.biome.Biome biome : registry) {
            var location = registry.getKey(biome);
            String name = ResourceLocationUtil.standardize(location);
            Biome bukkit;
            try {
                bukkit = Biome.valueOf(name);
            } catch (Throwable t) {
                bukkit = null;
            }
            if (bukkit == null) {
                bukkit = EnumHelper.makeEnum(Biome.class, name, i++, ImmutableList.of(), ImmutableList.of());
                newTypes.add(bukkit);
                Unsafe.putObject(bukkit, keyOffset, CraftNamespacedKey.fromMinecraft(location));
                ArclightMod.LOGGER.debug("Registered {} as biome {}", location, bukkit);
            }
        }
        EnumHelper.addEnums(Biome.class, newTypes);
        ArclightMod.LOGGER.info("registry.biome", newTypes.size());
    }

    private static void loadVillagerProfessions() {
        int i = Villager.Profession.values().length;
        List<Villager.Profession> newTypes = new ArrayList<>();
        Field key = Arrays.stream(Villager.Profession.class.getDeclaredFields()).filter(it -> it.getName().equals("key")).findAny().orElse(null);
        long keyOffset = Unsafe.objectFieldOffset(key);
        for (VillagerProfession villagerProfession : ForgeRegistries.VILLAGER_PROFESSIONS) {
            var location = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(villagerProfession);
            String name = ResourceLocationUtil.standardize(location);
            Villager.Profession profession;
            try {
                profession = Villager.Profession.valueOf(name);
            } catch (Throwable t) {
                profession = null;
            }
            if (profession == null) {
                profession = EnumHelper.makeEnum(Villager.Profession.class, name, i++, ImmutableList.of(), ImmutableList.of());
                newTypes.add(profession);
                Unsafe.putObject(profession, keyOffset, CraftNamespacedKey.fromMinecraft(location));
                ArclightMod.LOGGER.debug("Registered {} as villager profession {}", location, profession);
            }
        }
        EnumHelper.addEnums(Villager.Profession.class, newTypes);
        ArclightMod.LOGGER.info("registry.villager-profession", newTypes.size());
    }

    public static void registerEnvironments(Registry<LevelStem> registry) {
        int i = World.Environment.values().length;
        List<World.Environment> newTypes = new ArrayList<>();
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : registry.entrySet()) {
            ResourceKey<LevelStem> key = entry.getKey();
            World.Environment environment = DIM_MAP.get(key);
            if (environment == null) {
                String name = ResourceLocationUtil.standardize(key.location());
                environment = EnumHelper.makeEnum(World.Environment.class, name, i, ENV_CTOR, ImmutableList.of(i - 1));
                newTypes.add(environment);
                ENVIRONMENT_MAP.put(i - 1, environment);
                DIM_MAP.put(key, environment);
                ArclightMod.LOGGER.debug("Registered {} as environment {}", key.location(), environment);
                i++;
            }
        }
        EnumHelper.addEnums(World.Environment.class, newTypes);
        ArclightMod.LOGGER.info("registry.environment", newTypes.size());
    }

    private static void loadEntities() {
        int origin = EntityType.values().length;
        int i = origin;
        List<EntityType> newTypes = new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getEntries().size() - origin + 1); // UNKNOWN
        for (net.minecraft.world.entity.EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation location = ForgeRegistries.ENTITY_TYPES.getKey(type);
            EntityType entityType = null;
            boolean found = false;
            if (location.getNamespace().equals(NamespacedKey.MINECRAFT)) {
                entityType = EntityType.fromName(location.getPath());
                if (entityType != null) {
                    found = true;
                    ((EntityTypeBridge) (Object) entityType).bridge$setHandle(type);
                }
                else ArclightMod.LOGGER.warn("Not found {} in {}", location, EntityType.class);
            }
            if (!found) {
                String name = ResourceLocationUtil.standardize(location);
                entityType = EnumHelper.makeEnum(EntityType.class, name, i++, ENTITY_CTOR, ImmutableList.of(location.getPath(), Entity.class, -1));
                ((EntityTypeBridge) (Object) entityType).bridge$setup(location, type, entitySpec(location));
                newTypes.add(entityType);
                ArclightMod.LOGGER.debug("Registered {} as entity {}", location, entityType);
            }
            ENTITY_NAME_MAP.put(location.toString(), entityType);
        }
        EnumHelper.addEnums(EntityType.class, newTypes);
        ArclightMod.LOGGER.info("registry.entity-type", newTypes.size());
    }

    private static void loadEnchantments() {
        int origin = Enchantment.values().length;
        int size = ForgeRegistries.ENCHANTMENTS.getEntries().size();
        putBool(Enchantment.class, "acceptingNew", true);
        for (net.minecraft.world.item.enchantment.Enchantment enc : ForgeRegistries.ENCHANTMENTS) {
            try {
                var location = ForgeRegistries.ENCHANTMENTS.getKey(enc);
                String name = ResourceLocationUtil.standardize(location);
                ArclightEnchantment enchantment = new ArclightEnchantment(enc, name);
                Enchantment.registerEnchantment(enchantment);
                ArclightMod.LOGGER.debug("Registered {} as enchantment {}", location, enchantment);
            } catch (Exception e) {
                ArclightMod.LOGGER.error("Failed to register enchantment {}: {}", enc, e);
            }
        }
        Enchantment.stopAcceptingRegistrations();
        ArclightMod.LOGGER.info("registry.enchantment", size - origin);
    }

    private static void loadPotions() {
        int origin = PotionEffectType.values().length;
        int size = ForgeRegistries.MOB_EFFECTS.getEntries().size();
        int maxId = ForgeRegistries.MOB_EFFECTS.getValues().stream().mapToInt(MobEffect::getId).max().orElse(0);
        PotionEffectType[] types = new PotionEffectType[maxId + 1];
        putStatic(PotionEffectType.class, "byId", types);
        putBool(PotionEffectType.class, "acceptingNew", true);
        for (MobEffect eff : ForgeRegistries.MOB_EFFECTS) {
            try {
                var location = ForgeRegistries.MOB_EFFECTS.getKey(eff);
                String name = ResourceLocationUtil.standardize(location);
                ArclightPotionEffect effect = new ArclightPotionEffect(eff, name);
                PotionEffectType.registerPotionEffectType(effect);
                ArclightMod.LOGGER.debug("Registered {} as potion {}", location, effect);
            } catch (Exception e) {
                ArclightMod.LOGGER.error("Failed to register potion type {}: {}", eff, e);
            }
        }
        PotionEffectType.stopAcceptingRegistrations();
        ArclightMod.LOGGER.info("registry.potion", size - origin);
        int typeId = PotionType.values().length;
        List<PotionType> newTypes = new ArrayList<>();
        BiMap<PotionType, String> map = HashBiMap.create(Unsafe.getStatic(CraftPotionUtil.class, "regular"));
        putStatic(CraftPotionUtil.class, "regular", map);
        for (var potion : ForgeRegistries.POTIONS) {
            var location = ForgeRegistries.POTIONS.getKey(potion);
            if (CraftPotionUtil.toBukkit(location.toString()).getType() == PotionType.UNCRAFTABLE && potion != Potions.EMPTY) {
                String name = ResourceLocationUtil.standardize(location);
                MobEffectInstance effectInstance = potion.getEffects().isEmpty() ? null : potion.getEffects().get(0);
                PotionType potionType = EnumHelper.makeEnum(PotionType.class, name, typeId++,
                    Arrays.asList(PotionEffectType.class, boolean.class, boolean.class),
                    Arrays.asList(effectInstance == null ? null : PotionEffectType.getById(MobEffect.getId(effectInstance.getEffect())), false, false));
                newTypes.add(potionType);
                map.put(potionType, location.toString());
                ArclightMod.LOGGER.debug("Registered {} as potion type {}", location, potionType);
            }
        }
        EnumHelper.addEnums(PotionType.class, newTypes);
    }

    private static void loadMaterials() {
        int blocks = 0, items = 0;
        int i = Material.values().length;
        int origin = i;
        List<Material> list = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation location = ForgeRegistries.BLOCKS.getKey(block);
            String name = ResourceLocationUtil.standardize(location);
            Material material = BY_NAME.get(name);
            if (material == null) {
                material = EnumHelper.makeEnum(Material.class, name, i, MAT_CTOR, ImmutableList.of(i));
                ((MaterialBridge) (Object) material).bridge$setupBlock(location, block, matSpec(location));
                BY_NAME.put(name, material);
                i++;
                blocks++;
                ArclightMod.LOGGER.debug("Registered {} as block {}", location, material);
                list.add(material);
            } else {
                ((MaterialBridge) (Object) material).bridge$setupVanillaBlock(matSpec(location));
            }
            BLOCK_MATERIAL.put(block, material);
            MATERIAL_BLOCK.put(material, block);
            Item value = ForgeRegistries.ITEMS.getValue(location);
            if (value != null && value != Items.AIR) {
                ((MaterialBridge) (Object) material).bridge$setItem();
                ITEM_MATERIAL.put(value, material);
                MATERIAL_ITEM.put(material, value);
            }
        }
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
            String name = ResourceLocationUtil.standardize(location);
            Material material = BY_NAME.get(name);
            if (material == null) {
                material = EnumHelper.makeEnum(Material.class, name, i, MAT_CTOR, ImmutableList.of(i));
                ((MaterialBridge) (Object) material).bridge$setupItem(location, item, matSpec(location));
                BY_NAME.put(name, material);
                i++;
                items++;
                ArclightMod.LOGGER.debug("Registered {} as item {}", location, material);
                list.add(material);
            }
            ITEM_MATERIAL.put(item, material);
            MATERIAL_ITEM.put(material, item);
            Block value = ForgeRegistries.BLOCKS.getValue(location);
            if (value != null && value != Blocks.AIR) {
                ((MaterialBridge) (Object) material).bridge$setBlock();
                BLOCK_MATERIAL.put(value, material);
                MATERIAL_BLOCK.put(material, value);
            }
        }
        EnumHelper.addEnums(Material.class, list);
        ArclightMod.LOGGER.info("registry.material", i - origin, blocks, items);
    }

    private static MaterialPropertySpec matSpec(ResourceLocation location) {
        return ArclightConfig.spec().getCompat().getMaterial(location.toString()).orElse(MaterialPropertySpec.EMPTY);
    }

    private static EntityPropertySpec entitySpec(ResourceLocation location) {
        return ArclightConfig.spec().getCompat().getEntity(location.toString()).orElse(EntityPropertySpec.EMPTY);
    }

    public static Pose toBukkitPose(net.minecraft.world.entity.Pose nms) {
        if (Pose.values().length <= nms.ordinal()) {
            var newTypes = new ArrayList<Pose>();
            var forgeCount = net.minecraft.world.entity.Pose.values().length;
            for (var id = Pose.values().length; id < forgeCount; id++) {
                var name = net.minecraft.world.entity.Pose.values()[id].name();
                var newPhase = EnumHelper.makeEnum(Pose.class, name, id, List.of(), List.of());
                newTypes.add(newPhase);
                ArclightMod.LOGGER.debug("Registered {} as pose {}", name, newPhase);
            }
            EnumHelper.addEnums(Pose.class, newTypes);
        }
        return org.bukkit.entity.Pose.values()[nms.ordinal()];
    }

    private static void putStatic(Class<?> cl, String name, Object o) {
        try {
            Unsafe.ensureClassInitialized(cl);
            Field field = cl.getDeclaredField(name);
            Object materialByNameBase = Unsafe.staticFieldBase(field);
            long materialByNameOffset = Unsafe.staticFieldOffset(field);
            Unsafe.putObject(materialByNameBase, materialByNameOffset, o);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void putBool(Class<?> cl, String name, boolean b) {
        try {
            Unsafe.ensureClassInitialized(cl);
            Field field = cl.getDeclaredField(name);
            Object materialByNameBase = Unsafe.staticFieldBase(field);
            long materialByNameOffset = Unsafe.staticFieldOffset(field);
            Unsafe.putBoolean(materialByNameBase, materialByNameOffset, b);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Set<IForgeRegistry<?>> registries() {
        return ImmutableSet.of(ForgeRegistries.BLOCKS, ForgeRegistries.ITEMS,
            ForgeRegistries.MOB_EFFECTS, ForgeRegistries.POTIONS,
            ForgeRegistries.ENTITY_TYPES, ForgeRegistries.BLOCK_ENTITY_TYPES,
            ForgeRegistries.BIOMES);
    }

    public static void unlockRegistries() {
        for (IForgeRegistry<?> registry : registries()) {
            if (registry instanceof ForgeRegistry) {
                ((ForgeRegistry<?>) registry).unfreeze();
            }
        }
    }

    public static void lockRegistries() {
        for (IForgeRegistry<?> registry : registries()) {
            if (registry instanceof ForgeRegistry) {
                ((ForgeRegistry<?>) registry).freeze();
            }
        }
    }
}
