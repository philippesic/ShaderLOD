#!/usr/bin/env python3
"""Inspect immutable Minecraft26.2 client API; does not load a renderer."""
import hashlib,json,pathlib,subprocess
root=pathlib.Path(__file__).resolve().parent
repository=root.parent.parent
java=repository/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/javap'
client=pathlib.Path.home()/'.gradle/caches/fabric-loom/26.2/minecraft-client.jar'
metadata=json.loads((repository/'dependencies/upstream/ports-26x/mojang-26.2.json').read_text())
assert hashlib.sha1(client.read_bytes()).hexdigest()==metadata['downloads']['client']['sha1']
classes=['net.minecraft.client.Minecraft','net.minecraft.client.gui.Gui','net.minecraft.client.renderer.GameRenderer','com.mojang.blaze3d.systems.GpuDeviceBackend','com.mojang.blaze3d.systems.DeviceInfo']
text=subprocess.check_output([str(java),'-p','-s','-classpath',str(client),*classes],text=True)
for signature in ['public final net.minecraft.client.gui.Gui gui','public final net.minecraft.client.renderer.GameRenderer gameRenderer','public net.minecraft.client.gui.screens.Screen screen()','public void setScreen(net.minecraft.client.gui.screens.Screen)','public com.mojang.blaze3d.pipeline.RenderTarget mainRenderTarget()','public abstract com.mojang.blaze3d.systems.DeviceInfo getDeviceInfo()','public java.lang.String backendName()']:
 assert signature in text,signature
(root/'evidence/minecraft-client-api.txt').write_text(text)
bytecode=subprocess.check_output([str(java),'-p','-c','-classpath',str(client),'com.mojang.blaze3d.opengl.GlHeuristics'],text=True)
assert 'String OpenGL' in bytecode
(root/'evidence/minecraft-opengl-deviceinfo.txt').write_text(bytecode)
(root/'evidence/minecraft-client-pin.json').write_text(json.dumps(dict(path=str(client),sha1=hashlib.sha1(client.read_bytes()).hexdigest(),sha256=hashlib.sha256(client.read_bytes()).hexdigest(),runtimeVerified=False),indent=2)+'\n')
print('Exact official Minecraft26.2 SHA1 and GUI/target/device-info API + OpenGL backend-name producer passed')
