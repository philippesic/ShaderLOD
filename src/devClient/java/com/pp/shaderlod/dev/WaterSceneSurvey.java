package com.pp.shaderlod.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One opt-in terminal survey of already loaded client cells; never generates or moves anything. */
public final class WaterSceneSurvey {
    private static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.waterSceneSurvey");
    private static final int[] XS = {240, 300, 400, 500, 650};
    private static final int[] ZS = {640, 660, 680};
    private static final int MIN_Y = 40, MAX_Y = 80;
    private static final int READ_LIMIT = 4096;
    private static JsonObject terminalSnapshot;

    private WaterSceneSurvey() {}

    /** First call freezes the snapshot for this JVM; call after measurement with the owned world still open. */
    public static JsonObject captureTerminalSnapshot(Minecraft client) {
        if (terminalSnapshot != null) return terminalSnapshot.deepCopy();
        JsonObject result = new JsonObject();
        terminalSnapshot = result;
        result.addProperty("schema", 1);
        result.addProperty("opt_in", ENABLED);
        result.addProperty("captured_utc", Instant.now().toString());
        result.addProperty("coordinate_units", "Minecraft world block units; integer cells, camera position as actual doubles; angles in degrees");
        result.addProperty("candidate_column_cells", XS.length * ZS.length * (MAX_Y - MIN_Y + 1));
        result.addProperty("column_min_y_inclusive", MIN_Y);
        result.addProperty("column_max_y_inclusive", MAX_Y);
        result.addProperty("direct_block_state_read_limit", READ_LIMIT);
        result.addProperty("camera_fog_required_position_limit", 12);
        result.addProperty("camera_fog_internal_block_fluid_read_upper_bound", 17);
        result.addProperty("camera_fog_bound_basis", "Pinned Minecraft 1.21.1 Camera and vanilla water/lava height implementations; replacements by other mods are unverified");
        result.addProperty("total_world_state_read_upper_bound", READ_LIMIT + 17);
        result.addProperty("scope", "615 fixed candidate column cells plus <=12 camera/fog positions and bounded neighbor context; no heightmap, chunk generation, biome, ray visibility or glint proof");
        if (!ENABLED || client == null || client.level == null) {
            result.add("survey", unknown(!ENABLED ? "shaderlod.dev.waterSceneSurvey not enabled" : "owned client world unavailable"));
            return result.deepCopy();
        }
        ClientLevel level = client.level;
        ReadView view = new ReadView(level);
        result.addProperty("dimension", level.dimension().location().toString());
        result.addProperty("game_time_ticks", level.getGameTime());
        result.addProperty("day_time_ticks", level.getDayTime());
        result.addProperty("day_time_mod_24000", Math.floorMod(level.getDayTime(), 24000L));
        result.addProperty("has_sky_light", level.dimensionType().hasSkyLight());
        result.addProperty("has_ceiling", level.dimensionType().hasCeiling());
        result.addProperty("weather_interpolation_partial_tick", 0.0F);
        result.addProperty("rain_level", level.getRainLevel(0.0F));
        result.addProperty("thunder_level", level.getThunderLevel(0.0F));
        result.addProperty("is_raining", level.isRaining());
        result.addProperty("is_thundering", level.isThundering());
        try { result.add("camera", camera(client, view)); }
        catch (RuntimeException exception) { result.add("camera", unknown(exception.toString())); }
        JsonArray columns = new JsonArray();
        for (int x : XS) for (int z : ZS) columns.add(column(view, x, z));
        result.add("columns", columns);
        result.addProperty("actual_direct_block_state_reads", view.reads);
        result.addProperty("actual_unique_cached_positions", view.states.size());
        result.addProperty("direct_read_budget_exhausted", view.reads >= READ_LIMIT);
        result.addProperty("read_count_scope", "Counts cached ReadView misses only; Camera.getFluidInCamera internal duplicate lookups are not instrumented and have the separate pinned-source upper bound");
        return result.deepCopy();
    }

