package it.adrian.code.platform;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Opaque handle to a remote process opened by a {@link NativeAccess} backend.
 * <p>
 * A session is reference-counted: every {@link it.adrian.code.memory.Pointer}
 * that uses it bumps the count on creation and decrements it on
 * {@code close()}. The underlying OS handle / file descriptor is only released
 * when the count reaches zero, so it is safe to {@code copy()} a {@code Pointer}
 * and {@code close()} either the original or the copy independently.
 */
public abstract class ProcessSession {

    private final AtomicInteger refCount = new AtomicInteger(1);
    public final int pid;

    protected ProcessSession(int pid) {
        this.pid = pid;
    }

    /** Increment the reference count and return {@code this} for chaining. */
    public ProcessSession retain() {
        refCount.incrementAndGet();
        return this;
    }

    /**
     * Decrement the reference count.
     * @return the new count; {@code 0} means the caller should release the
     *         underlying OS resource.
     */
    public int release() {
        int updated = refCount.decrementAndGet();
        if (updated < 0) {
            refCount.set(0);
            return 0;
        }
        return updated;
    }

    public int referenceCount() {
        return refCount.get();
    }
}
