#!/usr/bin/env python3
"""Certify that each actual public entry forwards to the fixture-executed implementation."""
import pathlib,subprocess
root=pathlib.Path(__file__).resolve().parent
javap=root.parents[1]/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/javap'
for loader,cls in [('fabric','com.pp.shaderlod.fabric.FabricLodSettings'),('neoforge','com.pp.shaderlod.ShaderLodSettings')]:
 jar=next((root/loader/'build/libs').glob('*.jar'))
 text=subprocess.check_output([str(javap),'-p','-c','-v','-classpath',str(jar),cls],text=True)
 (root/'evidence'/f'{loader}-apply-refusal-bytecode.txt').write_text(text)
 start=text.index('public static boolean apply(boolean, com.pp.shaderlod.LodPreset);')
 end=text.index('static boolean apply(boolean, com.pp.shaderlod.LodPreset,',start)
 entry=text[start:end]
 assert 'InvokeDynamic' in entry and 'Method apply:(ZLcom/pp/shaderlod/LodPreset;' in entry and '$Preflight;' in entry
 assert 'REF_invokeStatic com/pp/shaderlod/integration/RendererBackend.requireApplySupported:()V' in text
 body=text[end:text.index('public static java.lang.String failureMessage()',end)]
 gate=body.index('$Preflight.run:()V')
 state=body.index('FabricLodConfig.snapshot:') if loader=='fabric' else body.index('ShaderLodConfig.ENABLED:')
 factory=body.index('java/util/function/Supplier.get:')
 prepare=body.index('PackSelectionTransaction.prepare:')
 assert gate<state<factory<prepare,(loader,gate,state,factory,prepare)
 refusal=body.index('KnownRuntimeBoundary.isRefusal:')
 restore=body.index('FabricLodConfig.replace:',refusal) if loader=='fabric' else body.index('ShaderLodConfig.ENABLED:',refusal)
 assert 'ireturn' in body[refusal:restore],loader
 print(loader+': actual public Apply delegates required preflight; preflight precedes config/factory/prepare; typed refusal returns before restoration')
