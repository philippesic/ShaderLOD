package com.pp.shaderlod.optimization.framecopy;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Canonical recipe checks only. Generated resources remain the root integrator's responsibility. */
public class MakeUpDeadCopyRecipeTest {
    @Test void macScalarRouteAndLegacyLateFlipsKeepDefaultOffDeclaration() throws Exception {
        var recipe = JsonParser.parseString(Files.readString(Path.of("patches/adapters/makeup-ultra-fast-shaders.json"))).getAsJsonObject();
        assertEquals("9.5g", recipe.get("version").getAsString());
        assertEquals("pgb0JjoN", recipe.get("version_id").getAsString());
        assertEquals("LGPL-3.0-or-later", recipe.getAsJsonObject("license").get("id").getAsString());
        var key = "SHADERLOD_MAKEUP_DEAD_FRAME_COPY";
        var control = recipe.getAsJsonObject("optimization_compile_controls").getAsJsonObject("dead_frame_copy");
        assertEquals(key, control.get("option").getAsString());
        assertEquals("0", control.get("disabled").getAsString()); assertEquals("1", control.get("enabled").getAsString());
        assertFalse(control.get("default_enabled").getAsBoolean()); assertTrue(control.get("reload_required").getAsBoolean());
        List<String> declarations = new ArrayList<>(); String properties = null;
        for (var item : recipe.getAsJsonArray("patches")) {
            var patch = item.getAsJsonObject(); String after = patch.get("after").getAsString();
            if (after.contains("#define " + key)) declarations.add(patch.get("path").getAsString());
            if (patch.get("path").getAsString().equals("shaders/shaders.properties")) properties = after;
        }
        assertFalse(declarations.isEmpty());
        assertEquals(java.util.Set.of("shaders/lib/config.glsl"),java.util.Set.copyOf(declarations));
        assertNotNull(properties);
        String guard = "#if defined SHADERLOD_FRAMECOPY_RUNTIME_GUARD && " + key + " == 1\nflip.composite2.colortex0=true\nflip.composite2.colortex7=true\n#endif\n";
        assertTrue(properties.endsWith(guard+"#endif\n"));
        assertEquals(6, properties.lines().filter(line -> line.startsWith("flip.")).count());
        assertTrue(properties.contains("size.buffer.colortex8=1 1"));
        assertTrue(properties.contains("flip.composite3.colortex4=true"));
        assertFalse(properties.contains(".colortex3=true"));
        assertFalse(properties.contains(".colortex1=true"));
        assertFalse(control.get("runtime_adoption").getAsBoolean());
    }
}
