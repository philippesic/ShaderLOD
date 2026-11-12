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

  record Motion(boolean flying, boolean noGravity, java.util.List<Double> velocity) {}

  private static Motion motion(net.minecraft.world.entity.player.Player player) {
    var velocity = player.getDeltaMovement();
    return new Motion(
        player.getAbilities().flying,
        player.isNoGravity(),
        java.util.List.of(velocity.x, velocity.y, velocity.z));
  }

  private static void applyMotion(net.minecraft.world.entity.player.Player player, Motion motion) {
    player.getAbilities().flying = motion.flying();
    player.setNoGravity(motion.noGravity());
    var velocity = motion.velocity();
    player.setDeltaMovement(velocity.get(0), velocity.get(1), velocity.get(2));
  }

  void applyClient(Map<String, Object> change) {
    if (change.get("player_motion") instanceof Motion motion) {
      applyMotion(Minecraft.getInstance().player, motion);
    }
  }

  private record Snapshot(
      String savePath,
      long seed,
      String dimension,
      String playerId,
      java.util.List<Double> playerPose,
      Motion clientMotion,
      Motion serverMotion,
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
    if (server == null || dimension == null || client.player == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Owned integrated world required"));
    }
    var clientPlayer = client.player;
    if (!restore && !clientPlayer.mayFly()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Prepared creative flight required"));
    }
    var clientMotion = motion(clientPlayer);
    String playerId = clientPlayer.getUUID().toString();
    var playerPose =
        java.util.List.of(
            clientPlayer.getX(),
            clientPlayer.getY(),
            clientPlayer.getZ(),
            (double) clientPlayer.getYRot(),
            (double) clientPlayer.getXRot());
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
            var serverPlayer = server.getPlayerList().getPlayer(clientPlayer.getUUID());
            if (serverPlayer == null || (!restore && !serverPlayer.mayFly())) {
              throw new IllegalStateException("Prepared integrated creative player required");
            }
            if (original == null && Files.exists(journalPath)) {
              journal = Files.readAllBytes(journalPath);
              original =
                  new Gson().fromJson(new String(journal, StandardCharsets.UTF_8), Snapshot.class);
              if (original == null
                  || original.savePath() == null
                  || original.playerId() == null
                  || original.clientMotion() == null
                  || original.serverMotion() == null
                  || original.playerPose() == null
                  || original.playerPose().size() != 5
                  || !original.playerId().equals(playerId)
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
                      playerId,
                      playerPose,
                      clientMotion,
                      motion(serverPlayer),
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
            var preparedMotion = new Motion(true, true, java.util.List.of(0.0, 0.0, 0.0));
            var restoredClientMotion = restore ? original.clientMotion() : preparedMotion;
            applyMotion(serverPlayer, restore ? original.serverMotion() : preparedMotion);
            serverPlayer.onUpdateAbilities();
            if (restore) {
              var player =
                  server.getPlayerList().getPlayer(java.util.UUID.fromString(original.playerId()));
              if (player == null) {
                throw new IllegalStateException("Original benchmark player is unavailable");
              }
              var pose = original.playerPose();
              player.teleportTo(
                  world,
                  pose.get(0),
                  pose.get(1),
                  pose.get(2),
                  java.util.Set.of(),
                  pose.get(3).floatValue(),
                  pose.get(4).floatValue());
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
                "player_motion",
                restoredClientMotion,
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
