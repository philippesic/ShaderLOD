package com.pp.shaderlod.dev.timing.costmap;
public final class ScopeContextTest {
    public static void main(String[] args) {
        check(ScopeContext.of("dh/world/opaque","main",false),"main","world","opaque");
        check(ScopeContext.of("dh/shadow/translucent","deferred",false),"deferred","shadow","translucent");
        check(ScopeContext.of("dh-total/world/deferred","unknown",false),"deferred","world","unknown");
        check(ScopeContext.of("dh-render-list/shadow","main",false),"main","shadow","unknown");
        check(ScopeContext.of("dh/world/opaque","main",true),"not_applicable","world","opaque");
        check(ScopeContext.of("iris/composite","main",false),"not_applicable","unknown","unknown");
        var boundary=new CohortBoundary();Object graph=new Object();
        if(!boundary.observe(graph,"DH enabled not ready")||boundary.graphGeneration()!=1)throw new AssertionError();
        if(boundary.observe(graph,"DH enabled not ready"))throw new AssertionError("spurious boundary");
        if(!boundary.observe(graph,"DH enabled ready")||boundary.graphGeneration()!=1)throw new AssertionError("readiness must end cohort, keep graph");
        if(!boundary.observe(graph,"DH disabled")||boundary.graphGeneration()!=1)throw new AssertionError("settings boundary");
        if(!boundary.observe(new Object(),"DH disabled")||boundary.graphGeneration()!=2)throw new AssertionError("reload must change graph");
        if(!boundary.observe(null,"disconnected"))throw new AssertionError("disconnect");
        System.out.println("Physical DH context checks passed");
    }
    private static void check(ScopeContext c,String stage,String world,String opacity){
        if(!c.equals(new ScopeContext(stage,world,opacity)))throw new AssertionError(c);
    }
}
