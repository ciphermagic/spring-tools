package cn.ciphermagic.common.controller;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * enable dynamic controller
 *
 * @author: CipherCui
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(DynamicControllerRegistry.class)
public @interface EnableDynamicController {

    /**
     * package path to scan
     *
     * @return package path
     */
    String value() default "";

}
