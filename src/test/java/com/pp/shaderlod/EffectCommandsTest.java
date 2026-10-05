package com.pp.shaderlod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pp.shaderlod.lod.Effect;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectCommandsTest {
    private CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("shaderlod").then(ShaderLodCommands.effectCommands()));
        return dispatcher;
    }

    @Test void actualBrigadierTreeExposesAllEffectsAndValidatedEditShapes() {
        var dispatcher = dispatcher();
        for (Effect effect : Effect.values()) {
            String root = "shaderlod effects " + ShaderLodEffectConfig.effectKey(effect);
            for (String suffix : new String[]{"", " status", " mode inherit", " mode full", " mode adaptive",
                    " bands 32 96 8", " ratios 1 0.25", " protection true", " protection false"}) {
                var parsed = dispatcher.parse(root + suffix, null);
                assertFalse(parsed.getReader().canRead(), root + suffix);
                assertNotNull(parsed.getContext().getCommand(), root + suffix);
            }
        }
    }

    @Test void rangeAndShapeErrorsCannotBecomeExecutableRequests() {
        var dispatcher = dispatcher();
        String root = "shaderlod effects opaque_lighting";
        for (String command : new String[]{root + " mode fast", root + " bands 0 96 8",
                root + " bands 32 4097 8", root + " bands 32 96 -1", root + " ratios 1.1 .25",
                root + " ratios 1 NaN", root + " protection yes", "shaderlod effects unknown status"}) {
            var parsed = dispatcher.parse(command, null);
            assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand() == null, command);
        }
    }

    @Test void diagnosticsDescribeRequestedSettingsWithoutClaimingConsumption() {
        String status = ShaderLodCommands.describeEffect(Effect.OPAQUE_LIGHTING);
        assertTrue(status.contains("requestedMode="));
        assertTrue(status.contains("requestedRatios="));
        assertTrue(status.contains("transportRatios="));
        assertTrue(status.contains("shader consumption unverified"));
        assertFalse(status.contains("shader consumed"));
        assertTrue(ShaderLodCommands.describeEffects().contains("opaque_lighting:"));
    }
}
