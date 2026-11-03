package com.pp.shaderlod.tuning;
import java.util.Objects;
/** One independently measured goal with the existing finite Quick/Full segment limits. */
public record TuningRequest(TuningGoal goal,int budgetSeconds,boolean full) {
    public TuningRequest {
        Objects.requireNonNull(goal,"goal");
        budgetSeconds=budgetSeconds<=0?(full?3600:300):Math.max(60,Math.min(full?3600:900,budgetSeconds));
    }
    public static TuningRequest quick(TuningGoal goal){return new TuningRequest(goal,300,false);}
    public static TuningRequest full(TuningGoal goal){return new TuningRequest(goal,3600,true);}
}
