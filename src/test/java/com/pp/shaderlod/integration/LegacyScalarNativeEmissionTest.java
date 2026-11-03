package com.pp.shaderlod.integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.integration.makeupquad.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Executes the pinned native emission bytecode, native memory and native maximum scan with the
 * production scalar method bodies. This isolated fixture joins the injection seams explicitly;
 * it does not claim that the loader applied the mixins or that a GL draw occurred.
 */
public class LegacyScalarNativeEmissionTest {
    private static final long GENERATION = 31;
    private static final String BATCH = "net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch";
    private static final String RENDERER = "net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer";
    private static final String BATCH_MIXIN = "com/pp/shaderlod/integration/makeupquad/mixin/LegacyScalarBatchMixin";
    private static final String FILL_MIXIN = "com/pp/shaderlod/integration/makeupquad/mixin/LegacyScalarFillMixin";
    private static final String EMITTER = "com/pp/shaderlod/integration/ScalarNativeEmitter";
    private static final String PLAIN = "com/pp/shaderlod/integration/ScalarPlainNativeEmitter";
    private static final String OBSERVED = "com/pp/shaderlod/integration/ScalarObservedNativeEmitter";
    private static final ClassLoader LOADER = fixtureLoader();
    private static final Method COUNT = method(FILL_MIXIN, "count");
    private static final Method SCOPE = method(FILL_MIXIN, "scope");
    private static final Method EMIT = method(EMITTER, "addNonIndexedDrawCommands");
    private static final Method PLAIN_EMIT = method(PLAIN, "addNonIndexedDrawCommands");
    private static final Method OBSERVED_EMIT = method(OBSERVED, "addNonIndexedDrawCommands");
    private static int getterAttempts, getterReturns, failureFace = -1;
    private static final RuntimeException GETTER_FAILURE = new IllegalStateException("getter seam failure");

