package com.pp.shaderlod.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedCompilationInputs;
import java.util.Map;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.FileNode;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.option.BooleanOption;
import net.irisshaders.iris.shaderpack.option.OptionLocation;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.OptionType;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.option.StringOption;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import org.junit.jupiter.api.Test;

/** Actual pinned Iris graph/options classes, without enabling or emulating a runtime certificate. */
class ReimaginedCompilationInputsTest {
    private static final AbsolutePackPath PATH = AbsolutePackPath.fromAbsolutePath("/settings.glsl");

    private static IncludeGraph graph(String... lines) throws Exception {
        var constructor = IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class, ImmutableMap.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                ImmutableMap.of(PATH, new FileNode(PATH, ImmutableList.copyOf(lines))), ImmutableMap.of());
    }

    @Test
    void capturesCompleteTypedValuesIncludingDefaultsAndIgnoresUndeclaredOverrides() throws Exception {
        var location = new OptionLocation(PATH, 0);
        var builder = OptionSet.builder();
        builder.addBooleanOption(location, new BooleanOption(OptionType.DEFINE, "DEFAULT_TRUE", "", true));
        builder.addBooleanOption(location, new BooleanOption(OptionType.DEFINE, "DEFAULT_FALSE", "", false));
        builder.addBooleanOption(location, new BooleanOption(OptionType.DEFINE, "OVERRIDDEN", "", false));
        builder.addStringOption(location, StringOption.create(OptionType.DEFINE, "QUALITY", "[1 2 3]", "2"));
        builder.addStringOption(location, StringOption.create(OptionType.DEFINE, "MODE", "[native other]", "native"));
        var declarations = builder.build();
        var values = new MutableOptionValues(declarations,
                Map.of("OVERRIDDEN", "true", "QUALITY", "3", "UNDECLARED", "foreign"));
        var snapshot = ReimaginedCompilationInputs.capture(graph("// fixture"), declarations, values);

        assertEquals(Map.of("DEFAULT_TRUE", true, "DEFAULT_FALSE", false, "OVERRIDDEN", true),
                snapshot.booleanOptions());
        assertEquals(Map.of("QUALITY", "3", "MODE", "native"), snapshot.stringOptions());
        values.addAll(Map.of("OVERRIDDEN", "false", "QUALITY", "1"));
        assertTrue(snapshot.booleanOptions().get("OVERRIDDEN"));
        assertEquals("3", snapshot.stringOptions().get("QUALITY"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.booleanOptions().put("NEW", true));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.stringOptions().put("NEW", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> ReimaginedCompilationInputs.capture(snapshot.rawGraph(), OptionSet.builder().build(), values));
    }

    @Test
    void retainsActualConstructorRawGraphSeparatelyFromItsRewrittenGraph() throws Exception {
        var raw = graph("#define QUALITY 2 // [1 2 3]", "int quality = QUALITY;");
        var options = new ShaderPackOptions(raw, Map.of("QUALITY", "3"));
        var snapshot = ReimaginedCompilationInputs.capture(raw, options.getOptionSet(), options.getOptionValues());

        assertSame(raw, snapshot.rawGraph());
        assertNotSame(raw, options.getIncludes());
        assertEquals("#define QUALITY 2 // [1 2 3]", raw.getNodes().get(PATH).getLines().getFirst());
        assertNotEquals(raw.getNodes().get(PATH).getLines(),
                options.getIncludes().getNodes().get(PATH).getLines());
        assertEquals("3", snapshot.stringOptions().get("QUALITY"));
        ((MutableOptionValues) options.getOptionValues()).addAll(Map.of("QUALITY", "1"));
        assertEquals("3", snapshot.stringOptions().get("QUALITY"));
        assertEquals("1", options.getOptionValues().getStringValueOrDefault("QUALITY"));
        assertTrue(snapshot.rawGraph().getFailures().isEmpty());
    }
}
