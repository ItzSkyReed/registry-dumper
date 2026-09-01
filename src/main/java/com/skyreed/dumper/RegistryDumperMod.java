package com.skyreed.dumper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Dedicated server initializer for dumping Minecraft registries into structured JSON files.
 */
public class RegistryDumperMod implements DedicatedServerModInitializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Path outputDir = server.getServerDirectory().resolve("dump_output");

            try {
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                System.err.println("[RegistryDumper] Failed to create output directory: " + e.getMessage());
            }


            RegistryAccess access = server.registryAccess();

            dumpBlocks(outputDir.resolve("blocks.json"));
            dumpItems(outputDir.resolve("items.json"));
            dumpEffects(outputDir.resolve("effects.json"));
            dumpTextColors(outputDir.resolve("text_colors.json"));
            dumpDyeColors(outputDir.resolve("dye_colors.json"));
            dumpPotions(outputDir.resolve("potion.json"));
            dumpContainers(outputDir.resolve("containers.json"));

            // Dynamic data registries (loaded via RegistryAccess)
            dumpBannerPatterns(access, outputDir.resolve("banner_patterns.json"));
            dumpEnchantments(access, outputDir.resolve("enchantments.json"));
            dumpTrimPatterns(access, outputDir.resolve("trim_list.json"));
            dumpTrimMaterials(access, outputDir.resolve("trim_material_color.json"));

            System.out.println("[RegistryDumper] All registry files successfully generated!");
            server.halt(false);
        });
    }

    /**
     * Dumps all blocks with their display name.
     *
     * @param path Target destination file path.
     */
    private void dumpBlocks(Path path) {
        JsonObject root = new JsonObject();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            JsonObject blockData = new JsonObject();
            blockData.addProperty("display_name", block.getName().getString());
            root.add(id.getPath(), blockData);
        }
        writeJson(root, path);
    }

    /**
     * Dumps all items with display name, translation key, and stack size.
     *
     * @param path Target destination file path.
     */
    private void dumpItems(Path path) {
        JsonObject root = new JsonObject();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            JsonObject itemData = new JsonObject();
            ItemStack defaultStack = item.getDefaultInstance();

            itemData.addProperty("display_name", defaultStack.getHoverName().getString());
            itemData.addProperty("translation_key", item.getDescriptionId());
            itemData.addProperty("stack_size", defaultStack.getMaxStackSize());

            root.add(id.getPath(), itemData);
        }
        writeJson(root, path);
    }

    /**
     * Dumps status effect identifiers.
     *
     * @param path Target destination file path.
     */
    private void dumpEffects(Path path) {
        JsonArray array = new JsonArray();
        Set<String> sortedEffects = new TreeSet<>();

        for (Identifier id : BuiltInRegistries.MOB_EFFECT.keySet()) {
            sortedEffects.add(id.getPath());
        }
        for (String effect : sortedEffects) {
            array.add(effect);
        }

        writeJson(array, path);
    }

    /**
     * Dumps Minecraft text formatting colors with hex values.
     *
     * @param path Target destination file path.
     */
    private void dumpTextColors(Path path) {
        JsonObject root = new JsonObject();
        for (ChatFormatting format : ChatFormatting.values()) {
            TextColor textColor = TextColor.fromLegacyFormat(format);
            if (textColor != null) {
                String hex = String.format("#%06X", textColor.getValue());
                // Uses standard Java enum name() converted to lowercase
                root.addProperty(format.name().toLowerCase(Locale.ROOT), hex);
            }
        }
        writeJson(root, path);
    }

    /**
     * Dumps dye colors with both standard RGB (6-digit) and ARGB (8-digit) hex formats.
     *
     * @param path Target destination file path.
     */
    private void dumpDyeColors(Path path) {
        JsonObject root = new JsonObject();

        for (DyeColor color : DyeColor.values()) {
            int rawColor = color.getTextureDiffuseColor();

            // 6-digit RGB hex (#RRGGBB)
            String rgb = String.format("#%06X", rawColor & 0x00FFFFFF);

            // 8-digit ARGB hex (#AARRGGBB)
            String argb = String.format("#%08X", rawColor);

            JsonObject colorData = new JsonObject();
            colorData.addProperty("hex", rgb);
            colorData.addProperty("argb", argb);

            root.add(color.name().toLowerCase(Locale.ROOT), colorData);
        }

        writeJson(root, path);
    }

    /**
     * Dumps potions with flags for long and strong variants.
     *
     * @param path Target destination file path.
     */
    private void dumpPotions(Path path) {
        Set<String> allPotionKeys = new TreeSet<>();
        for (Identifier id : BuiltInRegistries.POTION.keySet()) {
            allPotionKeys.add(id.getPath());
        }

        Set<String> basePotions = new TreeSet<>();
        for (String name : allPotionKeys) {
            if (name.startsWith("long_")) {
                basePotions.add(name.substring(5));
            } else if (name.startsWith("strong_")) {
                basePotions.add(name.substring(7));
            } else {
                basePotions.add(name);
            }
        }

        JsonObject root = new JsonObject();
        for (String base : basePotions) {
            JsonObject potData = new JsonObject();
            potData.addProperty("long", allPotionKeys.contains("long_" + base));
            potData.addProperty("strong", allPotionKeys.contains("strong_" + base));
            root.add(base, potData);
        }
        writeJson(root, path);
    }

    /**
     * Dumps all container block IDs dynamically by checking block entity capabilities.
     *
     * @param path Target destination file path.
     */
    private void dumpContainers(Path path) {
        JsonArray array = new JsonArray();
        Set<String> containerIds = new TreeSet<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            if (isContainerBlock(block)) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                containerIds.add(id.getPath());
            }
        }

        for (String name : containerIds) {
            array.add(name);
        }

        writeJson(array, path);
    }

    /**
     * Determines if a block is a container by checking if its BlockEntity implements Container
     * or represents a specialized storage entity.
     *
     * @param block The block instance to check.
     * @return True if the block possesses inventory capabilities; otherwise, false.
     */
    private boolean isContainerBlock(Block block) {
        if (block instanceof EntityBlock entityBlock) {
            try {
                // Instantiate dummy BlockEntity for the block's default state
                BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                if (blockEntity == null) return false;

                // Standard containers (Chest, Trapped Chest, Shulker Boxes, Barrel, Hopper, Dispenser, Crafter, etc.)
                if (blockEntity instanceof Container) {
                    return true;
                }

                // Specialized storage blocks that store items outside the standard Container interface
                return blockEntity instanceof DecoratedPotBlockEntity
                        || blockEntity instanceof ChiseledBookShelfBlockEntity
                        || blockEntity instanceof EnderChestBlockEntity;

            } catch (Exception ignored) {
                // Catches any block requiring custom world context
            }
        }
        return false;
    }

    /**
     * Dumps banner pattern identifiers from the dynamic data registry.
     *
     * @param access Server registry access.
     * @param path Target destination file path.
     */
    private void dumpBannerPatterns(RegistryAccess access, Path path) {
        JsonArray array = new JsonArray();
        var bannerRegistry = access.lookupOrThrow(Registries.BANNER_PATTERN);

        getSortedKeys(path, array, bannerRegistry.keySet());
    }

    private void getSortedKeys(Path path, JsonArray array, Set<Identifier> identifiers) {
        Set<String> sortedKeys = new TreeSet<>();
        for (Identifier id : identifiers) {
            sortedKeys.add(id.getPath());
        }
        for (String key : sortedKeys) {
            array.add(key);
        }

        writeJson(array, path);
    }

    /**
     * Dumps maximum levels for all enchantments.
     *
     * @param access Server registry access.
     * @param path Target destination file path.
     */
    private void dumpEnchantments(RegistryAccess access, Path path) {
        JsonObject root = new JsonObject();
        var enchantmentRegistry = access.lookupOrThrow(Registries.ENCHANTMENT);

        Map<String, Integer> sortedEnchantments = new TreeMap<>();
        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry : enchantmentRegistry.entrySet()) {
            Identifier id = entry.getKey().identifier();
            sortedEnchantments.put(id.getPath(), entry.getValue().getMaxLevel());
        }

        for (Map.Entry<String, Integer> entry : sortedEnchantments.entrySet()) {
            root.addProperty(entry.getKey(), entry.getValue());
        }

        writeJson(root, path);
    }

    /**
     * Dumps trim pattern identifiers.
     *
     * @param access Server registry access.
     * @param path Target destination file path.
     */
    private void dumpTrimPatterns(RegistryAccess access, Path path) {
        JsonArray array = new JsonArray();
        var trimRegistry = access.lookupOrThrow(Registries.TRIM_PATTERN);

        getSortedKeys(path, array, trimRegistry.keySet());
    }

    /**
     * Dumps armor trim materials and their style description colors.
     *
     * @param access Server registry access.
     * @param path Target destination file path.
     */
    private void dumpTrimMaterials(RegistryAccess access, Path path) {
        JsonObject root = new JsonObject();
        var materialRegistry = access.lookupOrThrow(Registries.TRIM_MATERIAL);

        Map<String, String> sortedMaterials = new TreeMap<>();
        for (Map.Entry<ResourceKey<TrimMaterial>, TrimMaterial> entry : materialRegistry.entrySet()) {
            Identifier id = entry.getKey().identifier();
            TrimMaterial material = entry.getValue();

            int colorValue = 0xFFFFFF;
            if (material.description().getStyle().getColor() != null) {
                colorValue = material.description().getStyle().getColor().getValue();
            }
            sortedMaterials.put(id.getPath(), String.format("#%06x", colorValue));
        }

        for (Map.Entry<String, String> entry : sortedMaterials.entrySet()) {
            root.addProperty(entry.getKey(), entry.getValue());
        }

        writeJson(root, path);
    }

    /**
     * Serializes an object to JSON and writes it to disk.
     *
     * @param element JSON payload to write.
     * @param path Target destination file path.
     */
    private void writeJson(Object element, Path path) {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            GSON.toJson(element, writer);
            System.out.println("[RegistryDumper] Generated: " + path.getFileName());
        } catch (IOException e) {
            System.err.println("[RegistryDumper] Error writing " + path.getFileName() + ": " + e.getMessage());
        }
    }
}