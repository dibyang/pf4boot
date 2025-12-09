package net.xdob.pf4boot.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * Wrap a lock with the {@link AutoCloseable} interface
 * so that the {@link #close()} method will unlock the lock.
 */
public final class AutoCloseableLock implements AutoCloseable {
  /**
   * Acquire the given lock and then wrap it with {@link AutoCloseableLock}
   * so that the given lock can be released by calling {@link #close()},
   * or by using a {@code try}-with-resources statement as shown below.
   *
   * <pre> {@code
   * try(AutoCloseableLock acl = AutoCloseableLock.acquire(lock)) {
   *   ...
   * }}</pre>
   */
  public static AutoCloseableLock acquire(final Lock lock) {
    return acquire(lock, null);
  }

  @SuppressWarnings("java:S2222") // Locks should be release by calling {@link #close()}
  public static AutoCloseableLock acquire(final Lock lock, Runnable preUnlock) {
    lock.lock();
    return new AutoCloseableLock(lock, preUnlock);
  }

  private final Lock underlying;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Runnable preUnlock;

  private AutoCloseableLock(Lock underlying, Runnable preUnlock) {
    this.underlying = underlying;
    this.preUnlock = preUnlock;
  }

  /** Unlock the underlying lock.  This method is idempotent. */
  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      try {
        if (preUnlock != null) {
          preUnlock.run();
        }
      } finally {
        underlying.unlock();
      }
    }
  }
}
