package cn.ciphermagic.common.controller;

import cn.ciphermagic.common.util.ClassScaner;
import javassist.*;
import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ApplicationObjectSupport;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author: CipherCui
 * @Description: dynamic create controller by javassist, then register to the containing context.
 * @Date: Created in 14:00 2018/10/16
 */
public class DynamicControllerRegistry extends ApplicationObjectSupport implements BeanDefinitionRegistryPostProcessor {

    /**
     * spare using for findBasePackage
     */
    private static final Set<String> TOP_PACKAGES = new HashSet<String>() {{
        add("com");
        add("cn");
        add("org");
        add("net");
    }};

    /**
     * service class's base package path
     */
    private String basePackage;

    @Override
    protected void initApplicationContext(ApplicationContext context) {
        super.initApplicationContext(context);
        this.findBasePackage(context);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        if (StringUtils.isEmpty(basePackage)) {
            throw new IllegalArgumentException("could not find EnableDynamicController annotation.");
        }
        ClassScaner.scan(basePackage, BindingApi.class).forEach(serviceClass -> {
            Class<?> apiClass = serviceClass.getAnnotation(BindingApi.class).value();
            String className = assembleClassName(serviceClass.getName(), apiClass.getSimpleName());
            // make class
            Class clazz = makeClass(className, apiClass, serviceClass);
            // register bean
            BeanDefinitionBuilder definitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz.getName());
            beanDefinitionRegistry.registerBeanDefinition(clazz.getName(), definitionBuilder.getBeanDefinition());
        });
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    }

    /**
     * find annotation in application context
     *
     * @param applicationContext containing application context
     */
    private void findBasePackage(ApplicationContext applicationContext) {
        Map<String, Object> map = applicationContext.getBeansWithAnnotation(EnableDynamicController.class);
        if (map == null) {
            throw new IllegalArgumentException("could not found EnableDynamicController annotation.");
        }
        if (map.size() != 1) {
            throw new IllegalArgumentException("found more than 1 EnableDynamicController annotation.");
        }
        Object obj = map.values().iterator().next();
        EnableDynamicController annotation = obj.getClass().getAnnotation(EnableDynamicController.class);
        if (annotation != null && !StringUtils.isEmpty(annotation.value())) {
            this.basePackage = annotation.value();
        } else {
            this.basePackage = obj.getClass().getPackage().getName();
        }
    }

    /**
     * find annotation in top package, eg: cn, com, org ...
     */
    private void findBasePackage() {
        for (String top : TOP_PACKAGES) {
            Set<Class<?>> set = ClassScaner.scan(top, EnableDynamicController.class);
            if (!CollectionUtils.isEmpty(set)) {
                Class<?> clazz = set.iterator().next();
                EnableDynamicController annotation = clazz.getAnnotation(EnableDynamicController.class);
                this.basePackage = annotation.value();
                break;
            }
        }
    }

    /**
     * assemble class's name base on service class's package path and api class's name
     *
     * @param serviceClass service class
     * @param apiName      api class's name
     * @return class's name
     */
    private String assembleClassName(String serviceClass, String apiName) {
        int index = serviceClass.lastIndexOf(".") + 1;
        serviceClass = serviceClass.substring(0, index);
        return serviceClass + apiName + "Controller";
    }

    /**
     * make controller class that extends api class, while using service as field
     *
     * @param className    the controller class's name
     * @param apiClass     api class to extends
     * @param serviceClass service class to use
     * @return controller class
     */
    private Class makeClass(String className, Class apiClass, Class serviceClass) {
        Class clazz;
        // create class
        ClassPool pool = ClassPool.getDefault();
        ClassClassPath classPath = new ClassClassPath(this.getClass());
        pool.insertClassPath(classPath);
        CtClass cc = pool.makeClass(className);
        ClassFile ccFile = cc.getClassFile();
        ConstPool constpool = ccFile.getConstPool();

        try {

            // api class
            CtClass apiCtClass = pool.get(apiClass.getName());

            // implements api
            cc.addInterface(pool.getCtClass(apiClass.getName()));

            // controller annotation
            AnnotationsAttribute classAttr = new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
            Annotation controller = new Annotation("org.springframework.web.bind.annotation.RestController", constpool);
            classAttr.addAnnotation(controller);
            ccFile.addAttribute(classAttr);

            // autowired service
            CtField serviceField = new CtField(pool.getCtClass(serviceClass.getName()), "service", cc);
            serviceField.setModifiers(Modifier.PRIVATE);
            FieldInfo fieldInfo = serviceField.getFieldInfo();
            AnnotationsAttribute fieldAttr = new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
            Annotation autowired = new Annotation("org.springframework.beans.factory.annotation.Autowired", constpool);
            fieldAttr.addAnnotation(autowired);
            fieldInfo.addAttribute(fieldAttr);
            cc.addField(serviceField);

            // methods
            Method[] methods = apiClass.getMethods();
            for (Method m : methods) {
                CtMethod apiMethod = apiCtClass.getDeclaredMethod(m.getName());
                CtClass[] parameterTypes = apiMethod.getParameterTypes();

                // create method
                CtClass returnType = pool.get(m.getReturnType().getName());
                CtMethod ctMethod = new CtMethod(returnType, m.getName(), parameterTypes, cc);
                ctMethod.setModifiers(Modifier.PUBLIC);

                // generic signature
                if (apiMethod.getGenericSignature() != null) {
                    ctMethod.setGenericSignature(apiMethod.getGenericSignature());
                }

                // parameter annotations
                List attributes = apiMethod.getMethodInfo().getAttributes();
                if (!CollectionUtils.isEmpty(attributes)) {
                    for (Object attr : attributes) {
                        ctMethod.getMethodInfo().addAttribute((AttributeInfo) attr);
                    }
                }

                // method body
                StringBuilder paramBody = new StringBuilder("(");
                if (parameterTypes != null && parameterTypes.length > 0) {
                    for (int i = 1; i <= parameterTypes.length; i++) {
                        paramBody.append("$").append(i);
                        paramBody.append(i == parameterTypes.length ? "" : ",");
                    }
                }
                paramBody.append(")");
                String body = "{\n" +
                        "return service." + m.getName() + paramBody.toString() + ";\n" +
                        "}";
                ctMethod.setBody(body);
                cc.addMethod(ctMethod);
            }
            clazz = cc.toClass();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return clazz;
    }

}
