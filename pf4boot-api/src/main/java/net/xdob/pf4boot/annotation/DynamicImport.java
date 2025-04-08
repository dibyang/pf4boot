package net.xdob.pf4boot.annotation;


import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

/**
 *
 * 标识需要动态导入依赖
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@Documented
public @interface DynamicImport {
}
