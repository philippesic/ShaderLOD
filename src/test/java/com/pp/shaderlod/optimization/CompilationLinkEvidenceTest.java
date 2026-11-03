package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.sourcecache.CompilationLinkEvidence;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompilationLinkEvidenceTest {
    @Test void onlyExactCompiledMapCanCarryUnchangedSourceEvidenceToLinking() {
        var chain=new CompilationLinkEvidence<Object>();var certificate=new Object();
        var text=new HashMap<>(Map.of("vertex","verified vertex","fragment","verified fragment"));
        var shaders=new HashMap<String,Object>();shaders.put("vertex",new Object());shaders.put("fragment",new Object());
        assertNull(chain.linked(shaders));
        chain.transformed(text,certificate);chain.compiled(new HashMap<>(text),shaders);
        assertNull(chain.linked(shaders),"equal-but-foreign source map is not a compile receipt");
        chain.compiled(text,shaders);assertSame(certificate,chain.linked(shaders));
        assertNull(chain.linked(new HashMap<>(shaders)),"equal-but-foreign shader map cannot inherit ownership");
        shaders.put("fragment",new Object());assertNull(chain.linked(shaders));
    }
    @Test void changedTextMissingStagesAndFailedCompilationCannotPublishEvidence() {
        var chain=new CompilationLinkEvidence<String>();
        var text=new HashMap<>(Map.of("vertex","v","fragment","f"));
        chain.transformed(text,"certificate");text.put("fragment","changed");
        var shaders=Map.of("vertex",new Object(),"fragment",new Object());
        chain.compiled(text,shaders);assertNull(chain.linked(shaders));
        chain.transformed(text,"fresh");
        var missing=Map.of("vertex",new Object());chain.compiled(text,missing);assertNull(chain.linked(missing));
        chain.compiled(text,null);assertNull(chain.linked(shaders));
        chain.compiled(text,shaders);assertEquals("fresh",chain.linked(shaders));
        chain.clear();assertNull(chain.linked(shaders));
    }
    @Test void reusedMapAndRevokedPreparationCannotRetainAnEarlierReceipt() {
        var chain=new CompilationLinkEvidence<String>();
        var text=new HashMap<>(Map.of("fragment","f"));var shaders=Map.of("fragment",new Object());
        chain.transformed(text,"old");chain.compiled(text,shaders);assertEquals("old",chain.linked(shaders));
        chain.transformed(text,null);chain.compiled(text,shaders);assertNull(chain.linked(shaders));
        chain.transformed(text,"new");chain.compiled(text,shaders);assertEquals("new",chain.linked(shaders));
    }
    @Test void irisAbsentTextStagesAreOmittedByActualCompilationBoundary() {
        var text=new EnumMap<net.irisshaders.iris.pipeline.transform.PatchShaderType,String>(net.irisshaders.iris.pipeline.transform.PatchShaderType.class);
        for(var stage:net.irisshaders.iris.pipeline.transform.PatchShaderType.values())text.put(stage,null);
        text.put(net.irisshaders.iris.pipeline.transform.PatchShaderType.VERTEX,"v");
        text.put(net.irisshaders.iris.pipeline.transform.PatchShaderType.FRAGMENT,"f");
        var shaders=new EnumMap<net.irisshaders.iris.pipeline.transform.PatchShaderType,Object>(net.irisshaders.iris.pipeline.transform.PatchShaderType.class);
        shaders.put(net.irisshaders.iris.pipeline.transform.PatchShaderType.VERTEX,new Object());
        shaders.put(net.irisshaders.iris.pipeline.transform.PatchShaderType.FRAGMENT,new Object());
        var chain=new CompilationLinkEvidence<String>();chain.transformed(text,"actual-null-stage-layout");
        chain.compiled(text,shaders);assertEquals("actual-null-stage-layout",chain.linked(shaders));
        text.put(net.irisshaders.iris.pipeline.transform.PatchShaderType.GEOMETRY,"unexpected");
        chain.compiled(text,shaders);assertNull(chain.linked(shaders),"absent-stage mutation revokes the compile join");
    }

}
