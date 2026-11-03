package com.pp.shaderlod.optimization;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
class ComplementaryTemporalWiringTest {
    private ClassNode read(String name) throws Exception {
        var node=new ClassNode();try(var in=getClass().getClassLoader().getResourceAsStream(name+".class")){assertNotNull(in);new ClassReader(in).accept(node,0);}return node;
    }
    private List<MethodInsnNode> calls(MethodNode method){var result=new ArrayList<MethodInsnNode>();for(var i:method.instructions)if(i instanceof MethodInsnNode c)result.add(c);return result;}
    private MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    @Test void temporalEvidenceRunsBeforeAlphaAdmissionAndKeepsReturnedProgramObject() throws Exception {
        for(String family:List.of("Unbound","Reimagined")){
            var root="com/pp/shaderlod/optimization/"+family.toLowerCase()+"earlyalpha/";
            var mixin=read(root+"mixin/"+family+"AlphaProgramsMixin");
            for(String name:List.of("prepare","actual")){
                var list=calls(method(mixin,name)).stream().filter(c->c.owner.startsWith(root)&&!c.name.equals("epoch")).toList();
                assertEquals(root+family+"TemporalCompiledPrograms",list.getFirst().owner);
                assertEquals(name.equals("prepare")?"prepare":"transformed",list.getFirst().name);
            }
            assertTrue(calls(method(mixin,"compiled")).stream().anyMatch(c->c.owner.equals(root+family+"TemporalCompiledPrograms")&&c.name.equals("compiled")));
            var own=method(mixin,"own");
            assertTrue(own.visibleAnnotations.stream().anyMatch(a->a.desc.endsWith("/ModifyReturnValue;")));
            assertTrue(calls(own).stream().anyMatch(c->c.name.equals("ownTemporalNative")&&c.desc.contains("GlProgram;")));
            var runtime=read(root+family+"EarlyAlphaRuntime");
            var nativeAlpha=runtime.methods.stream().filter(m->m.name.equals("own")&&m.desc.contains("Certificate;")).findFirst().orElseThrow();
            assertFalse(calls(nativeAlpha).stream().anyMatch(c->c.name.equals("ownTemporal")),"native alpha registration must not overwrite temporal receipt");
            assertTrue(calls(method(runtime,"ownTemporal")).stream().anyMatch(c->c.name.equals("legacyProgram")));
            assertTrue(calls(method(runtime,"ownTemporalSpatial")).stream().anyMatch(c->c.name.equals("legacyProgram")));
        }
    }
    @Test void drawChecksReceiptWithoutCallingLegacySourceDirectlyAndRetainsLiveGuards() throws Exception {
        for(String family:List.of("Unbound","Reimagined")){
            var runtime=read("com/pp/shaderlod/optimization/"+family.toLowerCase()+"earlyalpha/"+family+"EarlyAlphaRuntime");
            var draw=method(runtime,"temporalDraw");var list=calls(draw);var names=new HashSet<String>();list.forEach(c->names.add(c.name));
            assertFalse(names.contains("temporalSource"));assertFalse(names.contains("drawTemporalSource"));
            assertTrue(names.containsAll(Set.of("temporalSourceForDraw","loadedStackMatches","resourcesMatch","getShaderPackOptionQueue","areShadowsCurrentlyBeingRendered","isOnRenderThread","glGetProgrami","sampler","nativeProgram","temporalHalfViewport")));
            int firstZero=-1,proof=-1,grant=-1;
            for(int i=0;i<draw.instructions.size();i++)if(draw.instructions.get(i) instanceof MethodInsnNode c){
                if(c.name.equals("temporalSourceForDraw"))proof=i;
                if(c.name.equals("glUniform1i")){if(c.getPrevious().getOpcode()==Opcodes.ICONST_0&&firstZero<0)firstZero=i;if(c.getPrevious().getOpcode()==Opcodes.ICONST_1)grant=i;}
            }
            assertTrue(firstZero>=0&&firstZero<proof&&proof<grant);
            assertTrue(calls(method(runtime,"temporalSourceForDraw")).stream().anyMatch(c->c.name.equals("allows")));
            assertTrue(calls(method(runtime,"revoke")).stream().anyMatch(c->c.name.equals("revokeCurrent")));
            assertTrue(calls(method(runtime,"revokeCurrent")).stream().anyMatch(c->c.name.equals("glUniform1i")));
        }
    }
    static final class ActualProgram extends net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<Object> {
        ActualProgram(int id){super(id,context->new Object());}
    }
    @Test void actualSodiumProgramHandleInvalidationRevokesProofWithoutAnyGlCall(){
        var receipts=new com.pp.shaderlod.optimization.sourcecache.TemporalProgramReceipts<ActualProgram,String>(p->p.handle());
        var original=new ActualProgram(31);receipts.nativeProgram(31,original,"proof",Map.of());
        assertTrue(receipts.allows(31,c->true,()->false));original.invalidateHandle();
        assertThrows(IllegalStateException.class,original::handle);
        assertFalse(receipts.allows(31,c->true,()->{fail("must not read source");return true;}));
        receipts.nativeProgram(31,new ActualProgram(31),null,Map.of());assertFalse(receipts.allows(31,c->true,()->true));
    }
}
