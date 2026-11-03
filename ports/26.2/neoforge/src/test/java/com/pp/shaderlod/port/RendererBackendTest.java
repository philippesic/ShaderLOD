package com.pp.shaderlod.port;
import com.pp.shaderlod.integration.RendererBackend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RendererBackendTest {
    @Test void observedExactOpenGlIsAccepted() { assertDoesNotThrow(() -> RendererBackend.requireSupportedIdentity("OpenGL", "com.mojang.blaze3d.opengl.GlDevice")); }
    @Test void vulkanIsRefused() { assertThrows(IllegalStateException.class, () -> RendererBackend.requireSupportedIdentity("Vulkan", "com.mojang.blaze3d.vulkan.VulkanDevice")); }
    @Test void openGlLabelCannotCertifyUnknownImplementation() { assertThrows(IllegalStateException.class, () -> RendererBackend.requireSupportedIdentity("OpenGL", "unknown.Device")); }
    @Test void unknownBackendIsRefused() { assertThrows(IllegalStateException.class, () -> RendererBackend.requireSupportedIdentity(null, null)); }
}
