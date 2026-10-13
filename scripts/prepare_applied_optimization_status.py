#!/usr/bin/env python3
"""Prepare an exact-preimage NeoForge 26.2 settings overlay without editing sealed sources.

The generated patch is source-only. Standalone verification uses existing local JARs,
never Gradle, Minecraft, a GPU, a remote host, or a native Goal.
"""
from __future__ import annotations
import argparse
import difflib
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PINS = {
'src/main/java/com/pp/shaderlod/packs/PackSelectionService.java': '5bba0e8cd70ed3808949ed3da5e82ab24959ce203722b573d26eb2f465c3aee5',
'src/main/java/com/pp/shaderlod/CoreUniformFrame.java': '9cc908c0f38f9d81f74f0c4dfc1d9b61ec40832040638cff25c3d1f874e04a5c',
'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/mixin/CommonUniformsMixin.java': 'ab1ba3799c02b5f3cf670dc24bd58a15e0920e4fe04371015911bdc19aee7190',
'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/ShaderLodSettings.java': '8a57575afbdc065567e498f810295865d7e52bbb6d623055155999d689cce5be',
'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/ShaderLodSettingsScreen.java': '607150970abc8b1aa7f0b7875b694c286df28dbad6b9138d416cdfe2b25b20d6',
}

SERVICE_ADDITION = '''    /** Source classification is separate from requested settings and renderer eligibility. */
    public record Selection(String adapterId, boolean shaderGraph, String budgetOption, String failure) {}
    public enum Optimization { ORIGINAL, SHADER, RENDERER, INACTIVE, UNAVAILABLE, FAILED }
    public record AppliedStatus(Optimization optimization, String detail) {}

    /** Read-only exact source inspection, called when opening settings, never in a frame callback. */
    public static Selection inspectSelection(Path directory, String loadedName) {
        try {
            Path dir = directory.toAbsolutePath().normalize();
            var members = archive(child(dir, loadedName));
            var recognized = SupportedPackCatalog.recognize(hashes(members));
            if (recognized.isEmpty()) return new Selection(null, false, null, null);
            String id = recognized.get().pack().id();
            if (!RECIPES.contains(id)) return new Selection(null, false, null, null);
            Recipe recipe = recipe(id);
            boolean shaderGraph = recognized.get().stage() == SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER;
            if (members.containsKey(MARKER)) {
                var marker = JsonParser.parseString(text(members.get(MARKER))).getAsJsonObject();
                State state = readState(child(dir, string(marker, "stateFile")));
                if (!loadedName.equals(state.generatedFile)) throw new IOException("Generated selection identity mismatch");
                verifyState(dir, state);
                verifyRecipeState(recipe, state);
                if (!shaderGraph) throw new IOException("Generated shader source is not recognized");
            }
            return new Selection(id, shaderGraph, id.equals("photon")
                    ? "SHADERLOD_LIVE_PIXEL_BUDGET" : recipe.optionKey(), null);
        } catch (IOException | RuntimeException failure) {
            return new Selection(null, false, null, failure.getMessage());
        }
    }

    /** Describe only the loaded selection, live renderer certificate and current bridge frame.
     * SHADER means controls are applied to a linked shader; it is not a sample-count or FPS claim.
     */
    public static AppliedStatus appliedStatus(Selection selection, Map<String, String> loadedOptions,
            boolean pipelineReady, boolean fallback, boolean enabled, LodPreset preset,
            boolean rendererEnabled, com.pp.shaderlod.CoreUniformFrame.Core bridge) {
        if (fallback) return new AppliedStatus(Optimization.FAILED, "Shader reload failed; fallback active");
        if (!pipelineReady) return new AppliedStatus(Optimization.UNAVAILABLE, "No shader pipeline active");
        if (selection.failure() != null)
            return new AppliedStatus(Optimization.UNAVAILABLE, "Optimization status unavailable: " + selection.failure());
        if (selection.adapterId() == null)
            return new AppliedStatus(Optimization.UNAVAILABLE, !enabled
                    ? "ShaderLOD Off; no verified optimization applied" : "No verified ShaderLOD optimization applied");
        if (!selection.shaderGraph()) {
            if (bridge != null || loadedOptions.containsKey(selection.budgetOption()))
                return new AppliedStatus(Optimization.UNAVAILABLE, "Loaded shader differs from inspected source; reopen settings");
            if (!enabled || preset == LodPreset.QUALITY)
                return new AppliedStatus(Optimization.ORIGINAL,
                        "Original shader active (" + (!enabled ? "Off" : "Quality") + ")");
            if (rendererEnabled && preset == LodPreset.PERFORMANCE)
                return new AppliedStatus(Optimization.RENDERER, "Renderer optimization applied (native shader)");
            return new AppliedStatus(Optimization.ORIGINAL, preset == LodPreset.PERFORMANCE
                    ? "Original shader active (renderer unavailable)"
                    : "Original shader active (shader optimization not applied)");
        }
        String selectedBudget = loadedOptions.get(selection.budgetOption());
        boolean budgetEnabled = selection.adapterId().equals("photon")
                ? "true".equals(selectedBudget) && "true".equals(loadedOptions.get("TAA"))
                    && "true".equals(loadedOptions.get("TAAU"))
                : "1".equals(selectedBudget);
        if (!budgetEnabled)
            return new AppliedStatus(Optimization.INACTIVE, "Shader optimization disabled in shader options");
        if (bridge == null)
            return new AppliedStatus(Optimization.UNAVAILABLE, "Shader optimization unavailable (bridge not linked)");
        if (bridge.settings().enabled() != enabled || bridge.preset() != preset)
            return new AppliedStatus(Optimization.UNAVAILABLE, "Shader optimization awaiting frame update");
        if (!bridge.settings().enabled() || bridge.preset() == LodPreset.QUALITY)
            return new AppliedStatus(Optimization.INACTIVE,
                    "Shader optimization inactive (" + (!bridge.settings().enabled() ? "Off" : "Quality") + ")");
        return new AppliedStatus(Optimization.SHADER, selection.adapterId().equals("photon")
                ? "Shader optimization applied (pixel budget)" : "Shader optimization applied (shader budgets)");
    }

'''

