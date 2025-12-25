package net.xdob.pf4boot.util;

import java.util.concurrent.TimeUnit;
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

  public static AutoCloseableLock acquire(final Lock lock, int timeout, TimeUnit unit) {
    return acquire(lock, timeout,unit, null);
  }

  public static AutoCloseableLock acquire(final Lock lock, int timeout, TimeUnit unit, Runnable preUnlock) {
    try {
      if(lock.tryLock(timeout, unit)) {
        return new AutoCloseableLock(lock, preUnlock);
      }else{
        return new AutoCloseableLock(null, preUnlock);
      }
    } catch (InterruptedException e) {
      return new AutoCloseableLock(null, preUnlock);
    }
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
      if(underlying!=null) {
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
}
