package com.pp.shaderlod.lod.adapter.layout;

import java.util.HashSet;
import java.util.List;

/** Immutable adapter-supplied declaration. Construction and validation certify no source graph. */
public record AdapterLayout(List<TargetDeclaration> targets, List<PassDeclaration> passes) {
    public AdapterLayout {
        targets = List.copyOf(targets);
        passes = List.copyOf(passes);
        if (targets.isEmpty() || passes.isEmpty()) {
            throw new IllegalArgumentException("Layout needs targets and passes");
        }
        var targetIds = new HashSet<String>();
        for (var target : targets) {
            if (!targetIds.add(target.id())) throw new IllegalArgumentException("Duplicate target: " + target.id());
        }
        var passIds = new HashSet<String>();
        for (var pass : passes) {
            if (!passIds.add(pass.id())) throw new IllegalArgumentException("Duplicate pass: " + pass.id());
        }
    }

    public LayoutValidation validate(Dimensions renderedSurface) {
        return LayoutValidation.validate(this, renderedSurface);
    }
}