CORE_ADDITION = '''    /** One observer per program registration; never combine different programs or frames. */
    public final class BridgeObservation {
        private Frame frame;
        private int fields;
        private Frame observe(int field) {
            var value = current;
            if (frame != value) { frame = value; fields = 0; }
            fields |= field;
            if (fields == 7) appliedBridgeFrame = value;
            return value;
        }
        public int version() { observe(1); return BRIDGE_VERSION; }
        public int enabled() { return observe(2).core().settings().enabled() ? 1 : 0; }
        public int preset() { return observe(4).core().preset().ordinal(); }
    }

    /** Suppliers retained by Iris after uniform type validation observe actual transport reads. */
    public BridgeObservation bridgeObservation() { return new BridgeObservation(); }

    /** All three controls consumed by one program in this exact current pipeline/world frame. */
    public static synchronized java.util.Optional<Core> appliedBridge(Object notifier) {
        var state = FRAMES.get(notifier);
        if (state == null || state.appliedBridgeFrame != state.current || !state.notified || !state.previousValid
                || state.current.temporalVersion() != TEMPORAL_VERSION) return java.util.Optional.empty();
        try {
            return state.previousWorld == state.worldIdentity.get()
                    ? java.util.Optional.of(state.current.core()) : java.util.Optional.empty();
        } catch (RuntimeException unavailable) { return java.util.Optional.empty(); }
    }

'''

SCREEN_CONSTRUCTOR = '''        inspectedPack = Iris.getCurrentPack().orElse(null);
        inspectedName = Iris.getCurrentPackName();
        selection = com.pp.shaderlod.packs.PackSelectionService.inspectSelection(
                Iris.getShaderpacksDirectory(), inspectedName);
        photon = "photon".equals(selection.adapterId());
        feedback = Component.literal(ShaderLodSettings.lastApplyFailure());
'''

