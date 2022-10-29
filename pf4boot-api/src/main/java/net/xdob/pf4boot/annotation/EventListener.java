package net.xdob.pf4boot.annotation;

import java.lang.annotation.*;

/**
 * EventListener
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {
}
