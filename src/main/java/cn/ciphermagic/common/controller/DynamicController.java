package cn.ciphermagic.common.controller;

import java.lang.annotation.*;

/**
 * dynamic create controller
 *
 * @author: CipherCui
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DynamicController {

    /**
     * Implemented class, when it's not set, find the direct implemented class.
     *
     * @return class
     * @see DynamicControllerRegistry#getImplementedClass(Class)
     */
    Class value() default String.class;

}
