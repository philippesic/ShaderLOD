package com.pp.shaderlod.port;
import com.google.gson.JsonParser;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Exercise supplied complete profiles against authentic production and actual Loom launcher artifacts. */
class RendererApiProfileTest {
    @Test void productionAndActualLoomConsumersPassButClasswiseMixAndTamperRefuse() throws Exception {
        var root=Path.of("ports","26.3");
        var manifest=JsonParser.parseString(Files.readString(root.resolve("fabric-speed-api-pins.json"))).getAsJsonObject();
        var lock=JsonParser.parseString(Files.readString(root.resolve("dependencies.lock.json"))).getAsJsonObject();
        var urls=new java.util.ArrayList<java.net.URL>();
        for(var value:lock.getAsJsonArray("artifacts")) {
            var artifact=value.getAsJsonObject();
            if(artifact.get("loader").getAsString().equals("fabric") &&
                !artifact.get("kind").getAsString().equals("dh")) urls.add(root.resolve("runtime").resolve(artifact.get("filename").getAsString()).toUri().toURL());
        }
        var productionUrls=new java.util.ArrayList<>(urls);
        productionUrls.add(Path.of(manifest.get("minecraftConsumerArtifact").getAsString()).toUri().toURL());
        var loomUrls=new java.util.ArrayList<>(urls);
        loomUrls.add(Path.of(manifest.get("loomDevelopmentMinecraftConsumerArtifact").getAsString()).toUri().toURL());
        try(var production=new URLClassLoader(productionUrls.toArray(java.net.URL[]::new),null);
            var loom=new URLClassLoader(loomUrls.toArray(java.net.URL[]::new),null)) {
            assertTrue(MakeUpQuadApiPins.resourcesMatch(production));
            assertTrue(MakeUpQuadApiPins.resourcesMatch(loom));
            var mixed=new ClassLoader(null) {
                @Override public InputStream getResourceAsStream(String name) {
                    return name.endsWith("/GlRenderPass.class") ? production.getResourceAsStream(name) : loom.getResourceAsStream(name);
                }
            };
            assertFalse(MakeUpQuadApiPins.resourcesMatch(mixed));
            var tampered=new ClassLoader(null) {
                @Override public InputStream getResourceAsStream(String name) {
                    if(name.endsWith("/MutableOptionValues.class")) return new java.io.ByteArrayInputStream(new byte[]{1,2,3});
                    return loom.getResourceAsStream(name);
                }
            };
            assertFalse(MakeUpQuadApiPins.resourcesMatch(tampered));
        }
    }
}
