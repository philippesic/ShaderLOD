package com.pp.shaderlod.fabric;

import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.io.IOException;
import java.nio.file.Path;
import net.irisshaders.iris.Iris;

/** Fabric adapter; selection transaction is shared with NeoForge. */
final class FabricPackSelection {
    private final PackSelectionTransaction transaction = PackSelectionTransaction.iris();
    boolean prepare(boolean enabled) throws IOException { return transaction.prepare(enabled); }
    boolean prepare(boolean enabled, LodPreset preset) throws IOException { return transaction.prepare(enabled, preset); }
    void restore() throws IOException { transaction.restore(); }
    static String recognition() {
        try {
            String name = Iris.getIrisConfig().getShaderPackName().orElse(null);
            if (name == null) return "No shader selected";
            Path directory = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
            Path path = directory.resolve(name).normalize();
            if (!path.getParent().equals(directory)) return "Unknown source";
            var source = PackSourceReader.read(path);
            var recognized = SupportedPackCatalog.recognize(source.memberSha256());
            if (recognized.isPresent()) return recognized.get().pack().name() + ": "
                    + (recognized.get().stage() == SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER
                        ? "LOD adapter detected" : "LOD available");
            if (source.memberSha256().containsKey("shaderlod-selection.json")) return "Private adapter marker; Apply validates source";
            return "Unrecognized source version";
        } catch (IOException | RuntimeException unavailable) { return "Source recognition unavailable"; }
    }
}