    private static JsonObject camera(Minecraft client, ReadView view) {
        var camera = client.gameRenderer.getMainCamera();
        JsonObject result = new JsonObject();
        Vec3 position = camera.getPosition();
        result.add("position", coordinates(position));
        result.addProperty("yaw_degrees", camera.getYRot());
        result.addProperty("pitch_degrees", camera.getXRot());
        result.addProperty("initialized", camera.isInitialized());
        result.addProperty("detached", camera.isDetached());
        result.addProperty("configured_camera_type", client.options.getCameraType().name());
        result.addProperty("configured_first_person", client.options.getCameraType().isFirstPerson());
        BlockPos eye = camera.getBlockPosition().immutable();
        result.add("position_cell", cell(view, eye));
        if (!camera.isInitialized() || camera.getEntity() == null || camera.getEntity().level() != view.level) {
            result.add("fog_type", unknown("camera not initialized or current camera-entity level not the surveyed client level"));
            return result;
        }
        var near = camera.getNearPlane();
        Set<BlockPos> required = new LinkedHashSet<>();
        required.add(eye);
        for (Vec3 offset : new Vec3[]{near.getPointOnPlane(0.0F, 0.0F), near.getTopLeft(), near.getTopRight(), near.getBottomLeft(), near.getBottomRight()}) {
            required.add(BlockPos.containing(position.add(offset)));
        }
        for (BlockPos pos : Set.copyOf(required)) required.add(pos.above());
        JsonArray guards = new JsonArray();
        boolean known = true;
        for (BlockPos pos : required) {
            JsonObject guard = view.guard(pos);
            guard.add("cell", coordinates(pos));
            try { view.getBlockState(pos); }
            catch (RuntimeException exception) { known = false; guard.add("state", unknown(exception.toString())); }
            guards.add(guard);
        }
        result.add("fog_required_position_guards", guards);
        result.addProperty("fog_required_unique_positions", required.size());
        if (!known) result.add("fog_type", unknown("not all eye/near-plane/above positions are loaded and within the read budget"));
        else {
            result.addProperty("fog_type", camera.getFluidInCamera().name());
            result.addProperty("fog_type_source", "Actual Camera.getFluidInCamera after all pinned-source lookup positions were guarded/preloaded");
        }
        return result;
    }

    private static JsonObject column(ReadView view, int x, int z) {
        JsonObject result = new JsonObject();
        result.addProperty("x", x);
        result.addProperty("z", z);
        result.add("loaded_guard", view.guard(new BlockPos(x, MIN_Y, z)));
        JsonArray cells = new JsonArray();
        int observed = 0, water = 0, fluid = 0;
        double highestObservedWaterTop = Double.NEGATIVE_INFINITY;
        for (int y = MIN_Y; y <= MAX_Y; y++) {
            JsonObject cell = cell(view, new BlockPos(x, y, z));
            cells.add(cell);
            if (!cell.has("water")) continue;
            observed++;
            if (!cell.get("fluid_empty").getAsBoolean()) fluid++;
            if (cell.get("water").getAsBoolean()) {
                water++;
                if (cell.has("fluid_height_in_cell") && cell.get("fluid_height_in_cell").isJsonPrimitive()) {
                    highestObservedWaterTop = Math.max(highestObservedWaterTop, y + cell.get("fluid_height_in_cell").getAsDouble());
                }
            }
        }
        result.add("cells", cells);
        result.addProperty("observed_cells", observed);
        result.addProperty("sample_range_complete", observed == MAX_Y - MIN_Y + 1);
        result.addProperty("observed_water_cells", water);
        result.addProperty("observed_nonempty_fluid_cells", fluid);
        if (Double.isFinite(highestObservedWaterTop)) result.addProperty("highest_observed_water_cell_top_y", highestObservedWaterTop);
        else result.add("highest_observed_water_cell_top_y", unknown("no water height observed in this sampled range"));
        result.addProperty("surface_height_scope", "Maximum observed fluid-cell top in y40..80 only; not a whole-column surface, continuous water depth or glint guarantee");
        return result;
    }

