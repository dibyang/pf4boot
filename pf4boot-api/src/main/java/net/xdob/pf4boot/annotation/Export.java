package net.xdob.pf4boot.annotation;

import java.lang.annotation.*;

/**
 * Export
 *
 * Exporting components or services for plug-ins
 *
 * @author yangzj
 * @version 1.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Export {
}
