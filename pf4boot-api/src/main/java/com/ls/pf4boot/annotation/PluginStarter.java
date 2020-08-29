package com.ls.pf4boot.annotation;

import java.lang.annotation.*;

/**
 * PluginStarter
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginStarter {
  Class<?>[] value();
}
