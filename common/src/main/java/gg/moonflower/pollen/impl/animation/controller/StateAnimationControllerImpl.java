package gg.moonflower.pollen.impl.animation.controller;

import gg.moonflower.pinwheel.api.animation.PlayingAnimation;
import gg.moonflower.pollen.api.animation.v1.controller.AnimationStateListener;
import gg.moonflower.pollen.api.animation.v1.controller.SerializableAnimationController;
import gg.moonflower.pollen.api.animation.v1.controller.StateAnimationController;
import gg.moonflower.pollen.api.animation.v1.state.AnimationState;
import io.github.ocelot.molangcompiler.api.MolangRuntime;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@ApiStatus.Internal
public class StateAnimationControllerImpl extends AnimationControllerImpl implements StateAnimationController, SerializableAnimationController {

    protected final AnimationState[] states;
    protected final IntSet playingStates;
    protected boolean dirty;

    private final IntSet removedStates;
    private final int[] stateTimes;

    private final Set<AnimationStateListener> listeners;

    public StateAnimationControllerImpl(AnimationState[] states, MolangRuntime.Builder builder) {
        super(builder);
        this.states = states;
        this.playingStates = new IntArraySet(states.length);
        this.dirty = false;

        this.removedStates = new IntOpenHashSet();
        this.stateTimes = new int[states.length];

        this.listeners = new ObjectArraySet<>();
    }

    private int getId(AnimationState state) {
        int id = ArrayUtils.indexOf(this.states, state);
        if (id == -1) {
            LOGGER.error("Unknown animation state: {}", state);
            return -1;
        }

        return id;
    }

    @Override
    public void tick() {
        for (int id : this.playingStates) {
            int tick = this.stateTimes[id];

            if (tick >= this.states[id].tickDuration()) {
                this.removedStates.add(id);
            }

            this.stateTimes[id]++;
        }

        this.removedStates.forEach(id -> this.stopAnimations(this.states[id]));
        this.removedStates.clear();

        // Update animation times
        super.tick();
    }

    @Override
    public void clearAnimations() {
        for (int stateId : this.playingStates) {
            this.listeners.forEach(listener -> listener.onAnimationStop(this.states[stateId]));
        }
        this.playingStates.clear();
    }

    @Override
    public boolean startAnimations(AnimationState animation) {
        if (AnimationState.EMPTY.equals(animation)) {
            if (!this.playingStates.isEmpty()) {
                this.clearAnimations();
                return true;
            }
            return false;
        }

        int id = this.getId(animation);
        if (id == -1) {
            return false;
        }

        if (this.playingStates.add(id)) {
            this.dirty = true;
            this.stateTimes[id] = 0;
            this.listeners.forEach(listener -> listener.onAnimationStart(animation));
            return true;
        }

        return false;
    }

    @Override
    public boolean stopAnimations(AnimationState animation) {
        if (AnimationState.EMPTY.equals(animation)) {
            return false;
        }

        int id = this.getId(animation);
        if (id == -1) {
            return false;
        }

        if (this.playingStates.remove(id)) {
            this.dirty = true;
            this.listeners.forEach(listener -> listener.onAnimationStop(animation));
            if (this.playingStates.isEmpty()) {
                this.listeners.forEach(AnimationStateListener::onAnimationsComplete);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean isAnimationPlaying(AnimationState animation) {
        return this.playingStates.contains(this.getId(animation));
    }

    @Override
    public Collection<AnimationState> getPlayingStates() {
        return this.playingStates.intStream().mapToObj(i -> this.states[i]).collect(Collectors.toSet());
    }

    @Override
    public void addListener(AnimationStateListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(AnimationStateListener listener) {
        this.listeners.remove(listener);
    }

    @Override
    public Collection<PlayingAnimation> getPlayingAnimations() {
        return Collections.emptySet();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        int[] states = this.playingStates.toIntArray();
        buf.writeVarIntArray(states);
    }

    @Override
    public void readFromNetwork(FriendlyByteBuf buf) {
        int[] states = buf.readVarIntArray();

        for (int state : states) {
            // If the local playing states don't have the new state, then it just started playing
            if (!this.playingStates.remove(state)) {
                this.stateTimes[state] = 0;
                this.listeners.forEach(listener -> listener.onAnimationStart(this.states[state]));
            }
        }

        // All states that haven't been removed are no longer playing on the server, so stop them
        for (int state : this.playingStates) {
            this.listeners.forEach(listener -> listener.onAnimationStop(this.states[state]));
        }

        // Sync states with server
        this.playingStates.clear();
        for (int state : states) {
            this.playingStates.add(state);
        }

        if (states.length == 0) {
            this.listeners.forEach(AnimationStateListener::onAnimationsComplete);
        }
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
