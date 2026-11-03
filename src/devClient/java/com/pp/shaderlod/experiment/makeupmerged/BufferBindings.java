package com.pp.shaderlod.experiment.makeupmerged;
import com.pp.shaderlod.experiment.makeupmerged.mixin.MergedDeviceAccess;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.state.GlStateTracker;
import org.lwjgl.opengl.*;
/** Current-pass copy/VAO mutations restored, then Sodium's cache invalidated before native reuse. */
public final class BufferBindings implements AutoCloseable {
 private final GlStateTracker tracker;
 private final int array,read,write,vao,element;
 private BufferBindings(GlStateTracker tracker){
  this.tracker=tracker;array=GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
  read=GL11C.glGetInteger(GL31C.GL_COPY_READ_BUFFER);write=GL11C.glGetInteger(GL31C.GL_COPY_WRITE_BUFFER);
  vao=GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);element=GL11C.glGetInteger(GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING);
 }
 public static BufferBindings capture(RenderDevice device){return device instanceof MergedDeviceAccess a?new BufferBindings(a.shaderlod$stateTracker()):null;}
 public void close(){
  GL30C.glBindVertexArray(vao);
  if(vao!=0)GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER,element);
  GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER,array);GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER,read);GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER,write);
  tracker.clear(); // Pinned method invalidates handles only; native APIs rebind on their next use.
 }
}
