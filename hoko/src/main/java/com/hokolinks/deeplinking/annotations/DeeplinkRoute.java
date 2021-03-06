package com.hokolinks.deeplinking.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use this annotation on the activities you wish to be deeplinkable.
 * <pre>{@code @DeeplinkRoute("product/:product_id")
 * public class ProductActivity extends Activity { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DeeplinkRoute {
    String noValue = "DeeplinkNoValue";

    String value() default noValue;

}
