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
		clearMethodProxyCache(pluginCl);
		clearEnhancerStaticCallbacks(pluginCl);
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

	// ---------- MethodProxy 静态缓存 ----------
	public static void clearMethodProxyCache(ClassLoader pluginCl) {
		try {
			Class<?> methodProxyClass = Class.forName("org.springframework.cglib.proxy.MethodProxy");
			Field cacheField = findStaticMapField(methodProxyClass,
					new String[] {"methodCache", "methodProxyCache", "cache", "FAST_CLASS_CACHE", "METHOD_CACHE"});
			if (cacheField == null) {
				LOG.info("MethodProxy static Map field not found");
				return;
			}
			cacheField.setAccessible(true);
			Object cacheObj = cacheField.get(null);
			if (!(cacheObj instanceof Map<?, ?>)) return;
			Map<?, ?> cache = (Map<?, ?>) cacheObj;

			cache.entrySet().removeIf(entry -> {
				Object value = entry.getValue();
				try {
					if (value == null) return false;
					Class<?> mpClass = value.getClass();

					// 尝试路径 1：createInfo.c1 / c2
					Field createInfoField = findFieldRecursive(mpClass, "createInfo");
					if (createInfoField != null) {
						createInfoField.setAccessible(true);
						Object createInfo = createInfoField.get(value);
						if (createInfo != null) {
							Field c1 = findFieldRecursive(createInfo.getClass(), "c1");
							Field c2 = findFieldRecursive(createInfo.getClass(), "c2");
							if (c1 != null) {
								c1.setAccessible(true);
								Object c1v = c1.get(createInfo);
								if (c1v instanceof Class<?> && ((Class<?>) c1v).getClassLoader() == pluginCl) return true;
							}
							if (c2 != null) {
								c2.setAccessible(true);
								Object c2v = c2.get(createInfo);
								if (c2v instanceof Class<?> && ((Class<?>) c2v).getClassLoader() == pluginCl) return true;
							}
						}
					}

					// 尝试路径 2：fastClassInfo.f1 / f2 -> 检测 FastClass 里的类型
					Field fciField = findFieldRecursive(mpClass, "fastClassInfo");
					if (fciField != null) {
						fciField.setAccessible(true);
						Object fci = fciField.get(value);
						if (fci != null) {
							Field f1 = findFieldRecursive(fci.getClass(), "f1");
							Field f2 = findFieldRecursive(fci.getClass(), "f2");
							if (f1 != null) {
								f1.setAccessible(true);
								Object fastClass1 = f1.get(fci);
								if (fastClass1 != null && fastClassBelongsToLoader(fastClass1, pluginCl)) return true;
							}
							if (f2 != null) {
								f2.setAccessible(true);
								Object fastClass2 = f2.get(fci);
								if (fastClass2 != null && fastClassBelongsToLoader(fastClass2, pluginCl)) return true;
							}
						}
					}

					// 尝试路径 3：如果 MethodProxy 的实现类本身由 pluginCl 加载
					if (mpClass.getClassLoader() == pluginCl) return true;

				} catch (Throwable ignored) {}
				return false;
			});

			LOG.info("Cleared MethodProxy cache entries for pluginClassLoader");
		} catch (Throwable t) {
			LOG.warn("clearMethodProxyCache failed: " + t.getMessage(), t);
		}
	}

	// ---------- helper ----------

	private static Field findStaticMapField(Class<?> cls, String[] candidates) {
		for (String name : candidates) {
			try {
				Field f = cls.getDeclaredField(name);
				if (Map.class.isAssignableFrom(f.getType()) && Modifier.isStatic(f.getModifiers())) {
					return f;
				}
			} catch (NoSuchFieldException ignored) {}
		}
		for (Field f : cls.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
				return f;
			}
		}
		return null;
	}

	private static Field findFieldRecursive(Class<?> cls, String name) {
		Class<?> cur = cls;
		while (cur != null && cur != Object.class) {
			try {
				Field f = cur.getDeclaredField(name);
				return f;
			} catch (NoSuchFieldException ignored) {}
			cur = cur.getSuperclass();
		}
		return null;
	}

	private static boolean fastClassBelongsToLoader(Object fastClassObj, ClassLoader pluginCl) {
		if (fastClassObj == null) return false;
		Class<?> fcClass = fastClassObj.getClass();

		// 尝试常见方法名获取被代理的类型
		String[] methodCandidates = new String[] {"getType", "getJavaClass", "getClazz", "getRealClass"};
		for (String mname : methodCandidates) {
			try {
				Method m = fcClass.getMethod(mname);
				if (m != null) {
					Object type = m.invoke(fastClassObj);
					if (type instanceof Class<?> && ((Class<?>) type).getClassLoader() == pluginCl) return true;
				}
			} catch (NoSuchMethodException ignored) {
			} catch (Throwable ignored) {}
		}

		// 反射查找 Class 字段
		for (Field f : fcClass.getDeclaredFields()) {
			if (Class.class.isAssignableFrom(f.getType())) {
				try {
					f.setAccessible(true);
					Object val = f.get(fastClassObj);
					if (val instanceof Class<?> && ((Class<?>) val).getClassLoader() == pluginCl) return true;
				} catch (Throwable ignored) {}
			}
		}

		// 最后回退：FastClass 实现类本身由 pluginCl 加载
		try {
			if (fcClass.getClassLoader() == pluginCl) return true;
		} catch (Throwable ignored) {}

		return false;
	}

	/**
	 * 清理所有由 pluginCl 加载的 CGLIB 生成类中的静态 CALLBACK 字段（比如 CGLIB$CALLBACK_FILTER）。
	 * 也会尝试清空 CGLIB$CALLBACKS 数组元素。
	 */
	private static void clearEnhancerStaticCallbacks(ClassLoader pluginCl) {
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

			// 扫描这些 Class 的静态字段，清掉 CGLIB CALLBACK 类型的静态引用
			for (Class<?> c : classesToScan) {
				try {
					for (Field f : c.getDeclaredFields()) {
						int mod = f.getModifiers();
						if (!Modifier.isStatic(mod)) continue;
						String name = f.getName();
						if (name.startsWith("CGLIB$CALLBACK") || name.contains("CALLBACK") || name.contains("CGLIB$BIND") || name.contains("CGLIB$THREAD")) {
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
									try { f.set(null, null); } catch (Throwable ignored) {}
									LOG.info("Cleared static callback field " + c.getName() + "#" + name);
								}
							} catch (Throwable t) {
								LOG.info("Failed clearing field " + c.getName() + "#" + name + " : " + t.getMessage());
							}
						}
					}
				} catch (Throwable ignored) {}
			}
		} catch (Throwable t) {
			LOG.info("clearEnhancerStaticCallbacks failed: " + t.getMessage(), t);
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
				String fname = f.getName().toUpperCase();
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

