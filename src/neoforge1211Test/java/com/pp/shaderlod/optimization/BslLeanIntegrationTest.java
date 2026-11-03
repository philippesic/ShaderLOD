package com.pp.shaderlod.optimization;

import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Iris static initialization is intentionally failed by other headless guard tests.
 * Keep the real Iris IncludeGraph/config fixture in a fresh JVM, preserving both contracts. */
class BslLeanIntegrationTest {
    @TempDir Path temporary;
    @Test void pinnedArchiveRealProducerParserAndRecovery()throws Exception {
        var classpath=new LinkedHashSet<String>();
        for(ClassLoader loader=getClass().getClassLoader();loader!=null;loader=loader.getParent())
            if(loader instanceof URLClassLoader urls)for(var url:urls.getURLs())
                if(url.getProtocol().equals("file"))classpath.add(Path.of(url.toURI()).toString());
        classpath.addAll(List.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        Path output=temporary.resolve("check.txt");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
            String.join(java.io.File.pathSeparator,classpath),BslLeanChecksMain.class.getName(),temporary.toString())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60,TimeUnit.SECONDS),"finite real-parser fixture exceeded60s");
            assertEquals(0,process.exitValue(),()->{try{return Files.readString(output);}catch(Exception failure){return failure.toString();}});
            var report=Files.readString(output);assertTrue(report.contains("PASS 10 actual pinned BSL/Solas/Astra"));
            report.lines().filter(line->line.startsWith("PASS ")).forEach(System.out::println);
        } finally {if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
    }
}
