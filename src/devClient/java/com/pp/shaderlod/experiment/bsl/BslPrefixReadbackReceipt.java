package com.pp.shaderlod.experiment.bsl;
import com.google.gson.*;
import java.util.Arrays;
import java.util.function.Supplier;

/** One finite DEV operation; persists both snapshots even when capture throws.
 * Proxy fixtures exercise control flow only, never prove GL restoration. */
public final class BslPrefixReadbackReceipt {
    @FunctionalInterface public interface Capture<T>{T get()throws Exception;}
    public static <T>T observe(JsonObject report,Supplier<int[]> state,Capture<T> capture)throws Exception {
        int[] before=null;T result=null;Throwable failure=null;
        report.addProperty("GL_restoration_status","UNKNOWN");
        report.addProperty("GPU_capture_status","NOT_ATTEMPTED");
        try {
            before=state.get().clone();report.add("GL_state_before",new Gson().toJsonTree(before));
            report.addProperty("GPU_capture_status","ATTEMPTED");
            result=capture.get();report.addProperty("GPU_capture_status","RETURNED");
        }catch(Exception|Error error){failure=error;report.addProperty("GPU_capture_error",error.toString());}
        finally {
            try {
                int[] after=state.get().clone();report.add("GL_state_after",new Gson().toJsonTree(after));
                boolean restored=before!=null&&Arrays.equals(before,after);
                report.addProperty("GL_restoration_status",before==null?"UNKNOWN_BEFORE_UNAVAILABLE":restored?"STATE_VECTOR_MATCH":"STATE_VECTOR_MISMATCH");
                if(!restored){var mismatch=new IllegalStateException("finite diagnostic restoration unavailable/mismatch");
                    if(failure==null)failure=mismatch;else failure.addSuppressed(mismatch);}
            }catch(Exception|Error afterError){report.addProperty("GL_restoration_status","UNKNOWN_AFTER_QUERY_FAILED");report.addProperty("GL_state_after_error",afterError.toString());
                if(failure==null)failure=afterError;else failure.addSuppressed(afterError);}
        }
        if(failure!=null){report.addProperty("GPU_capture_status","FAILED_NO_ADMISSION");
            report.addProperty("certificate_active",false);
            if(failure instanceof Exception e)throw e;throw (Error)failure;}
        return result;
    }
    private BslPrefixReadbackReceipt(){}
}
