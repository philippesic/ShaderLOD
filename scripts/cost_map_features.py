"""Reviewed mixed feature labels for the four pinned original archives."""
SEEDS = {
 'reimagined-r5.9.3': {'deferred1':['AO','atmosphere','materials'], 'composite':['clouds','reflections','materials'], 'composite1':['volumetric-light','shadow-consumer','water-fog','refraction','reflection-blur'], 'composite4':['bloom-tiles'], 'composite5':['bloom-combine'], 'composite6':['TAA'], 'composite7':['conditional-FXAA']},
 'unbound-r5.9.3': {'deferred1':['AO','atmosphere','materials'], 'composite':['clouds','reflections','materials'], 'composite1':['volumetric-light','shadow-consumer','water-fog','refraction','reflection-blur'], 'composite4':['bloom-tiles'], 'composite5':['bloom-combine'], 'composite6':['TAA'], 'composite7':['conditional-FXAA']},
 'bsl-10.1.8': {'deferred':['AO-producer'], 'deferred1':['AO-consumer','atmosphere','clouds'], 'composite':['light-shafts','water-fog'], 'composite4':['bloom-tiles'], 'composite5':['bloom','post'], 'composite6':['FXAA'], 'composite7':['conditional-TAA']},
 'makeup-9.5g': {'deferred':['AO','volumetric-clouds'], 'composite':['volumetric-light','bloom-source'], 'composite1':['bloom','blur'], 'composite2':['TAA','motion-blur'], 'final':['tone-mapping','optional-final-effects']}}

# Dedicated DH raster programs and shared consumers are distinct, nonadditive labels.
for _revision, _programs in SEEDS.items():
    _programs.update(dh_terrain=['DH-terrain','materials','lighting','fog'],dh_water=['DH-translucency','water','lighting','fog'])
    if _revision.startswith(('reimagined','unbound')):
        _programs['composite3']=['fog','DH-depth-consumer','mixed-post']
