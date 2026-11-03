package com.pp.shaderlod.integration.bindingcompat;
import java.util.*;
/** Exact consumed-input memo; identity/generation/content changes invalidate before reuse. No file or game IO. */
public final class BindingInputCertificateCache {
    private Object pack,pipeline,world,values,sourceStamp;
    private long generation;
    private long optimizationGeneration=-1;
    private String name;
    private Map<String,Boolean> booleans=Map.of();
    private Map<String,String> strings=Map.of();
    private List<String> resources=List.of();
    private boolean valid,eligible;
    public void invalidate() {valid=false;}
    public boolean matches(Object pack,Object pipeline,Object world,Object values,long generation,String name,
        Map<String,Boolean> booleans,Map<String,String> strings,Collection<String> resources,Object sourceStamp) {
        long currentGeneration=com.pp.shaderlod.optimization.OptimizationConfig.generation();
        if(currentGeneration!=optimizationGeneration || !com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.BINDING_CERTIFICATE_CACHE)) {invalidate();return false;}
        return valid && this.pack==pack && this.pipeline==pipeline && this.world==world && this.values==values &&
            this.generation==generation && Objects.equals(this.name,name) && this.booleans.equals(booleans) && this.strings.equals(strings) &&
            resourcesMatch(resources) && Objects.equals(this.sourceStamp,sourceStamp);
    }
    public void remember(Object pack,Object pipeline,Object world,Object values,long generation,String name,
        Map<String,Boolean> booleans,Map<String,String> strings,Collection<String> resources,Object sourceStamp,boolean eligible) {
        optimizationGeneration=com.pp.shaderlod.optimization.OptimizationConfig.generation();
        this.pack=pack;this.pipeline=pipeline;this.world=world;this.values=values;this.generation=generation;this.name=name;
        this.booleans=Map.copyOf(booleans);this.strings=Map.copyOf(strings);this.resources=List.copyOf(resources);this.sourceStamp=sourceStamp;
        this.eligible=eligible;valid=true;
    }
    /** Compare the actual API Collection in iteration order without trusting Collection.equals or copying per draw. */
    private boolean resourcesMatch(Collection<String> actual) {
        if(actual==null || resources.size()!=actual.size())return false;
        var expected=resources.iterator();var observed=actual.iterator();
        while(expected.hasNext())if(!observed.hasNext() || !Objects.equals(expected.next(),observed.next()))return false;
        return !observed.hasNext();
    }
    public boolean eligible() {if(optimizationGeneration!=com.pp.shaderlod.optimization.OptimizationConfig.generation())invalidate();return valid && eligible;}
}