SCREEN_STATUS = '''    /** A stable empty selection is unavailable, rather than a change requiring reopen. */
    static String loadedSelectionDetail(String detail, boolean current, boolean initiallyLoaded,
            boolean currentlyLoaded, boolean fallback) {
        return !current && !fallback && (initiallyLoaded || currentlyLoaded)
                ? "Loaded shader changed; reopen settings" : detail;
    }

    /** Requested controls stay staged; the status always reads current committed/loaded state. */
    Component appliedStatus() {
        try {
            var pack = Iris.getCurrentPack().orElse(null);
            var pipeline = Iris.getPipelineManager().getPipelineNullable();
            boolean current = pack != null && pack == inspectedPack
                    && inspectedName.equals(Iris.getCurrentPackName())
                    && Iris.getIrisConfig().getShaderPackName().filter(inspectedName::equals).isPresent();
            var effective = new java.util.TreeMap<String, String>();
            if (current) {
                var options = pack.getShaderPackOptions();
                var declared = options.getOptionSet();
                var values = options.getOptionValues();
                declared.getBooleanOptions().keySet().forEach(key ->
                        effective.put(key, Boolean.toString(values.getBooleanValueOrDefault(key))));
                declared.getStringOptions().keySet().forEach(key ->
                        effective.put(key, values.getStringValueOrDefault(key)));
            }
            var bridge = pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline iris
                    ? com.pp.shaderlod.CoreUniformFrame.appliedBridge(iris.getFrameUpdateNotifier()).orElse(null) : null;
            boolean appliedEnabled = ShaderLodConfig.ENABLED.get();
            var appliedPreset = ShaderLodConfig.PRESET.get();
            var status = com.pp.shaderlod.packs.PackSelectionService.appliedStatus(selection, effective,
                    current && minecraft != null && minecraft.level != null
                            && pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline,
                    Iris.isFallback(), appliedEnabled, appliedPreset,
                    com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled(), bridge);
            return Component.literal(loadedSelectionDetail(status.detail(), current,
                    inspectedPack != null, pack != null, Iris.isFallback()));
        } catch (RuntimeException | LinkageError unavailable) {
            return Component.literal("Optimization status unavailable");
        }
    }

'''

def replace_once(text: str, before: str, after: str) -> str:
    if text.count(before) != 1:
        raise ValueError(f"Expected one exact source anchor, found {text.count(before)}: {before[:70]}")
    return text.replace(before, after, 1)


