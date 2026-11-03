package com.pp.shaderlod.optimization;

import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** The native admission API consumes repository selections, not expanded loaded resources. */
final class EarlyAlphaResourceSelectionTest {
    @Test void selectedNativePacksPassWhileExpandedAndExternalSelectionsFail() {
        var selected=List.of("vanilla","mod_resources");
        // Authentic r8 ResourceManager reload line164; normal mod resources expand selected packs.
        var loaded=List.of("vanilla","mod_resources","mod/sodium",
            "mod/fabric_rendering_data_attachment_v1","mod/iris","mod/shaderlod",
            "mod/fabric_renderer_api_v1","mod/fabric_api_base","mod/neoforge",
            "mod/fabric_block_view_api_v2","mod/shaderlod_dev");
        assertTrue(MakeUpQuadRuntime.resourcesMatch(selected));
        assertFalse(MakeUpQuadRuntime.resourcesMatch(loaded));
        var external=new ArrayList<>(selected);external.add("file/external-overrides.zip");
        assertFalse(MakeUpQuadRuntime.resourcesMatch(external));
        assertFalse(MakeUpQuadRuntime.resourcesMatch(List.of("vanilla")));
    }
}