    private static ClassNode read(String name) throws Exception {
        try (var in = LegacyScalarNativeEmissionTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(in, name);
            var node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static byte[] bytes(ClassNode node) {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassLoader fixtureLoader() {
        try {
            assertTrue(LegacyScalarApiPins.exact(), "Fixture must use the admitted native Sodium classes");
            var batch = read(BATCH);
            var mixin = read(BATCH_MIXIN);
            batch.interfaces.addAll(mixin.interfaces);
            for (var field : mixin.fields) {
                if (!field.name.startsWith("shaderlod$")) continue; // Native size/capacity stay native.
                batch.fields.add(field);
            }
            for (var method : mixin.methods) {
                if (method.name.startsWith("<")) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.owner.equals(BATCH_MIXIN)) field.owner = BATCH;
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(BATCH_MIXIN)) call.owner = BATCH;
                    if (instruction instanceof FrameNode frame) {
                        if (frame.local != null) frame.local.replaceAll(type -> BATCH_MIXIN.equals(type) ? BATCH : type);
                        if (frame.stack != null) frame.stack.replaceAll(type -> BATCH_MIXIN.equals(type) ? BATCH : type);
                    }
                }
                batch.methods.add(method);
            }
            for (var method : batch.methods) {
                if (!Set.of("clear", "delete").contains(method.name)) continue;
                var head = new InsnList();
                head.add(new VarInsnNode(Opcodes.ALOAD, 0));
                head.add(new InsnNode(Opcodes.ACONST_NULL));
                head.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BATCH, "invalidate",
                    "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", false));
                method.instructions.insert(head);
            }
            var emitter = new ClassNode();
            emitter.version = Opcodes.V21;
            emitter.access = Opcodes.ACC_PUBLIC;
            emitter.name = EMITTER;
            emitter.superName = "java/lang/Object";
            var nativeMethod = read(RENDERER).methods.stream()
                .filter(m -> m.name.equals("addNonIndexedDrawCommands")).findFirst().orElseThrow();
            nativeMethod.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
            var plain = new ClassNode();
            plain.version = Opcodes.V21;
            plain.access = Opcodes.ACC_PUBLIC;
            plain.name = PLAIN;
            plain.superName = "java/lang/Object";
            var plainMethod = read(RENDERER).methods.stream()
                .filter(m -> m.name.equals("addNonIndexedDrawCommands")).findFirst().orElseThrow();
            plainMethod.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
            plain.methods.add(plainMethod);
            int observed = 0;
            for (var instruction : nativeMethod.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || !call.name.equals("getElementCount")) continue;
                assertEquals("net/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe", call.owner);
                assertEquals("(JI)J", call.desc);
                assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                var face = nativeMethod.localVariables.stream().filter(l -> l.name.equals("facing") && l.desc.equals("I")
                    && nativeMethod.instructions.indexOf(l.start) <= nativeMethod.instructions.indexOf(call)
                    && nativeMethod.instructions.indexOf(l.end) > nativeMethod.instructions.indexOf(call)).toList();
                assertEquals(1, face.size(), "The captured face must be unambiguous and live at the invoke");
                assertEquals(11, face.getFirst().index, "Pinned native LVT");
                assertEquals(11, ((VarInsnNode) call.getPrevious()).var, "Getter uses the captured face");
                var locals = new InsnList();
                locals.add(new VarInsnNode(Opcodes.ILOAD, face.getFirst().index));
                locals.add(new VarInsnNode(Opcodes.ILOAD, 3)); // Exact native mask argument.
                locals.add(new VarInsnNode(Opcodes.ALOAD, 0)); // Exact native batch argument.
                locals.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FILL_MIXIN, "count",
                    "(JIIL" + BATCH + ";)J", false));
                // Preserve the actual native invocation; join only its normal result, before L2I.
                nativeMethod.instructions.insert(call, locals);
                observed++;
            }
            assertEquals(1, observed);
            emitter.methods.add(nativeMethod);
            var fill = read(FILL_MIXIN);
            // Test-only visibility change permits a direct typed call with the actual native batch.
            fill.methods.stream().filter(m -> m.name.equals("count")).forEach(m ->
                m.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
            var observedEmitter = new ClassNode();
            new ClassReader(bytes(emitter)).accept(observedEmitter, 0);
            observedEmitter.name = OBSERVED;
            var observedMethod = observedEmitter.methods.getFirst();
            for (var instruction : observedMethod.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || !call.name.equals("getElementCount")) continue;
                var before = new InsnList();
                before.add(new VarInsnNode(Opcodes.ILOAD, 11));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(LegacyScalarNativeEmissionTest.class),
                    "beforeGetter", "(I)V", false));
                observedMethod.instructions.insertBefore(call, before);
                var after = new InsnList();
                after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(LegacyScalarNativeEmissionTest.class),
                    "afterGetter", "()V", false));
                observedMethod.instructions.insert(call, after);
            }
            var definitions = Map.of(BATCH.replace('/', '.'), bytes(batch), EMITTER.replace('/', '.'), bytes(emitter),
                PLAIN.replace('/', '.'), bytes(plain), FILL_MIXIN.replace('/', '.'), bytes(fill),
                OBSERVED.replace('/', '.'), bytes(observedEmitter));
            return new ClassLoader(LegacyScalarNativeEmissionTest.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    byte[] data = definitions.get(name);
                    if (data == null) return super.loadClass(name, resolve);
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> result = findLoadedClass(name);
                        if (result == null) result = defineClass(name, data, 0, data.length);
                        if (resolve) resolveClass(result);
                        return result;
                    }
                }
            };
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static Method method(String owner, String name) {
        try {
            var method = Arrays.stream(LOADER.loadClass(owner.replace('/', '.')).getDeclaredMethods())
                .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            method.setAccessible(true);
            return method;
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException failure) throw failure;
            if (wrapped.getCause() instanceof Error failure) throw failure;
            throw new AssertionError(wrapped.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class Batch implements AutoCloseable {
        final Object nativeBatch;
        final LegacyScalarState.Owner owner;
        final LegacyScalarBatch scalarBatch;
        final long section = SectionRenderDataUnsafe.allocateHeap(1);
        Batch() throws Exception {
            nativeBatch = LOADER.loadClass(BATCH.replace('/', '.')).getConstructor(int.class).newInstance(24);
            owner = (LegacyScalarState.Owner) nativeBatch;
            scalarBatch = (LegacyScalarBatch) nativeBatch;
        }
        void fill(boolean allowed, Runnable body) { fill(allowed, false, body); }
        void fill(boolean allowed, boolean compactionOnly, Runnable body) {
            Operation<Void> nativeFill = arguments -> {
                assertSame(nativeBatch, arguments[0]);
                Operation<Void> clear = clearArguments -> {
                    assertSame(nativeBatch, clearArguments[0]);
                    invoke(method(BATCH, "clear"), nativeBatch);
                    return null;
                };
                clear.call(nativeBatch);
                // Supply admission explicitly: terrain-pass initialization/runtime gates need the client/GL.
                if (allowed) owner.shaderlod$beginScalar(GENERATION, compactionOnly);
                body.run();
                return null;
            };
            invoke(SCOPE, null, nativeBatch, null, null, null, null, null, false, nativeFill);
        }
        void emit(int mask, long... counts) { emitWith(EMIT, mask, counts); }
        void emitWith(Method emitter, int mask, long... counts) {
            assertEquals(7, counts.length);
            for (int face = 0; face < counts.length; face++) {
                SectionRenderDataUnsafe.setElementCount(section, face, counts[face]);
                SectionRenderDataUnsafe.setVertexOffset(section, face, face * 4L);
            }
            invoke(emitter, null, nativeBatch, section, mask);
        }
        long address(String name) {
            try { return nativeBatch.getClass().getField(name).getLong(nativeBatch); }
            catch (Exception failure) { throw new AssertionError(failure); }
        }
        int nativeMaximum() { return (int) invoke(method(BATCH, "getIndexBufferSize"), nativeBatch); }
        LegacyScalarState state() { return scalarBatch.shaderlod$scalar(); }
        void assertNativeMaximum() {
            assertTrue(state().reusable(scalarBatch.shaderlod$size(), GENERATION));
            assertEquals(nativeMaximum(), state().maximum());
            assertNull(owner.shaderlod$recordingScalar(), "Completed fills must release emission recording");
        }
        void compact() {
            int before = scalarBatch.shaderlod$size();
            long result = AdjacentQuadCoalescer.compactWithMaximum(address("pElementPointer"), address("pElementCount"),
                address("pBaseVertex"), before, scalarBatch.shaderlod$capacity(), Pointer.POINTER_SIZE);
            int after = AdjacentQuadCoalescer.compactedSize(result);
            try { nativeBatch.getClass().getField("size").setInt(nativeBatch, after); }
            catch (Exception failure) { throw new AssertionError(failure); }
            int maximum = AdjacentQuadCoalescer.checkedMaximum(result);
            if (maximum >= 0) state().compacted(before, after, maximum, GENERATION);
        }
        @Override public void close() {
            invoke(method(BATCH, "delete"), nativeBatch);
            SectionRenderDataUnsafe.freeHeap(section);
        }
    }

    @Test void nativeMaskedEmissionAndSignedNarrowingMatchFinalScan() throws Exception {
        try (var batch = new Batch()) {
            long[] counts = {6, 0xffff_ffffL, 18, 0x8000_0000L, 24, 0, 12};
            for (int mask : new int[]{0, 1, 0b1010101, 0b0001010, 0b1111111}) {
                batch.fill(true, () -> batch.emit(mask, counts));
                assertEquals(Integer.bitCount(mask), batch.scalarBatch.shaderlod$size());
                batch.assertNativeMaximum();
                assertFalse(batch.state().reusable(batch.scalarBatch.shaderlod$size(), GENERATION + 1));
                int command = 0;
                for (int face = 0; face < 7; face++) if ((mask & (1 << face)) != 0) {
                    assertEquals((int) counts[face], MemoryUtil.memGetInt(batch.address("pElementCount") + 4L * command++));
                }
            }
        }
    }

    @Test void inactiveFillClearDeleteAndCompactorDoNotAllocateState() throws Exception {
        try (var batch = new Batch()) {
            batch.fill(false, () -> {
                assertNull(batch.owner.shaderlod$recordingScalar());
                batch.emit(0b111, 6, 6, 12, 6, 6, 6, 6);
                batch.compact();
            });
            assertEquals(1, batch.scalarBatch.shaderlod$size());
            assertSame(LegacyScalarState.INACTIVE, batch.state());
            assertFalse(batch.state().reusable(1, GENERATION));
            invoke(method(BATCH, "clear"), batch.nativeBatch);
            assertSame(LegacyScalarState.INACTIVE, batch.state());
        }
    }

    @Test void activeCompactionPublishesOnlyItsCheckedFinalMaximum() throws Exception {
        try (var batch = new Batch()) {
            batch.fill(true, () -> {
                batch.emit(0b111, 6, 6, 12, 0, 0, 0, 0);
                assertEquals(12, batch.nativeMaximum());
                batch.compact();
                assertFalse(batch.state().reusable(1, GENERATION), "Compaction cannot finish the fill");
            });
            assertEquals(1, batch.scalarBatch.shaderlod$size());
            assertEquals(24, batch.nativeMaximum());
            batch.assertNativeMaximum();
        }
    }

    @Test void inactiveReturnAndPartialFailureRevokePriorProofAndRecover() throws Exception {
        try (var batch = new Batch()) {
            batch.fill(true, () -> batch.emit(1, 6, 0, 0, 0, 0, 0, 0));
            var retained = batch.state();
            batch.assertNativeMaximum();
            batch.fill(false, () -> batch.emit(1, 60, 0, 0, 0, 0, 0, 0));
            assertSame(retained, batch.state());
            assertNull(batch.owner.shaderlod$recordingScalar());
            assertFalse(retained.reusable(1, GENERATION));
            var expected = new IllegalStateException("partial native fill");
            assertSame(expected, assertThrows(IllegalStateException.class, () -> batch.fill(true, () -> {
                batch.emit(1, 12, 0, 0, 0, 0, 0, 0);
                throw expected;
            })));
            assertNull(batch.owner.shaderlod$recordingScalar());
            assertFalse(retained.reusable(1, GENERATION));
            batch.fill(true, () -> batch.emit(1, 18, 0, 0, 0, 0, 0, 0));
            batch.assertNativeMaximum();
            invoke(method(BATCH, "clear"), batch.nativeBatch);
            assertFalse(retained.reusable(0, GENERATION));
        }
    }

    @Test void differentBatchNestedFillsAndFailuresCannotStealOuterRecording() throws Exception {
        try (var outer = new Batch(); var inner = new Batch()) {
            outer.fill(true, () -> {
                outer.emit(1, 6, 0, 0, 0, 0, 0, 0);
                inner.fill(false, () -> inner.emit(1, 600, 0, 0, 0, 0, 0, 0));
                assertSame(LegacyScalarState.INACTIVE, inner.state());
                inner.fill(true, () -> inner.emit(1, 60, 0, 0, 0, 0, 0, 0));
                inner.assertNativeMaximum();
                assertThrows(IllegalArgumentException.class, () -> inner.fill(true, () -> { throw new IllegalArgumentException(); }));
                outer.emit(1, 12, 0, 0, 0, 0, 0, 0);
            });
            assertEquals(2, outer.scalarBatch.shaderlod$size());
            assertEquals(12, outer.nativeMaximum());
            outer.assertNativeMaximum();
        }
    }

    @Test void sameBatchReentryCannotPublishAnInnerOrOuterProof() throws Exception {
        try (var batch = new Batch()) {
            batch.fill(true, () -> {
                batch.emit(1, 6, 0, 0, 0, 0, 0, 0);
                batch.fill(true, () -> batch.emit(1, 60, 0, 0, 0, 0, 0, 0));
                assertNull(batch.owner.shaderlod$recordingScalar());
                batch.emit(1, 12, 0, 0, 0, 0, 0, 0);
            });
            assertFalse(batch.state().reusable(batch.scalarBatch.shaderlod$size(), GENERATION));
            batch.fill(true, () -> batch.emit(1, 18, 0, 0, 0, 0, 0, 0));
            batch.assertNativeMaximum();
        }
    }

    @Test void inactivePublicationTargetCannotBeActivated() {
        var state = LegacyScalarState.INACTIVE;
        state.begin(true, 24, GENERATION);
        state.emitted(6);
        state.finish(1);
        assertFalse(state.compacted(1, 1, 6, GENERATION));
        assertFalse(state.recording());
        assertFalse(state.reusable(1, GENERATION));
        assertThrows(IllegalStateException.class, state::maximum);
    }

    @Test void nativeDeleteRevokesExistingProofAndKeepsNeverAdmittedStateAbsent() throws Exception {
        var active = new Batch();
        var inactive = new Batch();
        try (active; inactive) {
            active.fill(true, () -> active.emit(1, 6, 0, 0, 0, 0, 0, 0));
            active.assertNativeMaximum();
            assertSame(LegacyScalarState.INACTIVE, inactive.state());
        }
        assertFalse(active.state().reusable(1, GENERATION));
        assertNull(active.owner.shaderlod$recordingScalar());
        assertSame(LegacyScalarState.INACTIVE, inactive.state());
    }
    @Test void everyMaskAndBoundaryCountsPreserveAllNativeCommandBytes() throws Exception {
        var random = new Random(0x5ca1aL);
        long[] edge = {0, 1, 6, 0x7fff_ffffL, 0x8000_0000L, 0xffff_ffffL, 0x7fff_fffeL};
        int cases = 0;
        try (var candidate = new Batch(); var plain = new Batch()) {
            for (boolean active : new boolean[]{false, true}) {
                for (int trial = 0; trial < 8; trial++) {
                    long[] counts = trial == 0 ? edge : random.longs(7, 0, 0x1_0000_0000L).toArray();
                    for (int mask = 0; mask < 128; mask++) {
                        int selected = mask;
                        for (var batch : new Batch[]{candidate, plain}) {
                            MemoryUtil.memSet(batch.address("pElementCount"), 0x5a, 24L * 4);
                            MemoryUtil.memSet(batch.address("pBaseVertex"), 0x6b, 24L * 4);
                            MemoryUtil.memSet(batch.address("pElementPointer"), 0x7c, 24L * Pointer.POINTER_SIZE);
                        }
                        candidate.fill(active, () -> candidate.emit(selected, counts));
                        plain.fill(false, () -> plain.emitWith(PLAIN_EMIT, selected, counts));
                        assertEquals(plain.scalarBatch.shaderlod$size(), candidate.scalarBatch.shaderlod$size());
                        assertEquals(plain.nativeMaximum(), candidate.nativeMaximum());
                        if (active) candidate.assertNativeMaximum();
                        else assertFalse(candidate.state().reusable(Integer.bitCount(mask), GENERATION));
                        for (String field : List.of("pElementCount", "pBaseVertex", "pElementPointer")) {
                            int width = field.equals("pElementPointer") ? Pointer.POINTER_SIZE : 4;
                            for (int offset = 0; offset < 24 * width; offset++) {
                                assertEquals(MemoryUtil.memGetByte(plain.address(field) + offset),
                                    MemoryUtil.memGetByte(candidate.address(field) + offset), field + " byte " + offset);
                            }
                        }
                        cases++;
                    }
                }
            }
        }
        assertEquals(2048, cases);
    }

    @Test void primitiveHandlerReturnsEveryLongBitPatternAndRecordsOnlySelectedFaces() throws Exception {
        long[] values = {Long.MIN_VALUE, -1L, 0L, 6L, 0x7fff_ffffL, 0x8000_0000L,
            0xffff_ffffL, 0x1_0000_0006L, Long.MAX_VALUE};
        try (var batch = new Batch()) {
            for (long value : values) {
                for (int face : new int[]{-1, 0, 6, 7, 31, 32}) {
                    for (int mask : new int[]{0, 1, 0x40, 0x80, Integer.MIN_VALUE, -1}) {
                        var state = batch.owner;
                        state.shaderlod$enterScalarFill();
                        state.shaderlod$beginScalar(GENERATION);
                        assertEquals(value, (long) invoke(COUNT, null, value, face, mask, batch.nativeBatch));
                        boolean selected = face >= 0 && face < 32 && ((mask >>> face) & 1) != 0;
                        batch.nativeBatch.getClass().getField("size").setInt(batch.nativeBatch, selected ? 1 : 0);
                        state.shaderlod$leaveScalarFill(true);
                        assertTrue(batch.state().reusable(selected ? 1 : 0, GENERATION));
                        assertEquals(selected ? Math.max(0, (int) value) : 0, batch.state().maximum());
                    }
                }
            }
        }
    }

    @Test void compiledObserverIsPrimitiveAndHasAnExactExpressionAndLocalContract() throws Exception {
        var handler = read(FILL_MIXIN).methods.stream().filter(m -> m.name.equals("count")).findFirst().orElseThrow();
        assertEquals("(JIIL" + BATCH + ";)J", handler.desc);
        var annotations = handler.visibleAnnotations;
        var annotation = annotations.stream().filter(a -> a.desc.equals(
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;")).findFirst().orElseThrow();
        var values = annotationValues(annotation);
        assertEquals(List.of("addNonIndexedDrawCommands(L" + BATCH + ";JI)V"), values.get("method"));
        for (String key : List.of("require", "expect", "allow")) assertEquals(1, values.get(key));
        assertEquals(1000, values.get("order"));
        var at = (AnnotationNode) ((List<?>) values.get("at")).getFirst();
        assertEquals("INVOKE", annotationValues(at).get("value"));
        assertEquals("Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getElementCount(JI)J",
            annotationValues(at).get("target"));
        assertEquals(0, annotationValues(at).get("ordinal"));
        var face = handler.invisibleParameterAnnotations[1].getFirst();
        assertEquals("Lcom/llamalad7/mixinextras/sugar/Local;", face.desc);
        assertEquals(List.of("facing"), annotationValues(face).get("name"));
        for (int parameter : new int[]{2, 3}) {
            assertEquals(true, annotationValues(handler.invisibleParameterAnnotations[parameter].getFirst()).get("argsOnly"));
        }
        int narrowings = 0, returns = 0;
        for (var instruction : handler.instructions) {
            int opcode = instruction.getOpcode();
            assertFalse(Set.of(Opcodes.NEW, Opcodes.NEWARRAY, Opcodes.ANEWARRAY, Opcodes.MULTIANEWARRAY,
                Opcodes.PUTFIELD, Opcodes.PUTSTATIC).contains(opcode), "No allocations or batch writes");
            if (opcode == Opcodes.L2I) narrowings++;
            if (opcode == Opcodes.LRETURN) {
                returns++;
                assertEquals(Opcodes.LLOAD, instruction.getPrevious().getOpcode());
                assertEquals(0, ((VarInsnNode) instruction.getPrevious()).var, "Return the original long");
            }
            if (instruction instanceof MethodInsnNode call) {
                assertFalse(call.owner.startsWith("java/lang/"), "No boxed primitives");
                assertFalse(call.owner.contains("wrapoperation") || call.name.equals("getElementCount"),
                    "No Operation or direct getter bypass");
                assertTrue(call.name.equals("shaderlod$recordingScalar") || call.name.equals("emitted"));
            }
        }
        assertEquals(1, narrowings);
        assertEquals(1, returns);
    }

    private static Map<String, Object> annotationValues(AnnotationNode annotation) {
        var result = new HashMap<String, Object>();
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        }
        return result;
    }

    // Test-only instrumentation leaves the native getter invocation in place. The deliberate
    // exception is injected immediately before it; invalid unsafe addresses are never executed.
    public static void beforeGetter(int face) {
        getterAttempts++;
        if (face == failureFace) throw GETTER_FAILURE;
    }
    public static void afterGetter() { getterReturns++; }

    @Test void originalGetterRunsOncePerFaceAndFailedInvokeSeamSkipsRecording() throws Exception {
        try (var batch = new Batch()) {
            getterAttempts = getterReturns = 0;
            batch.fill(true, () -> batch.emitWith(OBSERVED_EMIT, 0x7f, 6, 12, 18, 24, 30, 36, 42));
            assertEquals(7, getterAttempts);
            assertEquals(7, getterReturns);
            batch.assertNativeMaximum();
            getterAttempts = getterReturns = 0;
            failureFace = 2;
            assertSame(GETTER_FAILURE, assertThrows(IllegalStateException.class, () -> batch.fill(true, () -> {
                try {
                    batch.emitWith(OBSERVED_EMIT, 0x7f, 6, 12, 18, 24, 30, 36, 42);
                } catch (RuntimeException failure) {
                    try {
                        var observed = LegacyScalarState.class.getDeclaredField("observed");
                        observed.setAccessible(true);
                        assertEquals(2, observed.getInt(batch.state()), "The failing face was not recorded");
                    } catch (ReflectiveOperationException impossible) { throw new AssertionError(impossible); }
                    throw failure;
                }
            })));
            assertEquals(3, getterAttempts);
            assertEquals(2, getterReturns);
            assertNull(batch.owner.shaderlod$recordingScalar());
            assertFalse(batch.state().reusable(batch.scalarBatch.shaderlod$size(), GENERATION));
            failureFace = -1;
            batch.fill(true, () -> batch.emit(1, 18, 0, 0, 0, 0, 0, 0));
            batch.assertNativeMaximum();
        } finally {
            failureFace = -1;
        }
    }


    private static int observed(LegacyScalarState state) {
        try { var field=LegacyScalarState.class.getDeclaredField("observed"); field.setAccessible(true); return field.getInt(state); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void setSize(Batch batch,int size) {
        try { batch.nativeBatch.getClass().getField("size").setInt(batch.nativeBatch,size); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    @Test void compactionOnlyRunsEveryNativeGetterAndPublishesFreshMutatedCounts() throws Exception {
        try (var batch=new Batch()) {
            getterAttempts=getterReturns=0;
            batch.fill(true,true,()-> {
                assertNull(batch.owner.shaderlod$recordingScalar());
                batch.emitWith(OBSERVED_EMIT,0b111,6,6,12,6,6,6,6);
                assertEquals(0,observed(batch.state()),"No duplicate per-face INDEX recording");
                // Mutate actual command storage after emission: the authoritative proof must see this.
                MemoryUtil.memPutInt(batch.address("pElementCount")+8,18);
                batch.compact();
                assertFalse(batch.state().reusable(1,GENERATION),"Publication awaits successful scope exit");
            });
            assertEquals(7,getterAttempts);assertEquals(7,getterReturns);
            assertEquals(1,batch.scalarBatch.shaderlod$size());assertEquals(30,batch.nativeMaximum());
            batch.assertNativeMaximum();
        }
    }
    @Test void compactionOnlyMissingRefusedOrIneligiblePublicationUsesNativeFallback() throws Exception {
        try(var batch=new Batch()) {
            for(String cause:List.of("missing","nonquad","foreign","empty-without-return")) {
                batch.fill(true,true,()-> {
                    if(cause.equals("empty-without-return")) return;
                    batch.emit(0b11,6,6,0,0,0,0,0);
                    if(cause.equals("nonquad"))MemoryUtil.memPutInt(batch.address("pElementCount")+4,5);
                    if(cause.equals("foreign"))MemoryUtil.memPutAddress(batch.address("pElementPointer"),8);
                    if(!cause.equals("missing"))batch.compact();
                });
                assertFalse(batch.state().reusable(batch.scalarBatch.shaderlod$size(),GENERATION),cause);
                assertNull(batch.owner.shaderlod$recordingScalar());
            }
            // Standalone INDEX still records when Quad was not requested.
            batch.fill(true,()->batch.emit(1,18,0,0,0,0,0,0));batch.assertNativeMaximum();
        }
    }
    @Test void compactionOnlyExceptionsBeforeAndAfterPublicationCannotEscapeScope() throws Exception {
        try(var batch=new Batch()) {
            for(boolean publish:List.of(false,true)) {
                var failure=new IllegalStateException("after native work");
                assertSame(failure,assertThrows(IllegalStateException.class,()->batch.fill(true,true,()->{
                    batch.emit(0b11,6,6,0,0,0,0,0);if(publish)batch.compact();throw failure;
                })));
                assertFalse(batch.state().reusable(batch.scalarBatch.shaderlod$size(),GENERATION));
                assertNull(batch.owner.shaderlod$recordingScalar());
                assertFalse(batch.state().compacted(2,1,12,GENERATION));
                batch.fill(true,true,()->{batch.emit(0b11,6,6,0,0,0,0,0);batch.compact();});
                batch.assertNativeMaximum();
            }
        }
    }
    @Test void compactionOnlySameBatchReentryPoisonsBothScopesButDifferentBatchIsIndependent() throws Exception {
        try(var batch=new Batch();var other=new Batch()) {
            batch.fill(true,true,()-> {
                batch.emit(1,6,0,0,0,0,0,0);
                other.fill(true,true,()->{other.emit(1,60,0,0,0,0,0,0);other.compact();});
                other.assertNativeMaximum();batch.compact();
            });batch.assertNativeMaximum();
            batch.fill(true,true,()-> {
                batch.emit(1,6,0,0,0,0,0,0);
                batch.fill(true,true,()->{batch.emit(1,60,0,0,0,0,0,0);batch.compact();});
                batch.compact();
            });
            assertFalse(batch.state().reusable(batch.scalarBatch.shaderlod$size(),GENERATION));
            batch.fill(true,true,()->{batch.emit(1,18,0,0,0,0,0,0);batch.compact();});batch.assertNativeMaximum();
        }
    }
    @Test void compactionOnlyStaleGenerationCapacityAndFinalCountMismatchFailClosed() throws Exception {
        try(var batch=new Batch()) {
            batch.fill(true,true,()-> {
                batch.emit(0b11,6,6,0,0,0,0,0);
                assertFalse(batch.state().compacted(2,1,12,GENERATION+1));
                setSize(batch,1);
            });assertFalse(batch.state().reusable(1,GENERATION));
            batch.fill(true,true,()-> {
                batch.emit(0b11,6,6,0,0,0,0,0);batch.compact();setSize(batch,2);
            });assertFalse(batch.state().reusable(2,GENERATION));
            // A failed finish permanently revokes pending publication.
            assertFalse(batch.state().compacted(2,1,12,GENERATION));
            batch.fill(true,true,()-> {
                batch.emit(0b11,6,6,0,0,0,0,0);
                assertFalse(batch.state().compacted(batch.scalarBatch.shaderlod$capacity()+1,1,12,GENERATION));
            });assertFalse(batch.state().reusable(2,GENERATION));
            batch.fill(true,true,()->{batch.emit(0b11,6,6,0,0,0,0,0);batch.compact();});
            batch.assertNativeMaximum();assertFalse(batch.state().reusable(1,GENERATION+1));
        }
    }
    @Test void compactionOnlyLoneLateAndDuplicateStampsCannotActivateMetadata() throws Exception {
        try(var batch=new Batch()) {
            batch.owner.shaderlod$beginScalar(GENERATION,true);
            assertSame(LegacyScalarState.INACTIVE,batch.state());
            assertFalse(batch.state().compacted(2,1,12,GENERATION));
            batch.fill(true,true,()->{batch.emit(0b11,6,6,0,0,0,0,0);});
            assertFalse(batch.state().compacted(2,1,12,GENERATION));
            batch.state().finish(1);assertFalse(batch.state().reusable(1,GENERATION));
            batch.fill(true,true,()-> {
                batch.emit(0b11,6,6,0,0,0,0,0);batch.compact();
                assertFalse(batch.state().compacted(2,1,12,GENERATION),"Single publication per pending fill");
            });assertFalse(batch.state().reusable(1,GENERATION));
            batch.fill(true,true,()->{batch.emit(0b11,6,6,0,0,0,0,0);batch.compact();});batch.assertNativeMaximum();
            assertFalse(batch.state().compacted(1,1,12,GENERATION),"Completed fill cannot accept a new stamp");
            assertFalse(batch.state().reusable(1,GENERATION));
        }
    }
    @Test void compactionOnlyGetterExceptionPreservesNativeAttemptsAndNeverRecords() throws Exception {
        try(var batch=new Batch()) {
            getterAttempts=getterReturns=0;failureFace=2;
            assertSame(GETTER_FAILURE,assertThrows(IllegalStateException.class,()->batch.fill(true,true,()-> {
                try { batch.emitWith(OBSERVED_EMIT,0x7f,6,12,18,24,30,36,42); }
                catch(RuntimeException failure){assertEquals(0,observed(batch.state()));throw failure;}
            })));
            assertEquals(3,getterAttempts);assertEquals(2,getterReturns);
            assertFalse(batch.state().reusable(batch.scalarBatch.shaderlod$size(),GENERATION));
        } finally {failureFace=-1;}
    }
    @Test void compactionOnlyAllMasksPreserveEntireNativeCommandStorageAndOriginalLongNarrowing() throws Exception {
        var random=new Random(0xca11L);int cases=0;
        try(var candidate=new Batch();var plain=new Batch()) {
            for(int trial=0;trial<8;trial++)for(int mask=0;mask<128;mask++) {
                int selected=mask;long[] counts=trial==0?new long[]{0,6,0x7fff_ffffL,0x8000_0000L,0xffff_ffffL,0x7fff_fffeL,0}:random.longs(7,0,0x1_0000_0000L).toArray();
                for(var batch:new Batch[]{candidate,plain})for(String field:List.of("pElementCount","pBaseVertex","pElementPointer"))
                    MemoryUtil.memSet(batch.address(field),0x5a,24L*(field.equals("pElementPointer")?Pointer.POINTER_SIZE:4));
                candidate.fill(true,true,()-> {candidate.emit(selected,counts);assertEquals(0,observed(candidate.state()));});
                plain.fill(false,()->plain.emitWith(PLAIN_EMIT,selected,counts));
                assertEquals(plain.scalarBatch.shaderlod$size(),candidate.scalarBatch.shaderlod$size());
                assertEquals(plain.nativeMaximum(),candidate.nativeMaximum());
                assertFalse(candidate.state().reusable(Integer.bitCount(mask),GENERATION));
                for(String field:List.of("pElementCount","pBaseVertex","pElementPointer"))
                    for(int i=0;i<24*(field.equals("pElementPointer")?Pointer.POINTER_SIZE:4);i++)
                        assertEquals(MemoryUtil.memGetByte(plain.address(field)+i),MemoryUtil.memGetByte(candidate.address(field)+i));
                cases++;
            }
        }assertEquals(1024,cases);
    }

}
