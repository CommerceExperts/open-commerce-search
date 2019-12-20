package de.cxp.ocs.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Offers the possibility to run a {@link Runnable} function, once in a
 * defined time frame.
 */
@Slf4j
public class OnceInAWhileRunner {

	private static final Cache<String, Timeout> CACHE = CacheBuilder.newBuilder()
			.maximumSize(10000)
			.build();

	/**
	 * Runs the {@link Runnable} with the same key only once in a specified time
	 * frame.
	 * 
	 * @param runnable
	 *        the runnable to run.
	 * @param key
	 *        the key which should be unique for the same runnable.
	 * @param unit
	 *        the {@link ChronoUnit} which describes the time frame. Please
	 *        refer to {@link Instant#plus(java.time.temporal.TemporalAmount)}
	 *        for a list of supported units.
	 * @param expires
	 *        the frame span for the specified unit.
	 */
	public static void runAgainAfter(final Runnable runnable, final String key, final ChronoUnit unit,
			final long expires) {
		Optional<Timeout> fromCache = getFromCache(runnable, key, unit, expires);
		if (fromCache.isPresent()) {
			if (fromCache.get().isExpired()) {
				CACHE.invalidate(key);
				runnable.run();
			}
		}
	}

	private static Optional<Timeout> getFromCache(final Runnable runnable, final String key, final ChronoUnit unit,
			final long expires) {
		try {
			Timeout timeout = CACHE.get(key, () -> computeCache(runnable, unit, expires));
			return Optional.ofNullable(timeout);
		}
		catch (ExecutionException e) {
			log.error("Error while loading cache value, because of", e);
		}
		return Optional.empty();
	}

	private static Timeout computeCache(final Runnable runnable, final ChronoUnit unit, final long expires) {
		runnable.run();
		return new Timeout(Instant.now(), unit, expires);
	}

	/**
	 * Simple timeout utility class.
	 */
	private static class Timeout {

		private Instant expires;

		/**
		 * Creates a new instance.
		 * 
		 * @param start
		 *        the start point in time from whom the timeout will be
		 *        measured.
		 * @param unit
		 *        the unit of the timeout value
		 * @param expires
		 *        the value when this timeout will be seen as timed out from the
		 *        start time.
		 */
		private Timeout(Instant start, ChronoUnit unit, long expires) {
			this.expires = start.plus(expires, unit);
		}

		/**
		 * Checks weather this timeout has expired or not.
		 * 
		 * @return <code>true</code> if the timeout is expired,
		 *         <code>false</code> otherwise.
		 */
		public boolean isExpired() {
			return Instant.now().isAfter(expires);
		}
	}
}
