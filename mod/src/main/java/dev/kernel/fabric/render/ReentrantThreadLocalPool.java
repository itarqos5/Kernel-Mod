package dev.kernel.fabric.render;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reuses one resettable value per thread while preserving correctness during nested calls.
 *
 * <p>A nested acquisition receives a fresh value rather than the primary value currently in use. If a caller exits
 * exceptionally without releasing its lease, later calls conservatively keep receiving fresh values instead of
 * risking shared mutable state.</p>
 */
public final class ReentrantThreadLocalPool<T> {
    private final Supplier<? extends T> factory;
    private final Consumer<? super T> reset;
    private final ThreadLocal<State<T>> local;

    public ReentrantThreadLocalPool(Supplier<? extends T> factory, Consumer<? super T> reset) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.reset = Objects.requireNonNull(reset, "reset");
        this.local = ThreadLocal.withInitial(this::createState);
    }

    public T acquire() {
        State<T> state = this.local.get();
        T value = state.depth++ == 0 ? state.primary : this.createValue();
        this.reset.accept(value);
        return value;
    }

    public void release() {
        State<T> state = this.local.get();
        if (state.depth == 0) {
            throw new IllegalStateException("Cannot release a render-storage value that is not acquired");
        }
        state.depth--;
    }

    private State<T> createState() {
        return new State<>(this.createValue());
    }

    private T createValue() {
        return Objects.requireNonNull(this.factory.get(), "factory returned null");
    }

    private static final class State<T> {
        private final T primary;
        private int depth;

        private State(T primary) {
            this.primary = primary;
        }
    }
}
