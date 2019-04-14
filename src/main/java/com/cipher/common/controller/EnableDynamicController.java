package com.cipher.common.controller;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @Author: CipherCui
 * @Description: enable dynamic controller
 * @Date: Created in 17:11 2018/10/16
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(DynamicControllerRegistry.class)
public @interface EnableDynamicController {

    String value() default "";

}
