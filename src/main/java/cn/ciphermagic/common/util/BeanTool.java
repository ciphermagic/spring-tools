package cn.ciphermagic.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Get spring registered beans in non-spring managed classes
 *
 * @author cipher
 */
public class BeanTool implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        if (applicationContext == null) {
            applicationContext = context;
        }
    }

    /**
     * get bean by name
     *
     * @param name bean name
     * @return bean object
     */
    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }

    /**
     * get bean by class type
     *
     * @param clazz class
     * @param <T>   class type
     * @return bean object
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

}