package com.pp.shaderlod.dev.timing.costmap;

import java.util.Objects;

/** Pipeline object identity defines graph lifetime; settings/readiness define narrower cohorts. */
public final class CohortBoundary {
    private Object pipeline;
    private String context;
    private int graph;
    public boolean observe(Object next,String nextContext) {
        boolean graphChanged=next!=pipeline;
        if(!graphChanged&&Objects.equals(context,nextContext))return false;
        if(graphChanged)graph++;
        pipeline=next;context=nextContext;return true;
    }
    public int graphGeneration(){return graph;}
}
