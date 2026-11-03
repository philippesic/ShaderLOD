package com.pp.shaderlod.optimization.sourcecache;

import java.util.Map;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedCompilationInputs;

/** Exact immutable defaults plus live raw overrides imply the same effective options. */
public record RawOptionOverrides(OptionSet declarations, MutableOptionValues values,
        Map<String,Boolean> booleans, Map<String,String> strings) {
    public RawOptionOverrides { booleans=Map.copyOf(booleans);strings=Map.copyOf(strings); }
    private static final boolean PINNED=pinned();
    private static boolean pinned() { return SourceLifecyclePins.optionClasses(); }
    /** These pinned exact types store declarations/defaults/domains in immutable fields. */
    public static boolean immutableDefaults(OptionSet options){
        if(!PINNED||options==null||options.getClass()!=OptionSet.class)return false;
        for(var entry:options.getBooleanOptions().values())if(entry.getClass()!=MergedBooleanOption.class||entry.getOption().getClass()!=BooleanOption.class)return false;
        for(var entry:options.getStringOptions().values())if(entry.getClass()!=MergedStringOption.class||entry.getOption().getClass()!=StringOption.class)return false;
        return true;
    }
    public static RawOptionOverrides capture(ShaderPackOptions owner, ReimaginedCompilationInputs captured) {
        if(!PINNED || owner==null || owner.getOptionSet().getClass()!=OptionSet.class
                || owner.getOptionValues().getClass()!=MutableOptionValues.class)return null;
        var declarations=owner.getOptionSet();var values=(MutableOptionValues)owner.getOptionValues();
        if(values.getOptions()!=declarations)return null;
        try {
            var result=new RawOptionOverrides(declarations,values,values.getBooleanValues(),values.getStringValues());
            if(!declarations.getBooleanOptions().keySet().equals(captured.booleanOptions().keySet())
                    || !declarations.getStringOptions().keySet().equals(captured.stringOptions().keySet()))return null;
            for(var entry:declarations.getBooleanOptions().entrySet()) {
                var merged=entry.getValue();
                if(merged.getClass()!=MergedBooleanOption.class || merged.getOption().getClass()!=BooleanOption.class)return null;
                boolean effective=result.booleans().getOrDefault(entry.getKey(),merged.getOption().getDefaultValue());
                if(effective!=captured.booleanOptions().get(entry.getKey()))return null;
            }
            for(var entry:declarations.getStringOptions().entrySet()) {
                var merged=entry.getValue();
                if(merged.getClass()!=MergedStringOption.class || merged.getOption().getClass()!=StringOption.class)return null;
                String effective=result.strings().getOrDefault(entry.getKey(),merged.getOption().getDefaultValue());
                if(!effective.equals(captured.stringOptions().get(entry.getKey())))return null;
            }
            return result;
        }catch(RuntimeException unavailable){return null;}
    }
    public boolean current(ShaderPackOptions owner) {
        return owner.getOptionSet()==declarations && owner.getOptionValues()==values
            && values.getOptions()==declarations && booleans.equals(values.getBooleanValues())
            && strings.equals(values.getStringValues());
    }
}