def transform(path: str, source: str) -> str:
    if path.endswith('/packs/PackSelectionService.java'):
        return replace_once(source, '    /** Private default-off original-graph route;', SERVICE_ADDITION + '    /** Private default-off original-graph route;')
    if path.endswith('/CoreUniformFrame.java'):
        source = replace_once(source, '    private boolean completed;\n', '    private boolean completed;\n    private Frame appliedBridgeFrame;\n')
        return replace_once(source, '    /** Normal return of this exact notifier', CORE_ADDITION + '    /** Normal return of this exact notifier')
    if path.endswith('/mixin/CommonUniformsMixin.java'):
        source = replace_once(source, '        var frequency = UniformUpdateFrequency.PER_FRAME;\n', '        var observedBridge = state.bridgeObservation();\n        var frequency = UniformUpdateFrequency.PER_FRAME;\n')
        source = replace_once(source, '() -> CoreUniformFrame.BRIDGE_VERSION', 'observedBridge::version')
        source = replace_once(source, '() -> state.current().core().preset().ordinal()', 'observedBridge::preset')
        return replace_once(source, '() -> state.current().core().settings().enabled() ? 1 : 0', 'observedBridge::enabled')
    if path.endswith('/ShaderLodSettings.java'):
        source = replace_once(source, '    static java.util.function.Supplier<PackSelectionTransaction> selectionFactory = PackSelectionTransaction::iris;\n',
                '    private static final java.util.function.Supplier<PackSelectionTransaction> DEFAULT_SELECTION = PackSelectionTransaction::iris;\n    static java.util.function.Supplier<PackSelectionTransaction> selectionFactory = DEFAULT_SELECTION;\n    private static String lastApplyFailure = "";\n    static java.util.function.BooleanSupplier rendererAvailability = com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime::nativeGraphAvailable;\n    static java.util.function.Function<Boolean, PackSelectionTransaction> nativeSelectionFactory = PackSelectionTransaction::iris;\n')
        source = replace_once(source, '        return apply(enabled, preset, selectionFactory, applyReload, applyFallback);\n', '''        // Capture the live certificate before Apply suspends renderer work.
        var factory = selectionFactory;
        if (factory == DEFAULT_SELECTION) {
            boolean available = rendererAvailability.getAsBoolean();
            factory = () -> nativeSelectionFactory.apply(available);
        }
        return apply(enabled, preset, factory, applyReload, applyFallback);
''')
        source = replace_once(source, '            if (!changed) return true;\n', '            if (!changed) { lastApplyFailure = ""; return true; }\n')
        source = replace_once(source, '            if (fallback.getAsBoolean()) throw new IllegalStateException("Shader reload fell back");\n            return true;\n', '            if (fallback.getAsBoolean()) throw new IllegalStateException("Shader reload fell back");\n            lastApplyFailure = "";\n            return true;\n')
        source = replace_once(source, '            failureMessage = PackSelectionTransaction.failureMessage(failure);\n', '            failureMessage = PackSelectionTransaction.failureMessage(failure);\n            lastApplyFailure = "Apply failed: " + failureMessage;\n')
        source = replace_once(source, '                ShaderLod.LOGGER.warn("ShaderLOD retained', '                lastApplyFailure = "Apply refused: " + failureMessage;\n                ShaderLod.LOGGER.warn("ShaderLOD retained')
        return replace_once(source, '    public static String failureMessage() { return failureMessage; }\n', '    public static String failureMessage() { return failureMessage; }\n    public static String lastApplyFailure() { return lastApplyFailure; }\n')
    if path.endswith('/ShaderLodSettingsScreen.java'):
        source = replace_once(source, '    private Component packStatus = Component.empty();\n', '''    private final com.pp.shaderlod.packs.PackSelectionService.Selection selection;
    private final Object inspectedPack;
    private final String inspectedName;
''')
        start = source.index('        packStatus = ShaderLodPackDetection.current()')
        end = source.index('\n    }', start)
        source = source[:start] + SCREEN_CONSTRUCTOR.rstrip() + source[end:]
        source = replace_once(source, '            else feedback = Component.literal(ShaderLodSettings.failureMessage());\n', '            else feedback = Component.literal(ShaderLodSettings.lastApplyFailure());\n')
        source = replace_once(source, '    private static Component presetName', SCREEN_STATUS + '    private static Component presetName')
        source = replace_once(source, 'graphics.centeredText(font, packStatus,', 'graphics.centeredText(font, appliedStatus(),')
        return replace_once(source, '        graphics.centeredText(font, feedback,', '''        if (enabled != ShaderLodConfig.ENABLED.get() || preset != ShaderLodConfig.PRESET.get())
            graphics.centeredText(font, Component.literal("Pending changes; Apply to use them"),
                    width / 2, height / 2 + 88, 0xffc0c0c0);
        graphics.centeredText(font, feedback,''')
    raise ValueError(path)


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def prepare(output: Path, root: Path = ROOT) -> dict:
    root, output = root.resolve(), output.resolve()
    allowed = root / 'dev/logs/offline-delivery-sprint-20261012'
    if not output.is_relative_to(allowed) or output == allowed:
        raise ValueError(f"Output must be a fresh child of {allowed}")
    if output.exists():
        raise ValueError('Refusing existing output; retain historical evidence and choose a fresh receiver')
    originals = {}
    candidates = {}
    for path, expected in PINS.items():
        raw = (root/path).read_bytes()
        if sha(raw) != expected:
            raise ValueError(f"Sealed source/preimage drift: {path}")
        originals[path] = raw
        candidates[path] = transform(path, raw.decode()).encode()
    # Collect all source/anchor failures before making a fresh isolated receiver.
    patch = ''.join(''.join(difflib.unified_diff(originals[p].decode().splitlines(True),
        candidates[p].decode().splitlines(True), fromfile='a/'+p, tofile='b/'+p)) for p in PINS)
    receipt = {'schema':1, 'target':'NeoForge 26.2', 'runtime':'paused; no runtime adoption',
        'preimages':PINS, 'candidate_sha256':{p:sha(v) for p,v in candidates.items()},
        'patch_sha256':sha(patch.encode()), 'source_only':True}
    output.mkdir(parents=True)
    for path, data in candidates.items():
        target = output/'source'/path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    (output/'integration.patch').write_text(patch)
    (output/'receipt.json').write_text(json.dumps(receipt, indent=2)+'\n')
    # Confirm the untouched source boundary after producing the isolated copies.
    for path, raw in originals.items():
        if (root/path).read_bytes() != raw:
            raise ValueError(f"Source changed during preparation: {path}")
    return receipt


def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args=parser.parse_args()
    print(json.dumps(prepare(args.output), indent=2))

if __name__ == '__main__': main()
