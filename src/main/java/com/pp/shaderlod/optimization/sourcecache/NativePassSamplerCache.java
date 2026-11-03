package com.pp.shaderlod.optimization.sourcecache;

/** One synchronous native invocation. Poison survives restoration and nested calls. */
public final class NativePassSamplerCache<K,V> {
    private final ThreadLocal<State> current=new ThreadLocal<>();
    private final class State {
        int depth=1;boolean armed,poisoned,revoking;K key;V value;Runnable permission;
    }
    public final class Scope implements AutoCloseable {
        private final State state;private boolean closed;
        private Scope(State state){this.state=state;}
        public void close(){
            if(closed||state==null)return;closed=true;
            if(current.get()!=state)return;
            if(--state.depth==0){try{poison(state);}finally{current.remove();}}else poison(state);
        }
    }
    public Scope open(boolean eligible){
        var active=current.get();
        if(active!=null){poison(active);active.depth++;return new Scope(active);}
        if(!eligible)return new Scope(null);
        var state=new State();current.set(state);return new Scope(state);
    }
    public void arm(){var state=current.get();if(state!=null&&state.depth==1&&!state.poisoned)state.armed=true;}
    private void poison(State state){
        state.poisoned=true;state.key=null;state.value=null;
        var permission=state.permission;state.permission=null;
        if(permission!=null&&!state.revoking){state.revoking=true;try{permission.run();}finally{state.revoking=false;}}
    }
    /** Initial begin/setup is expected. Only an armed invocation can lend observations. */
    public void invalidate(){var state=current.get();if(state!=null&&(state.armed||state.permission!=null))poison(state);}
    public V reuse(K key){
        var state=current.get();if(state==null||!state.armed||state.poisoned||state.depth!=1)return null;
        if(state.key==null)return null;
        if(!state.key.equals(key)){poison(state);return null;}
        return state.value;
    }
    public void remember(K key,V value){
        var state=current.get();if(state!=null&&state.armed&&!state.poisoned&&state.depth==1){state.key=key;state.value=value;}
    }
    public void granted(Runnable revoke){var state=current.get();if(state!=null)state.permission=revoke;}
    public void revoked(){var state=current.get();if(state!=null)state.permission=null;}
    public boolean active(){return current.get()!=null;}
}
