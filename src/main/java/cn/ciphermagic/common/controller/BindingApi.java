package cn.ciphermagic.common.controller;

import java.lang.annotation.*;

/**
 * @Author: CipherCui
 * @Description: create controller
 * @Date: Created in 17:11 2018/10/16
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface BindingApi {

    Class value();

}