    private static JsonObject cell(ReadView view, BlockPos pos) {
        JsonObject result = new JsonObject();
        result.add("cell", coordinates(pos));
        result.add("loaded_guard", view.guard(pos));
        BlockState state;
        try { state = view.getBlockState(pos); }
        catch (RuntimeException exception) { result.add("state", unknown(exception.toString())); return result; }
        FluidState fluid = state.getFluidState();
        result.addProperty("status", "observed_loaded_cell");
        result.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        result.addProperty("air", state.isAir());
        result.addProperty("can_occlude", state.canOcclude());
        result.addProperty("fluid", BuiltInRegistries.FLUID.getKey(fluid.getType()).toString());
        result.addProperty("fluid_empty", fluid.isEmpty());
        result.addProperty("water", fluid.is(FluidTags.WATER));
        result.addProperty("fluid_source", fluid.isSource());
        result.addProperty("fluid_amount", fluid.getAmount());
        try { result.addProperty("fluid_height_in_cell", fluid.getHeight(view, pos)); }
        catch (RuntimeException exception) { result.add("fluid_height_in_cell", unknown(exception.toString())); }
        try { result.addProperty("solid_render", state.isSolidRender(view, pos)); }
        catch (RuntimeException exception) { result.add("solid_render", unknown(exception.toString())); }
        try { result.addProperty("collision_shape_empty", state.getCollisionShape(view, pos).isEmpty()); }
        catch (RuntimeException exception) { result.add("collision_shape_empty", unknown(exception.toString())); }
        return result;
    }

    private static JsonObject coordinates(Vec3 pos) {
        JsonObject result = new JsonObject();
        result.addProperty("x", pos.x); result.addProperty("y", pos.y); result.addProperty("z", pos.z);
        return result;
    }

    private static JsonObject coordinates(BlockPos pos) {
        JsonObject result = new JsonObject();
        result.addProperty("x", pos.getX()); result.addProperty("y", pos.getY()); result.addProperty("z", pos.getZ());
        return result;
    }

    private static JsonObject unknown(String reason) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "unknown"); result.addProperty("reason", reason);
        return result;
    }

    /** All contextual neighbor reads are loaded-only and share the same bounded state cache. */
    private static final class ReadView implements BlockGetter {
        private final ClientLevel level;
        private final Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        private int reads;

        private ReadView(ClientLevel level) { this.level = level; }

        private JsonObject guard(BlockPos pos) {
            JsonObject result = new JsonObject();
            result.addProperty("level_has_chunk_at", level.hasChunkAt(pos));
            result.addProperty("client_chunk_cache_has_chunk", level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4));
            result.addProperty("in_dimension_build_range", pos.getY() >= getMinBuildHeight() && pos.getY() < getMinBuildHeight() + getHeight());
            return result;
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            // ClientLevel.hasChunk is always true in 1.21.1; the cache check is essential.
            if (!level.hasChunkAt(pos) || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                throw new IllegalStateException("unknown: client chunk not loaded at " + pos);
            }
            if (pos.getY() < getMinBuildHeight() || pos.getY() >= getMinBuildHeight() + getHeight()) {
                throw new IllegalStateException("unknown: cell outside dimension build range at " + pos);
            }
            BlockState existing = states.get(pos);
            if (existing != null) return existing;
            if (reads >= READ_LIMIT) throw new IllegalStateException("unknown: direct state read budget exhausted at " + pos);
            reads++;
            BlockState state = level.getBlockState(pos);
            states.put(pos.immutable(), state);
            return state;
        }

        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            throw new IllegalStateException("unknown: block-entity-dependent context deliberately not queried at " + pos);
        }
        @Override public int getHeight() { return level.dimensionType().height(); }
        @Override public int getMinBuildHeight() { return level.dimensionType().minY(); }
    }
}
