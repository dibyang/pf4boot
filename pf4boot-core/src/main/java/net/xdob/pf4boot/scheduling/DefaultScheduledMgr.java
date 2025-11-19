package net.xdob.pf4boot.scheduling;

import net.xdob.pf4boot.Pf4bootPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.scheduling.config.*;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultScheduledMgr implements ScheduledMgr {
	static final Logger logger = LoggerFactory.getLogger(DefaultScheduledMgr.class);
	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

	private final Map<String, PluginScheduledTasks> scheduledTasks = new IdentityHashMap<>(16);
	//private final ScheduledTaskRegistrar registrar;

	private StringValueResolver embeddedValueResolver;

	public DefaultScheduledMgr() {
		//this.registrar = new ScheduledTaskRegistrar();
	}

	@Override
	public void unregisterScheduledTasks(Pf4bootPlugin pf4bootPlugin){
		String pluginId = pf4bootPlugin.getPluginId();
		synchronized (this.scheduledTasks) {
			unregisterScheduledTasks4Plugin(pluginId);
		}
	}

	private void unregisterScheduledTasks4Plugin(String pluginId) {
		PluginScheduledTasks pluginScheduledTasks = this.scheduledTasks.remove(pluginId);
		if(pluginScheduledTasks!=null){
			for (Set<ScheduledTask> tasks : pluginScheduledTasks.getScheduledTasks().values()) {
				for (ScheduledTask task : tasks) {
					task.cancel(false);
					logger.info("cancel scheduled task {}", task);
				}
			}
			pluginScheduledTasks.getScheduledTasks().clear();
			pluginScheduledTasks.getRegistrar().destroy();
			logger.info("unregister scheduled tasks for plugin {}", pluginId);
		}
	}


	@Override
	public void registerScheduledTasks(Pf4bootPlugin pf4bootPlugin) {
		String pluginId = pf4bootPlugin.getPluginId();
		ConfigurableApplicationContext context = pf4bootPlugin.getPluginContext();
		PluginScheduledTasks pluginScheduledTasks = this.scheduledTasks.computeIfAbsent(pluginId, key -> new PluginScheduledTasks(key, new ScheduledTaskRegistrar()));

		Map<String, Object> beans = context.getBeansWithAnnotation(Component.class);
		for (Map.Entry<String, Object> entry : beans.entrySet()) {
			String beanName = entry.getKey();
			Object bean = entry.getValue();
			Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
			if (!this.nonAnnotatedClasses.contains(targetClass) &&
					AnnotationUtils.isCandidateClass(targetClass, Arrays.asList(Scheduled.class, Schedules.class))) {
				Map<Method, Set<Scheduled>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
						(MethodIntrospector.MetadataLookup<Set<Scheduled>>) method -> {
							Set<Scheduled> scheduledAnnotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(
									method, Scheduled.class, Schedules.class);
							return (!scheduledAnnotations.isEmpty() ? scheduledAnnotations : null);
						});

				if (annotatedMethods.isEmpty()) {
					this.nonAnnotatedClasses.add(targetClass);
					if (logger.isTraceEnabled()) {
						logger.trace("No @Scheduled annotations found on bean class: " + targetClass);
					}
				}
				else {
					// Non-empty set of methods
					annotatedMethods.forEach((method, scheduledAnnotations) ->
							scheduledAnnotations.forEach(scheduled -> processScheduled(pluginScheduledTasks, scheduled, method, bean)));
					if (logger.isTraceEnabled()) {
						logger.trace(annotatedMethods.size() + " @Scheduled methods processed on bean '" + beanName +
								"': " + annotatedMethods);
					}
				}
			}
		}
		if(!pluginScheduledTasks.getScheduledTasks().isEmpty()){
			logger.info("register scheduled tasks for plugin {}", pluginId);
			pluginScheduledTasks.getRegistrar().afterPropertiesSet();
		}
	}

	private void processScheduled(PluginScheduledTasks pluginScheduledTasks, Scheduled scheduled, Method method, Object bean) {
		try {
			String pluginId = pluginScheduledTasks.getPluginId();
			ScheduledMethodRunnable runnable = createRunnable(bean, method);
			boolean processedSchedule = false;
			String errorMessage =
					"Exactly one of the 'cron', 'fixedDelay(String)', or 'fixedRate(String)' attributes is required";

			Set<ScheduledTask> tasks = new LinkedHashSet<>(4);

			// Determine initial delay
			long initialDelay = convertToMillis(scheduled.initialDelay(), scheduled.timeUnit());
			String initialDelayString = scheduled.initialDelayString();
			if (StringUtils.hasText(initialDelayString)) {
				Assert.isTrue(initialDelay < 0, "Specify 'initialDelay' or 'initialDelayString', not both");
				if (this.embeddedValueResolver != null) {
					initialDelayString = this.embeddedValueResolver.resolveStringValue(initialDelayString);
				}
				if (StringUtils.hasLength(initialDelayString)) {
					try {
						initialDelay = convertToMillis(initialDelayString, scheduled.timeUnit());
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid initialDelayString value \"" + initialDelayString + "\" - cannot parse into long");
					}
				}
			}

			// Check cron expression
			String cron = scheduled.cron();
			if (StringUtils.hasText(cron)) {
				String zone = scheduled.zone();
				if (this.embeddedValueResolver != null) {
					cron = this.embeddedValueResolver.resolveStringValue(cron);
					zone = this.embeddedValueResolver.resolveStringValue(zone);
				}
				if (StringUtils.hasLength(cron)) {
					Assert.isTrue(initialDelay == -1, "'initialDelay' not supported for cron triggers");
					processedSchedule = true;
					if (!Scheduled.CRON_DISABLED.equals(cron)) {
						CronTrigger trigger;
						if (StringUtils.hasText(zone)) {
							trigger = new CronTrigger(cron, StringUtils.parseTimeZoneString(zone));
						}
						else {
							trigger = new CronTrigger(cron);
						}
						tasks.add(pluginScheduledTasks.getRegistrar().scheduleCronTask(new CronTask(runnable, trigger)));
					}
				}
			}

			// At this point we don't need to differentiate between initial delay set or not anymore
			if (initialDelay < 0) {
				initialDelay = 0;
			}

			// Check fixed delay
			long fixedDelay = convertToMillis(scheduled.fixedDelay(), scheduled.timeUnit());
			if (fixedDelay >= 0) {
				Assert.isTrue(!processedSchedule, errorMessage);
				processedSchedule = true;
				tasks.add(pluginScheduledTasks.getRegistrar().scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));
				logger.info("register fixed delay task {} for plugin {}", runnable.toString(), pluginId);
			}

			String fixedDelayString = scheduled.fixedDelayString();
			if (StringUtils.hasText(fixedDelayString)) {
				if (this.embeddedValueResolver != null) {
					fixedDelayString = this.embeddedValueResolver.resolveStringValue(fixedDelayString);
				}
				if (StringUtils.hasLength(fixedDelayString)) {
					Assert.isTrue(!processedSchedule, errorMessage);
					processedSchedule = true;
					try {
						fixedDelay = convertToMillis(fixedDelayString, scheduled.timeUnit());
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid fixedDelayString value \"" + fixedDelayString + "\" - cannot parse into long");
					}
					tasks.add(pluginScheduledTasks.getRegistrar().scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));

				}
			}

			// Check fixed rate
			long fixedRate = convertToMillis(scheduled.fixedRate(), scheduled.timeUnit());
			if (fixedRate >= 0) {
				Assert.isTrue(!processedSchedule, errorMessage);
				processedSchedule = true;
				tasks.add(pluginScheduledTasks.getRegistrar().scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, initialDelay)));
			}
			String fixedRateString = scheduled.fixedRateString();
			if (StringUtils.hasText(fixedRateString)) {
				if (this.embeddedValueResolver != null) {
					fixedRateString = this.embeddedValueResolver.resolveStringValue(fixedRateString);
				}
				if (StringUtils.hasLength(fixedRateString)) {
					Assert.isTrue(!processedSchedule, errorMessage);
					processedSchedule = true;
					try {
						fixedRate = convertToMillis(fixedRateString, scheduled.timeUnit());
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid fixedRateString value \"" + fixedRateString + "\" - cannot parse into long");
					}
					tasks.add(pluginScheduledTasks.getRegistrar().scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, initialDelay)));
				}
			}

			// Check whether we had any attribute set
			Assert.isTrue(processedSchedule, errorMessage);

			// Finally register the scheduled tasks
			Map<Object, Set<ScheduledTask>> scheduledTasks = pluginScheduledTasks.getScheduledTasks();
			synchronized (scheduledTasks) {
				Set<ScheduledTask> regTasks = scheduledTasks.computeIfAbsent(bean, key -> new LinkedHashSet<>(4));
				regTasks.addAll(tasks);
			}
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					"Encountered invalid @Scheduled method '" + method.getName() + "': " + ex.getMessage());
		}
	}

	@Override
	public void destroy() {
		synchronized (this.scheduledTasks) {
			Set<String> pluginIds = this.scheduledTasks.keySet();
			for (String pluginId : pluginIds) {
				unregisterScheduledTasks4Plugin(pluginId);
			}
		}
	}

	protected ScheduledMethodRunnable createRunnable(Object target, Method method) {
		Assert.isTrue(method.getParameterCount() == 0, "Only no-arg methods may be annotated with @Scheduled");
		Method invocableMethod = AopUtils.selectInvocableMethod(method, target.getClass());
		return new ScheduledMethodRunnable(target, invocableMethod);
	}

	private static long convertToMillis(long value, TimeUnit timeUnit) {
		return TimeUnit.MILLISECONDS.convert(value, timeUnit);
	}

	private static long convertToMillis(String value, TimeUnit timeUnit) {
		if (isDurationString(value)) {
			return Duration.parse(value).toMillis();
		}
		return convertToMillis(Long.parseLong(value), timeUnit);
	}

	private static boolean isDurationString(String value) {
		return (value.length() > 1 && (isP(value.charAt(0)) || isP(value.charAt(1))));
	}

	private static boolean isP(char ch) {
		return (ch == 'P' || ch == 'p');
	}

}
