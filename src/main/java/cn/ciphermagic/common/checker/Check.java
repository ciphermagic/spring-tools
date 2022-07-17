package cn.ciphermagic.common.checker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * check param
 *
 * @author: CipherCui
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RUNTIME)
@Inherited
public @interface Check {

    /**
     * field name or spel expression
     * @return array
     */
    String[] value() default {};

    /**
     * dynamic field validation rules
     * @return key of dynamic filed
     */
    String dynamic() default "";

}
