package com.pp.shaderlod.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSceneTest {
    @Test void authoredRecipeFitsLoadedRegionAndCommandLimit() {
        assertFalse(BenchmarkScene.commands().isEmpty());
        for (String command : BenchmarkScene.commands()) {
            String[] fields = command.split(" ");
            assertTrue(fields[0].equals("fill") || fields[0].equals("fillbiome"));
            int x = Integer.parseInt(fields[1]), y = Integer.parseInt(fields[2]), z = Integer.parseInt(fields[3]);
            int x2 = Integer.parseInt(fields[4]), y2 = Integer.parseInt(fields[5]), z2 = Integer.parseInt(fields[6]);
            assertTrue(x >= BenchmarkScene.MIN_X && x2 <= BenchmarkScene.MAX_X, command);
            assertTrue(z >= BenchmarkScene.MIN_Z && z2 <= BenchmarkScene.MAX_Z, command);
            assertTrue(y >= 60 && y2 <= 319, command);
            assertTrue(x <= x2 && y <= y2 && z <= z2, command);
            assertTrue((long)(x2 - x + 1) * (y2 - y + 1) * (z2 - z + 1) <= 262144, command);
        }
    }

    @Test void sceneHasStableRecipeAndRequiredNativeWorkloads() {
        assertEquals(BenchmarkScene.commands(), BenchmarkScene.commands());
        assertEquals(64, BenchmarkScene.recipeHash().length());
        assertEquals(BenchmarkScene.recipeHash(), BenchmarkScene.recipeHash());
        var commands = String.join("\n", BenchmarkScene.commands());
        assertTrue(commands.contains("minecraft:water[level=0]"));
        assertTrue(commands.contains("leaves[persistent=true]"));
        assertTrue(commands.contains("minecraft:glass"));
        assertTrue(commands.contains("minecraft:sea_lantern"));
        assertTrue(commands.contains("minecraft:snowy_plains"));
        assertTrue(commands.contains("320 35 102 327 minecraft:stone_bricks"));
        assertTrue(commands.contains("fill -22 69 318 -3 94 318 minecraft:dirt"));
        assertTrue(commands.contains("fill -25 65 72 -18 74 72 minecraft:dirt"));
        assertTrue(320 - BenchmarkScene.POSES.getFirst().z() > 256);
        assertEquals(9, BenchmarkScene.POSES.size());
        assertTrue(BenchmarkScene.POSES.stream().anyMatch(p -> p.time() == 18000));
        assertTrue(BenchmarkScene.POSES.stream().anyMatch(BenchmarkScene.Pose::rain));
    }

    @Test void loweredSceneClearsEveryLaneColumnToTheBuildCeiling() {
        assertEquals("overworld-3-flat", BenchmarkScene.VERSION);
        assertEquals(-128, BenchmarkScene.Y_OFFSET);
        assertEquals(74.0, BenchmarkScene.POSES.getFirst().y());
        assertTrue(BenchmarkScene.POSES.stream().allMatch(p -> p.y() >= 66 && p.y() <= 74));
        var commands = BenchmarkScene.commands();
        for (int z = BenchmarkScene.MIN_Z; z <= BenchmarkScene.MAX_Z; z += 16) {
            assertTrue(commands.contains("fill -64 62 " + z + " 63 189 " + (z + 15) + " minecraft:air"));
            assertTrue(commands.contains("fill -64 190 " + z + " 63 317 " + (z + 15) + " minecraft:air"));
            assertTrue(commands.contains("fill -64 318 " + z + " 63 319 " + (z + 15) + " minecraft:air"));
        }
    }
}
