package com.pp.shaderlod.integration;

import com.pp.shaderlod.lod.adapter.layout.AdapterLayout;
import com.pp.shaderlod.lod.adapter.layout.Dimensions;
import java.util.Objects;

/** Optional declaration/domain gate. Acceptance never certifies actual GL allocations or feedback. */
public record RuntimeLayoutRequirement(AdapterLayout layout, Dimensions minimumSurface,
                                       Dimensions maximumSurface) {
    public RuntimeLayoutRequirement {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(minimumSurface, "minimumSurface");
        Objects.requireNonNull(maximumSurface, "maximumSurface");
        if (minimumSurface.width() > maximumSurface.width()
                || minimumSurface.height() > maximumSurface.height()) {
            throw new IllegalArgumentException("Minimum rendered surface exceeds maximum");
        }
    }

    public boolean accepts(Dimensions surface) {
        return surface.width() >= minimumSurface.width() && surface.height() >= minimumSurface.height()
                && surface.width() <= maximumSurface.width() && surface.height() <= maximumSurface.height()
                && layout.validate(surface).valid();
    }
}
