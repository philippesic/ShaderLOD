package com.pp.shaderlod.optimization.sourcecache;

import com.google.common.collect.ImmutableSet;
import java.util.*;
import net.minecraft.server.packs.repository.PackRepository;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceLifecycleResourceTest {
    @Test void capturedIdsMatchThePinnedMinecraftSetAndStillRejectSelectionChanges() {
        var selected=ImmutableSet.of("vanilla","mod_resources");
        var captured=List.copyOf(selected);
        assertTrue(IrisSourceLifecycle.selectedResourcesMatch(captured,selected),"Unchanged Minecraft Set must match the captured List");
        assertFalse(IrisSourceLifecycle.selectedResourcesMatch(captured,ImmutableSet.of("vanilla")));
        assertFalse(IrisSourceLifecycle.selectedResourcesMatch(captured,ImmutableSet.of("vanilla","different_pack")));
        assertFalse(IrisSourceLifecycle.selectedResourcesMatch(captured,List.of("mod_resources","vanilla")));
    }

    @Test void actualRootMinecraftSelectedIdsUsesAnImmutableSetCollector()throws Exception {
        var observed=new ArrayList<String>();
        try(var input=PackRepository.class.getResourceAsStream("PackRepository.class")) {
            assertNotNull(input);
            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if(!name.equals("getSelectedIds"))return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean isInterface) {
                            observed.add(owner+"."+name);
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertTrue(observed.contains("com/google/common/collect/ImmutableSet.toImmutableSet"),observed.toString());
    }
}
