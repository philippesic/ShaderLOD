package com.pp.shaderlod.tuning;

import java.util.*;

/** A one-use held-out allocation belongs to one frozen profile, even on failure/cancellation. */
final class TuningConfirmation {
    private final String heldOutCase;
    private final Set<String> searched=new LinkedHashSet<>();
    private String frozenProfile;
    private boolean consumed,passed,completed;
    TuningConfirmation(String heldOutCase){this.heldOutCase=Objects.requireNonNull(heldOutCase);}
    void searched(String caseId){
        if(heldOutCase.equals(caseId))throw new IllegalStateException("Held-out case cannot enter adaptive search");
        if(frozenProfile!=null)throw new IllegalStateException("Search cannot resume after profile freeze");
        searched.add(caseId);
    }
    void freeze(String profile){
        if(frozenProfile!=null)throw new IllegalStateException("Profile already frozen; allocate a new confirmation run");
        frozenProfile=Objects.requireNonNull(profile);
    }
    void consume(String profile){
        if(frozenProfile==null||!frozenProfile.equals(profile)||consumed)
            throw new IllegalStateException("Holdout unavailable, reused, or frozen profile changed");
        consumed=true;
    }
    void complete(boolean value){if(!consumed||completed)throw new IllegalStateException("Holdout has not been allocated or already completed");completed=true;passed=value;}
    boolean passed(){return passed;}
    boolean consumed(){return consumed;}
    boolean frozen(){return frozenProfile!=null;}
    String heldOutCase(){return heldOutCase;}
    List<String> searchedCases(){return List.copyOf(searched);}
    /** Timing positivity alone never authorizes a saved shipping profile. */
    static boolean maySave(boolean timing,boolean holdout,boolean nullControl,boolean knownEffect,
                           boolean readiness,boolean quality,boolean compatibility,boolean restored){
        return timing&&holdout&&nullControl&&knownEffect&&readiness&&quality&&compatibility&&restored;
    }
}
