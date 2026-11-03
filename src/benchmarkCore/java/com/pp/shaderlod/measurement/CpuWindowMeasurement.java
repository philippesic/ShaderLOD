package com.pp.shaderlod.measurement;

/** Cumulative CPU endpoints across the whole main loop, independent of per-render samples. */
public final class CpuWindowMeasurement {
    public record Snapshot(long threadId,long wallNs,long threadCpuNs,long processCpuNs) {}
    public record Result(Snapshot start,Snapshot end,long completedFrames,long elapsedWallNs,
                         Long threadCpuNs,Long processCpuNs,Double threadCpuMsPerFrame,
                         Double processCpuMsPerFrame,String threadStatus,String processStatus,
                         String scope) {}
    private final Snapshot start;
    private long completedFrames;
    private boolean frameOpen, wrongThread;
    private Result result;
    public CpuWindowMeasurement(Snapshot start) { this.start=start; }
    public void frameStarted(long threadId) {
        if(result!=null)return;
        if(threadId!=start.threadId())wrongThread=true;
        frameOpen=true;
    }
    public void frameCompleted(long threadId) {
        if(!frameOpen||result!=null)return;
        frameOpen=false;completed(threadId);
    }
    private void completed(long threadId) {
        if(result!=null)return;
        if(threadId!=start.threadId()){wrongThread=true;return;}
        completedFrames++;
    }
    public Result finish(Snapshot end) {
        if(result==null)result=snapshot(end);
        return result;
    }
    /** A live export must not stop the counters or freeze subsequent completed frames. */
    public Result snapshot(Snapshot end) {
        if(result!=null)return result;
        long elapsed=end.wallNs()-start.wallNs();
        String invalid=wrongThread?"frame thread changed":end.threadId()!=start.threadId()?"thread changed":elapsed<=0?"nonpositive window":completedFrames<=0?"no completed frames":null;
        String threadStatus=invalid!=null?invalid:end.threadId()!=start.threadId()?"thread changed":status(start.threadCpuNs(),end.threadCpuNs());
        String processStatus=invalid!=null?invalid:status(start.processCpuNs(),end.processCpuNs());
        Long thread=threadStatus.equals("available")?end.threadCpuNs()-start.threadCpuNs():null;
        Long process=processStatus.equals("available")?end.processCpuNs()-start.processCpuNs():null;
        return new Result(start,end,completedFrames,elapsed,thread,process,
                thread==null?null:thread/1e6/completedFrames,process==null?null:process/1e6/completedFrames,
                threadStatus,processStatus,"Whole main/render-thread loop from first eligible render start to capture pause/stop; includes ticks, submission and caller CPU; excludes descheduled waiting. Process CPU includes all JVM threads. Counter resolution is not inferred from nanosecond units; endpoints retained.");
    }
    public Result result() { return result; }
    private static String status(long before,long after) {
        if(before<0||after<0)return "unsupported";
        if(after<before)return "counter regressed";
        if(after==before)return "counter did not advance";
        return "available";
    }
}
