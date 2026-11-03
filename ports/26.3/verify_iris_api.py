#!/usr/bin/env python3
"""Check exact shipped Iris class descriptors without loading Minecraft or OpenGL."""
import pathlib, subprocess
root = pathlib.Path(__file__).resolve().parent
out = root / 'evidence'; out.mkdir(exist_ok=True)
for loader in ('fabric', 'neoforge'):
    jar = root / 'runtime' / ('iris-' + loader + '-1.11.7+mc26.3.jar')
    classes = ['net.irisshaders.iris.uniforms.CommonUniforms', 'net.irisshaders.iris.pipeline.IrisRenderingPipeline',
               'net.irisshaders.iris.mixinterface.GpuTextureInterface', 'net.irisshaders.iris.uniforms.FrameUpdateNotifier',
               'net.irisshaders.iris.uniforms.SystemTimeUniforms', 'net.irisshaders.iris.mixin.GpuDeviceAccessor']
    text = subprocess.check_output([str(pathlib.Path(__file__).resolve().parents[2] / 'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/javap'), '-p', '-s', '-classpath', str(jar), *classes], text=True)
    (out / ('iris-' + loader + '-api.txt')).write_text(text)
    required = ['generalCommonUniforms(net.irisshaders.iris.gl.uniform.UniformHolder, net.irisshaders.iris.uniforms.FrameUpdateNotifier, net.irisshaders.iris.shaderpack.properties.PackDirectives)',
                '(Lnet/irisshaders/iris/gl/uniform/UniformHolder;Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)V',
                'private final net.irisshaders.iris.uniforms.FrameUpdateNotifier updateNotifier',
                'public void finalizeLevelRendering()', 'public default int iris$getGlId()', 'public void addListener(java.lang.Runnable)',
                'COUNTER', 'com.mojang.renderpearl.backend.api.GpuDeviceBackend getBackend()']
    for signature in required:
        if signature not in text: raise SystemExit('Missing pinned signature: ' + loader + ': ' + signature)
    bytecode = subprocess.check_output([str(pathlib.Path(__file__).resolve().parents[2] / 'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/javap'), '-p', '-c', '-classpath', str(jar), classes[1]], text=True)
    (out / ('iris-' + loader + '-pipeline-bytecode.txt')).write_text(bytecode)
    start = bytecode.index('public void finalizeLevelRendering();')
    body = bytecode[start:bytecode.index('public void finalizeGameRendering();', start)]
    composite = body.index('CompositeRenderer.renderAll:()V'); final = body.index('FinalPassRenderer.renderFinalPass:()V')
    if not composite < final < body.rindex('return'): raise SystemExit('World completion ordering changed: ' + loader)
    print(loader + ': uniform injection descriptor, final notifier, GL texture ID, listener, counter and normal completion order passed')

    compiler = subprocess.check_output([str(root.parents[1] / 'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/javap'), '-p', '-c', '-classpath', str(jar), 'net.irisshaders.iris.gl.shader.GlShader', 'net.irisshaders.iris.gl.IrisRenderSystem', 'net.irisshaders.iris.uniforms.ViewportUniforms'], text=True)
    (out / ('iris-' + loader + '-renderer-bytecode.txt')).write_text(compiler)
    for token in ('GlStateManager.glCompileShader', 'com/mojang/renderpearl/backend/opengl/GlDevice', 'GameRenderer.mainRenderTarget'):
        if token not in compiler: raise SystemExit('Missing exact renderer evidence: ' + loader + ': ' + token)
    print(loader + ': actual Iris compiler remains GL, backend cast is Renderpearl GL, viewport uses GameRenderer main target')
