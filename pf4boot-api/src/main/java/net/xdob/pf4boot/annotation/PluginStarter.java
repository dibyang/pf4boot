package net.xdob.pf4boot.annotation;

import org.springframework.core.annotation.AliasFor;

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
@ExportBeans
public @interface PluginStarter {
  String EMPTY = "";
  String DEFAULT = "default";

  /**
   * 插件内的启动器类，一个插件至少需要一个启动器类
   */
  Class<?>[] value();

  /**
   * 插件分组，默认为default
   */
  String group() default DEFAULT;

  @AliasFor(annotation = ExportBeans.class)
  ExportBeans.Class4Bean[] class4Beans() default {};
  @AliasFor(annotation = ExportBeans.class)
  ExportBeans.Name4Bean[] name4Beans() default {};

 }
