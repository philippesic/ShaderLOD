package com.pp.shaderlod.benchmark;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Authored, deterministic vanilla geometry. No downloaded or personal save is an input. */
final class BenchmarkScene {
    static final String VERSION = "overworld-3-flat";
    static final int Y_OFFSET = -128;
    static final long SEED = 8675309L;
    static final int MIN_X = -64, MAX_X = 63, MIN_Z = -32, MAX_Z = 399;
    record Pose(String name, double x, double y, double z, float yaw, float pitch,
                long time, boolean rain) {
        Pose { y += Y_OFFSET; }
    }
    static final List<Pose> POSES = List.of(
            new Pose("Day: terrain and distant faces", 0.5, 202, -20.5, 0, 4, 6000, false),
            new Pose("Day: dense foliage and cutouts", -40.5, 197, 6.5, -15, 0, 6000, false),
            new Pose("Day: water and reflections", 0.5, 199, 82.5, 0, 9, 6000, false),
            new Pose("Day: underwater", 0.5, 194, 117.5, 0, -3, 6000, false),
            new Pose("Day: material gallery", -43.5, 198, 54.5, 0, 0, 6000, false),
            new Pose("Sunset: water and sky", 0.5, 199, 82.5, 90, -4, 12000, false),
            new Pose("Night: emissive and glass", -43.5, 198, 54.5, 0, 0, 18000, false),
            new Pose("Rain: terrain and foliage", 0.5, 202, -20.5, 0, 4, 6000, true),
            new Pose("Cold biome: snow and ice", 43.5, 199, 102.5, 0, 5, 6000, true));

    private BenchmarkScene() {}

    static List<String> commands() {
        var out = new ArrayList<String>();
        // Bound each fill below the private world's explicit 262144-block limit.
        // Clearing is intentional: an interrupted first construction can be resumed safely.
        for (int z = MIN_Z; z <= MAX_Z; z += 16) {
            // Clear to the Overworld ceiling so seeded natural hills cannot intrude.
            // A 128x128x16 tile is exactly the private command's 262144-block limit.
            for (int y = 190; y <= 319 - Y_OFFSET; y += 128)
                fill(out, MIN_X, y, z, MAX_X, Math.min(y + 127, 319 - Y_OFFSET), z + 15, "air");
            fill(out, MIN_X, 190, z, MAX_X, 191, z + 15, "stone");
            fill(out, MIN_X, 192, z, MAX_X, 192, z + 15, "grass_block");
            biome(out, -64, 188, z, 63, 252, z + 15, "plains");
        }
        // Stepped valley walls leave a continuous central sightline to the >256-block faces.
        for (int z = 160; z < 368; z += 32) {
            int h = 200 + ((z - 160) / 32) * 3;
            fill(out, -64, 193, z, -39, h, z + 31, "stone");
            fill(out, -64, h + 1, z, -39, h + 1, z + 31, "grass_block");
            fill(out, 38, 193, z, 63, h + 3, z + 31, "stone");
            fill(out, 38, h + 4, z, 63, h + 4, z + 31, "grass_block");
        }
        fill(out, -36, 193, 320, 35, 230, 327, "stone_bricks");
        fill(out, -30, 231, 321, 29, 238, 326, "stone");
        for (int x = -30; x <= 30; x += 12)
            fill(out, x, 201, 319, x + 3, 225, 319, "oak_planks");
        // Exposed dirt is a concrete eligible material in the pinned far-work consumers.
        fill(out, -22, 197, 318, -3, 222, 318, "dirt");
        fill(out, 3, 197, 318, 22, 222, 318, "dirt");

        // Pond has real source water, a glass/stone submerged gallery and shore reflections.
        fill(out, -25, 191, 96, 25, 191, 155, "sand");
        fill(out, -25, 192, 96, 25, 197, 155, "water[level=0]");
        fill(out, -26, 192, 95, -26, 197, 156, "stone_bricks");
        fill(out, 26, 192, 95, 26, 197, 156, "stone_bricks");
        fill(out, -25, 192, 95, 25, 197, 95, "stone_bricks");
        fill(out, -25, 192, 156, 25, 197, 156, "stone_bricks");
        fill(out, -7, 192, 143, -4, 195, 145, "prismarine_bricks");
        fill(out, 4, 192, 143, 7, 195, 145, "glass");
        fill(out, -1, 192, 145, 1, 192, 147, "sea_lantern");
        fill(out, -2, 198, 94, 2, 198, 103, "oak_planks");

        // Persistent leaves and fixed vegetation avoid random generation/decay differences.
        for (int z = 24; z <= 304; z += 24) {
            for (int x : new int[]{-52, -34, 34, 52}) {
                if (z >= 96 && z <= 160 && x > 0) continue;
                int ground = z < 160 || (x > -39 && x < 38) ? 192
                        : 200 + ((z - 160) / 32) * 3 + (x > 0 ? 4 : 1);
                tree(out, x, ground, z, (z / 24 + x) % 2 == 0 ? "oak" : "birch");
            }
        }
        for (int z = 12; z <= 76; z += 4) for (int x = -57; x <= -26; x += 5) {
            fill(out, x, 193, z, x, 193, z, (x + z) % 3 == 0 ? "poppy" : "short_grass");
        }

        // Recognizable vanilla material IDs exercise pack-native PBR when a PBR pack is loaded.
        String[] materials = {"stone", "cobblestone", "bricks", "oak_planks", "copper_block",
                "iron_block", "gold_block", "obsidian", "glass", "white_stained_glass",
                "glowstone", "sea_lantern", "redstone_lamp[lit=true]"};
        for (int i = 0; i < materials.length; i++) {
            int x = -62 + (i % 7) * 5, y = 193 + (i / 7) * 6;
            fill(out, x, y, 73, x + 3, y + 4, 76, materials[i]);
        }
        fill(out, -64, 192, 49, -25, 192, 79, "smooth_stone");
        fill(out, -61, 193, 61, -61, 195, 61, "glass");
        fill(out, -25, 193, 72, -18, 202, 72, "dirt");

        // Separate biome column gives native cold precipitation, snow/ice and blue ice.
        biome(out, 28, 188, 96, 63, 252, 175, "snowy_plains");
        fill(out, 28, 193, 96, 63, 193, 175, "snow_block");
        fill(out, 32, 194, 126, 59, 194, 151, "packed_ice");
        fill(out, 36, 195, 147, 40, 207, 151, "blue_ice");
        tree(out, 54, 193, 160, "spruce");
        return List.copyOf(out);
    }

    private static void tree(List<String> out, int x, int ground, int z, String type) {
        fill(out, x - 2, ground + 4, z - 2, x + 2, ground + 6, z + 2,
                type + "_leaves[persistent=true]");
        fill(out, x - 1, ground + 7, z - 1, x + 1, ground + 7, z + 1,
                type + "_leaves[persistent=true]");
        fill(out, x, ground + 1, z, x, ground + 5, z, type + "_log");
    }

    private static void fill(List<String> out, int x, int y, int z, int x2, int y2, int z2, String block) {
        out.add("fill " + x + " " + (y + Y_OFFSET) + " " + z + " " + x2 + " "
                + (y2 + Y_OFFSET) + " " + z2 + " minecraft:" + block);
    }

    private static void biome(List<String> out, int x, int y, int z, int x2, int y2, int z2, String biome) {
        out.add("fillbiome " + x + " " + (y + Y_OFFSET) + " " + z + " " + x2 + " "
                + (y2 + Y_OFFSET) + " " + z2 + " minecraft:" + biome);
    }

    static String recipeHash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (SEED + "\n" + String.join("\n", commands()) + "\n" + POSES).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
