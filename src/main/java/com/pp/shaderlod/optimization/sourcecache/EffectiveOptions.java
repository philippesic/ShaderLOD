package com.pp.shaderlod.optimization.sourcecache;

import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.*;

/** Constructor-captured effective options. Raw overrides are checked live; a mutation refuses until reload. */
public final class EffectiveOptions {
    private EffectiveOptions() {}
    private record Input(OptionSet declarations, MutableOptionValues values, Map<String,Boolean> booleans,
            Map<String,String> strings, Map<String,String> effective) {
        boolean current(OptionSet actual,OptionValues raw){return actual==declarations&&raw==values
            &&values.getOptions()==declarations&&booleans.equals(values.getBooleanValues())&&strings.equals(values.getStringValues());}
    }
    // Values do not retain their ShaderPackOptions key.
    private static final Map<ShaderPackOptions,Input> INPUTS=new WeakHashMap<>();
    private static final Map<OptionValues,java.lang.ref.WeakReference<Input>> VALUES=new WeakHashMap<>();
    public static synchronized void capture(ShaderPackOptions owner){
        INPUTS.remove(owner);
        if(owner.getOptionValues().getClass()!=MutableOptionValues.class
                ||!RawOptionOverrides.immutableDefaults(owner.getOptionSet()))return;
        var declarations=owner.getOptionSet();var values=(MutableOptionValues)owner.getOptionValues();
        if(values.getOptions()!=declarations)return;
        var effective=new TreeMap<String,String>();
        declarations.getBooleanOptions().forEach((k,v)->effective.put(k,Boolean.toString(values.getBooleanValueOrDefault(k))));
        declarations.getStringOptions().forEach((k,v)->effective.put(k,values.getStringValueOrDefault(k)));
        var input=new Input(declarations,values,Map.copyOf(values.getBooleanValues()),Map.copyOf(values.getStringValues()),Map.copyOf(effective));
        INPUTS.put(owner,input);VALUES.put(values,new java.lang.ref.WeakReference<>(input));
    }
    public static synchronized Map<String,String> current(ShaderPackOptions owner){
        var input=INPUTS.get(owner);return input!=null&&input.current(owner.getOptionSet(),owner.getOptionValues())?input.effective():Map.of();
    }
    public static synchronized Map<String,String> current(OptionSet declarations,OptionValues values){
        var reference=VALUES.get(values);var input=reference==null?null:reference.get();
        return input!=null&&input.current(declarations,values)?input.effective():Map.of();
    }
}
