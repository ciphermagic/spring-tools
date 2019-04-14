package cn.ciphermagic.common.checker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @Author: CipherCui
 * @Description: check param
 * @Date: Created in 17:11 2018/10/16
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RUNTIME)
public @interface Check {

    /**
     * field name or SpEL expression
     */
    String[] value() default {};

}
