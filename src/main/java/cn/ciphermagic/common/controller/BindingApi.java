package cn.ciphermagic.common.controller;

import java.lang.annotation.*;

/**
 * create controller
 *
 * @author: CipherCui
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface BindingApi {

    Class value();

}
