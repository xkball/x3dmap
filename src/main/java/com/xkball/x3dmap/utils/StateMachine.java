package com.xkball.x3dmap.utils;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import it.unimi.dsi.fastutil.objects.Object2BooleanFunction;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@NonNullByDefault
public final class StateMachine<S extends Enum<S>, O extends Enum<O>, T extends StateMachine.Target<S>> {
    
    private final Map<TransitionKey<S, O>, Transition<S, O, T>> transitions;
    
    private StateMachine(Map<TransitionKey<S, O>, Transition<S, O, T>> transitions) {
        this.transitions = Map.copyOf(transitions);
    }
    
    public static <S extends Enum<S>, O extends Enum<O>, T extends Target<S>> Builder<S, O, T> builder() {
        return new Builder<>();
    }
    
    public boolean fire(T target, O operation) {
        Transition<S, O, T> transition = findTransition(target, operation);
        if (transition == null) {
            return false;
        }
        var result = transition.execute(target);
        if (result) {
            target.setState(transition.targetState());
            return true;
        }
        return false;
    }
    
    public boolean canFire(T target, O operation) {
        return findTransition(target, operation) != null;
    }
    
    public @Nullable Transition<S, O, T> findTransition(T target, O operation) {
        return transitions.get(new TransitionKey<>(target.getState(), operation));
    }
    
    public interface Target<S extends Enum<S>> {
        
        S getState();
        
        void setState(S state);
    }
    
    public static class Transition<S extends Enum<S>, O extends Enum<O>, T extends Target<S>> {
        
        private final S source;
        private final O operation;
        private final S targetState;
        private final Function<T, Boolean> action;
        
        public Transition(S source, O operation, S targetState) {
            this(source, operation, targetState, (_) -> true);
        }
        
        public Transition(S source, O operation, S targetState, Function<T, Boolean> action) {
            this.source = source;
            this.operation = operation;
            this.targetState = targetState;
            this.action = action;
        }
        
        public S source() {
            return source;
        }
        
        public O operation() {
            return operation;
        }
        
        public S targetState() {
            return targetState;
        }
        
        public boolean execute(T target) {
            return action.apply(target);
        }
    }
    
    public static final class Builder<S extends Enum<S>, O extends Enum<O>, T extends Target<S>> {
        
        private final Map<TransitionKey<S, O>, Transition<S, O, T>> transitions = new HashMap<>();
        
        private Builder() {
        }
        
        public Builder<S, O, T> transition(S source, S targetState, O operation) {
            return transition(new Transition<>(source, operation, targetState));
        }
        
        public Builder<S, O, T> transition(S source, S targetState, O operation, Function<T, Boolean> action) {
            return transition(new Transition<>(source, operation, targetState, action));
        }
        
        public Builder<S, O, T> transition(Transition<S, O, T> transition) {
            TransitionKey<S, O> key = new TransitionKey<>(transition.source(), transition.operation());
            Transition<S, O, T> previous = transitions.putIfAbsent(key, transition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate transition for " + transition.source() + " and " + transition.operation());
            }
            return this;
        }
        
        public StateMachine<S, O, T> build() {
            return new StateMachine<>(transitions);
        }
    }
    
    private record TransitionKey<S extends Enum<S>, O extends Enum<O>>(S state, O operation) {
    }
}
