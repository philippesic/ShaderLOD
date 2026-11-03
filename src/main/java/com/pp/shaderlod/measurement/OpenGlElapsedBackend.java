package com.pp.shaderlod.measurement;
import org.lwjgl.opengl.*;
import org.lwjgl.glfw.GLFW;
/** Real GL_TIME_ELAPSED queries. GL33 calls resolve the same pinned ARB_timer_query functions. */
public final class OpenGlElapsedBackend implements FrameMeasurement.GpuBackend {
    private final boolean diagnosticQueryOff=Boolean.getBoolean("shaderlod.dev.disableGpuQueries");
    private final boolean reuseCompletedQueries=!diagnosticQueryOff&&Boolean.getBoolean("shaderlod.dev.reuseCompletedGpuQueries");
    private GLCapabilities queryCapabilities;
    private long queryContext;
    private boolean contextLost;
    private int bits=-1; private int owned; private String status="uninitialized";
    public boolean diagnosticQueryOff(){return diagnosticQueryOff;}
    public boolean reuseCompletedQueries(){return reuseCompletedQueries;}
    public String status() { initialize();return status; }
    public int counterBits() {initialize();return bits;}
    private void initialize() {
        checkContext();
        if(bits>=0)return;
        if(diagnosticQueryOff){bits=0;status="diagnostic: GPU queries disabled; CPU/frame clocks remain active";return;}
        var caps=GL.getCapabilities();
        if(!caps.OpenGL33&&!caps.GL_ARB_timer_query) {bits=0;status="unsupported: GL33/ARB_timer_query absent";return;}
        bits=GL15.glGetQueryi(GL33.GL_TIME_ELAPSED,GL15.GL_QUERY_COUNTER_BITS);
        status=bits>0?"OpenGL GL_TIME_ELAPSED; nanoseconds; counterBits="+bits:"unsupported: elapsed counter has zero bits";
    }
    /** No GL validation queries: compare the actual GLFW context and LWJGL dispatch identity. */
    private void checkContext() {
        if(!reuseCompletedQueries)return;
        if(contextLost)throw new IllegalStateException("Completed-query reuse refused: GL context/capabilities changed");
        GLCapabilities current;
        try {current=GL.getCapabilities();}
        catch(IllegalStateException missing) {contextLost=true;throw missing;}
        long context=GLFW.glfwGetCurrentContext();
        if(context==0||(queryCapabilities!=null&&(queryCapabilities!=current||queryContext!=context))) {
            contextLost=true;
            throw new IllegalStateException("Completed-query reuse refused: GL context/capabilities changed");
        }
        if(queryCapabilities==null) {queryCapabilities=current;queryContext=context;}
    }
    public int create() {checkContext();int query=GL15.glGenQueries();if(query<=0)throw new IllegalStateException("GPU query allocation failed");return query;}
    public boolean busy() {checkContext();return GL15.glGetQueryi(GL33.GL_TIME_ELAPSED,GL15.GL_CURRENT_QUERY)!=0;}
    public void begin(int query) {
        if(busy())throw new IllegalStateException("Another elapsed query owns the renderer scope");
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
        if(GL15.glGetQueryi(GL33.GL_TIME_ELAPSED,GL15.GL_CURRENT_QUERY)!=query)throw new IllegalStateException("GPU query start was rejected");
        owned=query;
    }
    public void end() {
        checkContext();
        int expected=owned;owned=0;
        if(expected==0||GL15.glGetQueryi(GL33.GL_TIME_ELAPSED,GL15.GL_CURRENT_QUERY)!=expected)
            throw new IllegalStateException("Elapsed query ownership was lost; no GPU result is certified");
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        int error=GL11.glGetError();
        if(error!=GL11.GL_NO_ERROR)throw new IllegalStateException("OpenGL error during measured renderer scope: 0x"+Integer.toHexString(error));
    }
    public boolean available(int query) {checkContext();boolean ready=GL15.glGetQueryObjecti(query,GL15.GL_QUERY_RESULT_AVAILABLE)!=0;checkReadError();return ready;}
    public long nanoseconds(int query) {checkContext();long result=GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT);checkReadError();return result;}
    private void checkReadError() {
        if(reuseCompletedQueries) {
            int error=GL11.glGetError();
            if(error!=GL11.GL_NO_ERROR)throw new IllegalStateException("Completed-query reuse refused: GPU query read error 0x"+Integer.toHexString(error));
        }
    }
    public void delete(int query) {checkContext();GL15.glDeleteQueries(query);}
}
