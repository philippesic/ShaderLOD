package com.pp.shaderlod.dev.timing.costmap;

/** Physical names from pinned timing mixins. Never assigns a semantic feature to a mixed pass. */
public record ScopeContext(String dhStage,String worldShadow,String opacity) {
    public static ScopeContext of(String name,String enclosingStage,boolean absent) {
        boolean dh=name.startsWith("dh/")||name.startsWith("dh-total/")||name.startsWith("dh-render-list/");
        String stage=absent?"not_applicable":name.startsWith("dh-total/")?name.substring(name.lastIndexOf('/')+1):dh?enclosingStage:"not_applicable";
        String world=name.contains("/shadow")?"shadow":name.contains("/world")?"world":"unknown";
        String opacity=name.endsWith("/opaque")||name.endsWith("/solid")?"opaque":name.endsWith("/translucent")?"translucent":"unknown";
        return new ScopeContext(stage,world,opacity);
    }
}
