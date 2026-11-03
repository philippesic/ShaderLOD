package com.pp.shaderlod.port;
import com.pp.shaderlod.integration.RendererBackend;
import com.pp.shaderlod.integration.RendererBackendObservationFixture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RendererBackendTest {
 @Test void onlyPublishedOpenGlBackendIsEligible() {
  assertTrue(RendererBackend.eligible("OpenGL"));
  for (String value : new String[]{"Vulkan", "OpenGL ES", "opengl", "", null}) assertFalse(RendererBackend.eligible(value));
 }
 @Test void openGlObservationIsAllowedExactlyOnce() {
  var observations=new AtomicInteger();
  assertDoesNotThrow(() -> RendererBackendObservationFixture.requireApplySupported(() -> { observations.incrementAndGet(); return "OpenGL"; }));
  assertEquals(1,observations.get());
 }
 @Test void contextRefusalRetainsOriginalCauseAndActionableDetail() {
  var failure=new IllegalStateException("missing device");
  var refusal=assertThrows(RendererBackend.Refusal.class, () -> RendererBackendObservationFixture.requireApplySupported(() -> { throw failure; }));
  assertSame(failure,refusal.getCause());
  assertTrue(refusal.getMessage().contains("graphics context unavailable"));
  assertTrue(refusal.getMessage().contains("Retry on the render thread"));
 }
 @Test void unsupportedObservationRetainsActualName() {
  var refusal=assertThrows(RendererBackend.Refusal.class, () -> RendererBackendObservationFixture.requireApplySupported(() -> "Vulkan"));
  assertTrue(refusal.getMessage().contains("observed backend=Vulkan"));
  assertNull(refusal.getCause());
 }
 @Test void fatalErrorIsNotClassifiedAsUnavailableContext() {
  var fatal=new AssertionError("unrelated fatal error");
  assertSame(fatal,assertThrows(AssertionError.class, () -> RendererBackendObservationFixture.requireApplySupported(() -> { throw fatal; })));
 }
}
