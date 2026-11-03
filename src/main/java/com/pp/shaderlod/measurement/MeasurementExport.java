package com.pp.shaderlod.measurement;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
/** Unique export receivers preserve raw samples, source pins, validity and phase denominators. */
public final class MeasurementExport {
    private MeasurementExport() {}
    public static Path write(Path directory,PairedMeasurement paired,List<FrameMeasurement.Sample> samples,String status) throws IOException {return write(directory,paired,samples,status,Map.of());}
    public static Path write(Path directory,PairedMeasurement paired,List<FrameMeasurement.Sample> samples,String status,Map<String,String> manualPins) throws IOException {
        Files.createDirectories(directory);Path destination=Files.createTempDirectory(directory,"shaderlod-"+Instant.now().toString().replace(':','-')+"-");
        var metadata=new Properties();metadata.putAll(manualPins);metadata.setProperty("status",status);metadata.setProperty("created_at",Instant.now().toString());
        if(paired!=null) {
            metadata.putAll(paired.fixedPins());metadata.setProperty("feature",paired.selected().key());metadata.setProperty("phase",paired.phase().name());metadata.setProperty("paired_status",paired.status());
            metadata.setProperty("baseline_state",paired.baselinePhase().name());metadata.setProperty("return_state",paired.baselinePhase().name());
            metadata.setProperty("phase_order",paired.baselinePhase()==PairedMeasurement.Phase.ON?"ON,OFF,RETURN":"OFF,ON,RETURN");
            paired.phasePins().forEach((phase,pins)->pins.forEach((key,value)->metadata.setProperty("phase_"+phase+"_"+key,value)));
            paired.summary().forEach((key,value)->metadata.setProperty(key,Double.toString(value)));
            for(var phase:PairedMeasurement.Phase.values())metadata.setProperty("samples_"+phase,Long.toString(paired.rows().stream().filter(row->row.phase()==phase).count()));
        }
        try(var writer=Files.newBufferedWriter(destination.resolve("metadata.properties"))) {metadata.store(writer,"ShaderLOD actual clocks; unavailable metrics remain empty, no inferred GPU time");}
        try(var writer=Files.newBufferedWriter(destination.resolve("samples.csv"))) {
            writer.write("phase,frame,epoch,frame_wall_ns,render_wall_ns,render_cpu_ns,process_cpu_ns,gpu_ns,gpu_status\n");
            if(paired!=null)for(var row:paired.rows())write(writer,row.phase().name(),row.sample());
            else for(var sample:samples)write(writer,"MANUAL",sample);
        }
        return destination;
    }
    private static void write(Writer writer,String phase,FrameMeasurement.Sample s) throws IOException {
        writer.write(phase+","+s.frame()+","+s.epoch()+","+value(s.frameWallNs())+","+s.renderWallNs()+","+value(s.renderCpuNs())+","+value(s.processCpuNs())+","+value(s.gpuNs())+",\""+s.gpuStatus().replace("\"","\"\"")+"\"\n");
    }
    private static String value(Long value) {return value==null?"":value.toString();}
}
