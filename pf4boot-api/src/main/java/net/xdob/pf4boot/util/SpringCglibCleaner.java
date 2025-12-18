package net.xdob.pf4boot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;


public final class SpringCglibCleaner {
	private static final Logger LOG = LoggerFactory.getLogger("SpringCglibCleaner");

	public static void clearAll(ClassLoader pluginCl) {
		if (pluginCl == null) return;
		clearAbstractClassGeneratorCache(pluginCl);
		clearEnhancerStaticCGLIBFields(pluginCl);
		clearEnhancerThreadLocals(pluginCl);
		// 可选：如果你还想尝试清理其他 cglib 相关静态缓存，可以在这里扩展
	}

	// ---------- AbstractClassGenerator.CACHE ----------
	private static void clearAbstractClassGeneratorCache(ClassLoader pluginCl) {
		try {
			Class<?> gen = Class.forName("org.springframework.cglib.core.AbstractClassGenerator");
			Field cacheField = null;
			// 常见名字
			try {
				cacheField = gen.getDeclaredField("CACHE");
			} catch (NoSuchFieldException ignored) {
				// 扫描静态 Map 字段作为回退
				for (Field f : gen.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
						cacheField = f;
						break;
					}
				}
			}
			if (cacheField == null) {
				LOG.info("AbstractClassGenerator.CACHE not found");
				return;
			}
			cacheField.setAccessible(true);
			Object cacheObj = cacheField.get(null);
			if (!(cacheObj instanceof Map<?, ?>)) return;
			Map<?, ?> cache = (Map<?, ?>) cacheObj;


			// CACHE 的 key 可能是 GeneratorKey，也可能内部封装了 ClassLoader
			cache.entrySet().removeIf(e -> {
				try {
					// value 通常是 Class<?> 或类似对象，直接检查 value 的类加载器
					Object v = e.getValue();
					if (v instanceof Class<?> && ((Class<?>) v).getClassLoader() == pluginCl) return true;
					if (v != null && v.getClass().getClassLoader() == pluginCl) return true;

					// key 检查：key 可能内部引用了 ClassLoader 字段
					Object k = e.getKey();
					if (k != null) {
						// 直接比对 key 的 classloader（有时 GeneratorKey 由 pluginCl 加载）
						if (k.getClass().getClassLoader() == pluginCl) return true;

						// 反射查找可能的 ClassLoader / Class 字段
						for (Field f : k.getClass().getDeclaredFields()) {
							if (Class.class.isAssignableFrom(f.getType())) {
								f.setAccessible(true);
								Object val = f.get(k);
								if (val instanceof Class<?> && ((Class<?>) val).getClassLoader() == pluginCl) return true;
							}
							if (ClassLoader.class.isAssignableFrom(f.getType())) {
								f.setAccessible(true);
								Object val = f.get(k);
								if (val == pluginCl) return true;
							}
						}
					}
				} catch (Throwable ignored) {}
				return false;
			});

			LOG.info("Cleared AbstractClassGenerator.CACHE entries for pluginClassLoader");
		} catch (Throwable t) {
			LOG.warn("clearAbstractClassGeneratorCache failed: " + t.getMessage(), t);
		}
	}



	// ---------- helper ----------


	/**
	 * 清理所有由 pluginCl 加载的 CGLIB 生成类中的静态 CALLBACK 字段（比如 CGLIB$CALLBACK_FILTER）。
	 * 也会尝试清空 CGLIB$CALLBACKS 数组元素。
	 */
	private static void clearEnhancerStaticCGLIBFields(ClassLoader pluginCl) {
		try {


			// 遍历 CACHE 中的 value（可能是 Class 或其他持有 Class 的对象）
			Set<Class<?>> classesToScan = new HashSet<>();
			// 获取所有已加载的CGLIB代理类
			Field classesField = ClassLoader.class.getDeclaredField("classes");
			classesField.setAccessible(true);
			Vector<Class<?>> classes = (Vector<Class<?>>) classesField.get(pluginCl);

			for (Class<?> clazz : new ArrayList<>(classes)) {
				if (clazz.getName().contains("$$EnhancerBySpringCGLIB$$")) {
					classesToScan.add(clazz);
				}
			}

			// 扫描这些 Class 的静态字段，清掉 CGLIB 类型的静态引用
			for (Class<?> c : classesToScan) {
				try {
					for (Field f : c.getDeclaredFields()) {
						int mod = f.getModifiers();
						if (!Modifier.isStatic(mod)||Modifier.isFinal(mod)) continue;
						String name = f.getName();
						if (name.startsWith("CGLIB$")) {
							try {
								f.setAccessible(true);
								Object old = f.get(null);
								if (old != null) {
									// 如果是数组（CGLIB$CALLBACKS），尽量清空元素引用再置空字段
									if (old.getClass().isArray()) {
										int len = Array.getLength(old);
										for (int i = 0; i < len; i++) {
											try { Array.set(old, i, null); } catch (Throwable ignored) {}
										}
									}
									// 最终把字段指向 null
									try { f.set(null, null); } catch (Throwable ignored) {
										LOG.warn("Failed clearing field " + c.getName() + "#" + name + " : ", ignored);
									}
									//LOG.info("Cleared static CGLIB field " + c.getName() + "#" + name);
								}
							} catch (Throwable t) {
								LOG.info("Failed clearing field " + c.getName() + "#" + name + " : " + t.getMessage());
							}
						}
					}
				} catch (Throwable ignored) {}
			}
		} catch (Throwable t) {
			LOG.info("clearEnhancerStaticCGLIBFields failed: " + t.getMessage(), t);
		}
	}

	/**
	 * 清理 org.springframework.cglib.proxy.Enhancer 类可能保存的 ThreadLocal / 全局 callbacks。
	 * 尝试以下策略：
	 *  - 找到 Enhancer 中的静态 ThreadLocal / Map /数组 并移除 pluginCl 相关项
	 *  - 如果是 ThreadLocal，尝试把 ThreadLocal 的值清掉（注意：有潜在风险，只在插件卸载阶段调用）
	 */
	private static void clearEnhancerThreadLocals(ClassLoader pluginCl) {
		try {
			Class<?> enhancer = Class.forName("org.springframework.cglib.proxy.Enhancer");
			for (Field f : enhancer.getDeclaredFields()) {
				int mod = f.getModifiers();
				if (!Modifier.isStatic(mod)) continue;
				Class<?> t = f.getType();
				if (ThreadLocal.class.isAssignableFrom(t) || Map.class.isAssignableFrom(t) || t.isArray()) {
					try {
						f.setAccessible(true);
						Object val = f.get(null);
						if (val == null) continue;

						// 如果是 ThreadLocal，尝试清空所有线程中存的 callback（谨慎）
						if (val instanceof ThreadLocal) {
							try {
								((ThreadLocal<?>) val).remove();
								LOG.info("Removed ThreadLocal in Enhancer field " + f.getName());
							} catch (Throwable ignored) {}
						} else if (val instanceof Map) {
							Map<?, ?> map = (Map<?, ?>) val;
							map.keySet().removeIf(k -> {
								try {
									if (k instanceof ClassLoader && k == pluginCl) return true;
									if (k != null && k.getClass().getClassLoader() == pluginCl) return true;
								} catch (Throwable ignored) {}
								return false;
							});
							LOG.info("Cleaned Map in Enhancer field " + f.getName());
						} else if (val.getClass().isArray()) {
							int len = Array.getLength(val);
							for (int i = 0; i < len; i++) {
								Object elem = Array.get(val, i);
								if (elem != null && elem.getClass().getClassLoader() == pluginCl) {
									try { Array.set(val, i, null); } catch (Throwable ignored) {}
								}
							}
						} else {
							// 其他类型：若其类加载器是 pluginCl，置空字段
							if (val.getClass().getClassLoader() == pluginCl) {
								try { f.set(null, null); } catch (Throwable ignored) {}
							}
						}
					} catch (Throwable ignored) {}
				} else {
					// 名字里含 CALLBACK / THREAD 的也尝试清空（保守）
					String name = f.getName();
					if (name.toUpperCase().contains("CALLBACK") || name.toUpperCase().contains("THREAD")) {
						try {
							f.setAccessible(true);
							Object val = f.get(null);
							if (val != null && val.getClass().getClassLoader() == pluginCl) {
								try { f.set(null, null); } catch (Throwable ignored) {}
							}
						} catch (Throwable ignored) {}
					}
				}
			}
		} catch (Throwable t) {
			LOG.warn("clearEnhancerThreadLocals failed: " + t.getMessage(), t);
		}
	}

}

