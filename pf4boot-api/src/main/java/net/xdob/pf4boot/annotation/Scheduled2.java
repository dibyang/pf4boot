package net.xdob.pf4boot.annotation;

import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Schedules2.class)
public @interface Scheduled2 {
	/**
	 * A special cron expression value that indicates a disabled trigger: {@value}.
	 * <p>This is primarily meant for use with <code>${...}</code> placeholders,
	 * allowing for external disabling of corresponding scheduled methods.
	 * @since 5.1
	 * @see ScheduledTaskRegistrar#CRON_DISABLED
	 */
	String CRON_DISABLED = ScheduledTaskRegistrar.CRON_DISABLED;


	/**
	 * A cron-like expression, extending the usual UN*X definition to include triggers
	 * on the second, minute, hour, day of month, month, and day of week.
	 * <p>For example, {@code "0 * * * * MON-FRI"} means once per minute on weekdays
	 * (at the top of the minute - the 0th second).
	 * <p>The fields read from left to right are interpreted as follows.
	 * <ul>
	 * <li>second</li>
	 * <li>minute</li>
	 * <li>hour</li>
	 * <li>day of month</li>
	 * <li>month</li>
	 * <li>day of week</li>
	 * </ul>
	 * <p>The special value {@link #CRON_DISABLED "-"} indicates a disabled cron
	 * trigger, primarily meant for externally specified values resolved by a
	 * <code>${...}</code> placeholder.
	 * @return an expression that can be parsed to a cron schedule
	 * @see org.springframework.scheduling.support.CronExpression#parse(String)
	 */
	String cron() default "";

	/**
	 * A time zone for which the cron expression will be resolved. By default, this
	 * attribute is the empty String (i.e. the scheduler's time zone will be used).
	 * @return a zone id accepted by {@link java.util.TimeZone#getTimeZone(String)},
	 * or an empty String to indicate the scheduler's default time zone
	 * @since 4.0
	 * @see org.springframework.scheduling.support.CronTrigger#CronTrigger(String, java.util.TimeZone)
	 * @see java.util.TimeZone
	 */
	String zone() default "";

	/**
	 * Execute the annotated method with a fixed period between the end of the
	 * last invocation and the start of the next.
	 * <p>The time unit is milliseconds by default but can be overridden via
	 * {@link #timeUnit}.
	 * @return the delay
	 */
	long fixedDelay() default -1;

	/**
	 * Execute the annotated method with a fixed period between the end of the
	 * last invocation and the start of the next.
	 * <p>The time unit is milliseconds by default but can be overridden via
	 * {@link #timeUnit}.
	 * <p>This attribute variant supports Spring-style "${...}" placeholders
	 * as well as SpEL expressions.
	 * @return the delay as a String value &mdash; for example, a placeholder
	 * or a {@link java.time.Duration#parse java.time.Duration} compliant value
	 * @since 3.2.2
	 * @see #fixedDelay()
	 */
	String fixedDelayString() default "";

	/**
	 * Execute the annotated method with a fixed period between invocations.
	 * <p>The time unit is milliseconds by default but can be overridden via
	 * {@link #timeUnit}.
	 * @return the period
	 */
	long fixedRate() default -1;

	/**
	 * Execute the annotated method with a fixed period between invocations.
	 * <p>The time unit is milliseconds by default but can be overridden via
	 * {@link #timeUnit}.
	 * <p>This attribute variant supports Spring-style "${...}" placeholders
	 * as well as SpEL expressions.
	 * @return the period as a String value &mdash; for example, a placeholder
	 * or a {@link java.time.Duration#parse java.time.Duration} compliant value
	 * @since 3.2.2
	 * @see #fixedRate()
	 */
	String fixedRateString() default "";

	/**
	 * Number of units of time to delay before the first execution of a
	 * {@link #fixedRate} or {@link #fixedDelay} task.
	 * <p>The time unit is milliseconds by default but can be overridden via
	 * {@link #timeUnit}.
	 * @return the initial
	 * @since 3.2
	 */
	long initialDelay() default -1;

	/**
	 * Number of units of time to delay before the first execution of a
	 * {@link #fixedRate} or {@link #fixedDelay} task.
	 * <p>The time unit is milliseconds by default but can be overridden via
	 * {@link #timeUnit}.
	 * <p>This attribute variant supports Spring-style "${...}" placeholders
	 * as well as SpEL expressions.
	 * @return the initial delay as a String value &mdash; for example, a placeholder
	 * or a {@link java.time.Duration#parse java.time.Duration} compliant value
	 * @since 3.2.2
	 * @see #initialDelay()
	 */
	String initialDelayString() default "";

	/**
	 * The {@link TimeUnit} to use for {@link #fixedDelay}, {@link #fixedDelayString},
	 * {@link #fixedRate}, {@link #fixedRateString}, {@link #initialDelay}, and
	 * {@link #initialDelayString}.
	 * <p>Defaults to {@link TimeUnit#MILLISECONDS}.
	 * <p>This attribute is ignored for {@linkplain #cron() cron expressions}
	 * and for {@link java.time.Duration} values supplied via {@link #fixedDelayString},
	 * {@link #fixedRateString}, or {@link #initialDelayString}.
	 * @return the {@code TimeUnit} to use
	 * @since 5.3.10
	 */
	TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

	String group() default "";
}
