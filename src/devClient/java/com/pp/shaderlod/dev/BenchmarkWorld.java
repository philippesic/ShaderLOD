package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.pp.shaderlod.storage.AtomicFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameRules;

final class BenchmarkWorld {
  private Snapshot original;
  private byte[] journal;
  private int preimages;
  private final Path directory;
  private final Path journalPath;

  private record Snapshot(
      String savePath,
      long seed,
      String dimension,
      long dayTime,
      boolean rain,
      boolean thunder,
      int clearTime,
      int rainTime,
      int thunderTime,
      boolean daylight,
      boolean weather,
      boolean mobs,
      int randomTicks) {}

  BenchmarkWorld(Path directory) {
    this.directory = directory;
    journalPath =
        net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("shaderlod-benchmark-world.json");
  }

  CompletableFuture<Map<String, Object>> set(boolean restore) {
    return set(restore, 6000, false, false);
  }

  CompletableFuture<Map<String, Object>> set(
      boolean restore, int dayTime, boolean raining, boolean thundering) {
    var client = Minecraft.getInstance();
    var server = client.getSingleplayerServer();
    var dimension = client.level == null ? null : client.level.dimension();
    if (server == null || dimension == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Owned integrated world required"));
    }
    return CompletableFuture.supplyAsync(
        () -> {
          var world = server.getLevel(dimension);
          if (world == null) {
            throw new IllegalStateException("Integrated dimension unavailable");
          }
          var rules = world.getGameRules();
          var data = (net.minecraft.world.level.storage.ServerLevelData) world.getLevelData();
          try {
            var save =
                server
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath()
                    .normalize();
            if (original == null && Files.exists(journalPath)) {
              journal = Files.readAllBytes(journalPath);
              original =
                  new Gson().fromJson(new String(journal, StandardCharsets.UTF_8), Snapshot.class);
              if (original == null
                  || original.savePath() == null
                  || !Path.of(original.savePath()).toAbsolutePath().normalize().equals(save)
                  || original.seed() != world.getSeed()
                  || !dimension.location().toString().equals(original.dimension())) {
                throw new IllegalStateException("World recovery identity differs");
              }
              if (!restore) {
                throw new IllegalStateException(
                    "Interrupted world journal requires restore before preparing another run");
              }
            }
            if (original == null && restore) {
              return Map.of("restored", true, "mutated", false);
            }
            if (original == null) {
              original =
                  new Snapshot(
                      save.toString(),
                      world.getSeed(),
                      dimension.location().toString(),
                      world.getDayTime(),
                      world.isRaining(),
                      world.isThundering(),
                      data.getClearWeatherTime(),
                      data.getRainTime(),
                      data.getThunderTime(),
                      rules.getBoolean(GameRules.RULE_DAYLIGHT),
                      rules.getBoolean(GameRules.RULE_WEATHER_CYCLE),
                      rules.getBoolean(GameRules.RULE_DOMOBSPAWNING),
                      rules.getInt(GameRules.RULE_RANDOMTICKING));
              journal = new Gson().toJson(original).getBytes(StandardCharsets.UTF_8);
              AtomicFiles.publish(journalPath, journal);
              preimages++;
              AtomicFiles.publish(
                  directory.resolve("world-preimage-" + preimages + ".json"), journal);
            }
            rules.getRule(GameRules.RULE_DAYLIGHT).set(restore && original.daylight(), server);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(restore && original.weather(), server);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(restore && original.mobs(), server);
            rules
                .getRule(GameRules.RULE_RANDOMTICKING)
                .set(restore ? original.randomTicks() : 0, server);
            world.setDayTime(restore ? original.dayTime() : dayTime);
            world.setWeatherParameters(
                restore ? original.clearTime() : (raining ? 0 : 6000),
                restore ? original.rainTime() : 6000,
                restore ? original.rain() : raining,
                restore ? original.thunder() : thundering);
            if (restore) {
              data.setThunderTime(original.thunderTime());
              AtomicFiles.removeIfUnchanged(journalPath, journal);
              original = null;
            }
            return Map.of(
                "restored",
                restore,
                "day_time",
                world.getDayTime(),
                "rain",
                world.isRaining(),
                "thunder",
                world.isThundering(),
                "dimension",
                dimension.location().toString());
          } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
          }
        },
        server);
  }
}
