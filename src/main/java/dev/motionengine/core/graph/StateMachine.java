package dev.motionengine.core.graph;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Generic animation state machine.
 *
 * <p>States are enum constants. Transitions are evaluated in registration order (first match wins),
 * "any-state" transitions are evaluated before the current state's own transitions. Each transition
 * carries a blend time which the owner uses to cross-fade layer weights; because weights are moved
 * toward the new targets from wherever they currently are, a transition that interrupts another
 * blend continues smoothly instead of popping (interruptible transitions).
 *
 * <p>New states and transitions can be added without touching existing ones — this is the extension
 * point required by the spec ("adicionar novas animações sem reescrever toda a arquitetura").
 */
public final class StateMachine<S extends Enum<S>, C> {

    public static final class Transition<S, C> {
        final S from; // null = any state
        final S to;
        final Predicate<C> condition;
        final float minTimeInState;
        final float blendTime;
        final String name;

        Transition(String name, S from, S to, Predicate<C> condition, float minTimeInState, float blendTime) {
            this.name = name;
            this.from = from;
            this.to = to;
            this.condition = condition;
            this.minTimeInState = minTimeInState;
            this.blendTime = blendTime;
        }
    }

    public interface Listener<S> {
        void onTransition(S from, S to, float blendTime, String transitionName);
    }

    private final Map<S, List<Transition<S, C>>> byState;
    private final List<Transition<S, C>> anyState = new ArrayList<>();
    private S current;
    private S previous;
    private float timeInState;
    private float lastBlendTime;
    private String lastTransition = "init";
    private Listener<S> listener;

    public StateMachine(Class<S> type, S initial) {
        this.byState = new EnumMap<>(type);
        this.current = initial;
        this.previous = initial;
    }

    public StateMachine<S, C> add(String name, S from, S to, Predicate<C> cond, float minTime, float blend) {
        Transition<S, C> t = new Transition<>(name, from, to, cond, minTime, blend);
        if (from == null) anyState.add(t);
        else byState.computeIfAbsent(from, k -> new ArrayList<>()).add(t);
        return this;
    }

    public StateMachine<S, C> addAny(String name, S to, Predicate<C> cond, float blend) {
        return add(name, null, to, cond, 0f, blend);
    }

    public void setListener(Listener<S> listener) {
        this.listener = listener;
    }

    /** Evaluates transitions and advances time. Returns true if the state changed. */
    public boolean update(C ctx, float dt) {
        timeInState += dt;
        Transition<S, C> fired = null;
        for (Transition<S, C> t : anyState) {
            if (t.to != current && t.condition.test(ctx)) { fired = t; break; }
        }
        if (fired == null) {
            List<Transition<S, C>> list = byState.get(current);
            if (list != null) {
                for (Transition<S, C> t : list) {
                    if (timeInState >= t.minTimeInState && t.condition.test(ctx)) { fired = t; break; }
                }
            }
        }
        if (fired == null) return false;
        force(fired.to, fired.blendTime, fired.name);
        return true;
    }

    public void force(S to, float blendTime, String reason) {
        previous = current;
        current = to;
        timeInState = 0f;
        lastBlendTime = blendTime;
        lastTransition = reason;
        if (listener != null) listener.onTransition(previous, current, blendTime, reason);
    }

    public S current() { return current; }
    public S previous() { return previous; }
    public float timeInState() { return timeInState; }
    public float lastBlendTime() { return lastBlendTime; }
    public String lastTransition() { return lastTransition; }
}
