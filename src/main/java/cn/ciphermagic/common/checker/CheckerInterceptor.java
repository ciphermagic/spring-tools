package cn.ciphermagic.common.checker;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMethodMatcher;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * aspect for param check
 *
 * @author CipherCui
 */
public class CheckerInterceptor implements MethodInterceptor {

    private static final String SEPARATOR = ":";
    private final ExpressionParser parser = new SpelExpressionParser();
    private final LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();
    private Function<String, Object> unsuccessful;

    private CheckerInterceptor() {
    }

    /**
     * Action performed when check fails
     *
     * @param unsuccessful lambda of the action
     */
    public void setUnsuccessful(Function<String, Object> unsuccessful) {
        this.unsuccessful = unsuccessful;
    }

    /**
     * checker builder
     */
    public static class Builder {
        private final CheckerInterceptor checker = new CheckerInterceptor();

        public Builder unsuccessful(Function<String, Object> unsuccessful) {
            checker.setUnsuccessful(unsuccessful);
            return this;
        }

        public CheckerInterceptor build() {
            return checker;
        }
    }

    public static Advisor checkAdvisor(Function<String, Object> unsuccessful) {
        final AnnotationMethodMatcher annotatedMethodOrTargetClassMatcher = new AnnotationMethodMatcher(Check.class, true) {
            @Override
            public boolean matches(final Method method, final Class<?> targetClass) {
                if (AnnotatedElementUtils.hasAnnotation(targetClass, Check.class)) {
                    return true;
                }
                return super.matches(method, targetClass);
            }
        };
        return new DefaultPointcutAdvisor(
                new ComposablePointcut(annotatedMethodOrTargetClassMatcher),
                CheckerInterceptor.builder().unsuccessful(unsuccessful).build()
        );
    }

    /**
     * initialize builder
     *
     * @return checker builder
     * @see Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object obj;
        String msg = doCheck(invocation);
        if (!StringUtils.isEmpty(msg)) {
            return unsuccessful.apply(msg);
        }
        obj = invocation.proceed();
        return obj;
    }

    private String doCheck(MethodInvocation invocation) {
        Object[] arguments = invocation.getArguments();
        Method method = invocation.getMethod();
        String methodInfo = StringUtils.isEmpty(method.getName()) ? "" : " while calling " + method.getName();
        String msg = "";
        MergedAnnotation<Check> mergedAnnotation = isCheck(method, arguments);
        if (mergedAnnotation.isPresent()) {
            Check annotation = mergedAnnotation.synthesize();
            String[] fields = annotation.value();
            Object vo = arguments[0];
            if (vo == null) {
                msg = "param can not be null";
            } else {
                for (String field : fields) {
                    FieldInfo info = resolveField(field, methodInfo);
                    Boolean isValid;
                    if (info.optEnum == Operator.SPEL) {
                        isValid = parseSpel(method, arguments, info.field);
                    } else {
                        String getMethodName = "get" + StringUtils.capitalize(info.field);
                        Method getMethod = ReflectionUtils.findMethod(vo.getClass(), getMethodName);
                        if (getMethod == null) {
                            break;
                        }
                        Object value = ReflectionUtils.invokeMethod(getMethod, vo);
                        isValid = info.optEnum.fun.apply(value, info.operatorNum);
                    }
                    if (!isValid) {
                        msg = info.innerMsg;
                        break;
                    }
                }
            }
        }
        return msg;
    }

    /**
     * parse spel expression
     *
     * @param method    method
     * @param arguments arguments
     * @param spel      spel expression
     * @return is match
     */
    private Boolean parseSpel(Method method, Object[] arguments, String spel) {
        String[] params = discoverer.getParameterNames(method);
        EvaluationContext context = new StandardEvaluationContext();
        if (params == null || params.length == 0) {
            return Boolean.FALSE;
        }
        for (int len = 0; len < params.length; len++) {
            context.setVariable(params[len], arguments[len]);
        }
        try {
            Expression expression = parser.parseExpression(spel);
            return expression.getValue(context, Boolean.class);
        } catch (Exception e) {
            e.printStackTrace();
            return Boolean.FALSE;
        }
    }

    /**
     * parse field
     *
     * @param fieldStr   field string
     * @param methodInfo method info
     * @return the entity contain field's info
     */
    private FieldInfo resolveField(String fieldStr, String methodInfo) {
        FieldInfo fieldInfo = new FieldInfo();
        String innerMsg = "";
        // parse error message
        if (fieldStr.contains(SEPARATOR)) {
            if (fieldStr.split(SEPARATOR).length == 2) {
                innerMsg = fieldStr.split(SEPARATOR)[1].trim();
                fieldStr = fieldStr.split(SEPARATOR)[0].trim();
            } else {
                throw new IllegalArgumentException("@Check annotation error: " + fieldStr);
            }
        }
        // parse operator
        if (fieldStr.startsWith("#") || fieldStr.startsWith("T(")) {
            fieldInfo.optEnum = Operator.SPEL;
        } else if (fieldStr.contains(Operator.GREATER_THAN_EQUAL.value)) {
            fieldInfo.optEnum = Operator.GREATER_THAN_EQUAL;
        } else if (fieldStr.contains(Operator.LESS_THAN_EQUAL.value)) {
            fieldInfo.optEnum = Operator.LESS_THAN_EQUAL;
        } else if (fieldStr.contains(Operator.GREATER_THAN.value)) {
            fieldInfo.optEnum = Operator.GREATER_THAN;
        } else if (fieldStr.contains(Operator.LESS_THAN.value)) {
            fieldInfo.optEnum = Operator.LESS_THAN;
        } else if (fieldStr.contains(Operator.NOT_EQUAL.value)) {
            fieldInfo.optEnum = Operator.NOT_EQUAL;
        } else {
            fieldInfo.optEnum = Operator.NOT_NULL;
        }
        // direct assignment field
        if (fieldInfo.optEnum == Operator.NOT_NULL || fieldInfo.optEnum == Operator.SPEL) {
            fieldInfo.field = fieldStr;
        }
        // other operators, need to separate fields and operands
        else {
            fieldInfo.field = fieldStr.split(fieldInfo.optEnum.value)[0];
            fieldInfo.operatorNum = fieldStr.split(fieldInfo.optEnum.value)[1];
        }
        fieldInfo.operator = fieldInfo.optEnum.value;
        String operatorNum = fieldInfo.operatorNum == null ? "" : " " + fieldInfo.operatorNum;
        String defaultMsg = fieldInfo.field + " must " + fieldInfo.operator + operatorNum + methodInfo;
        fieldInfo.innerMsg = StringUtils.isEmpty(innerMsg) ? defaultMsg : innerMsg;
        return fieldInfo;
    }

    /**
     * is not null
     *
     * @param value       field's value
     * @param operatorNum the num of operator
     * @return is not null
     */
    private static Boolean isNotNull(Object value, String operatorNum) {
        Boolean isNotNull = Boolean.TRUE;
        Boolean isStringNull = (value instanceof String) && StringUtils.isEmpty(value);
        Boolean isCollectionNull = (value instanceof Collection) && CollectionUtils.isEmpty((Collection<?>) value);
        if (value == null) {
            isNotNull = Boolean.FALSE;
        } else if (isStringNull || isCollectionNull) {
            isNotNull = Boolean.FALSE;
        }
        return isNotNull;
    }

    /**
     * is greater than
     *
     * @param value       field value
     * @param operatorNum operatorNum
     * @return is greater than
     */
    private static Boolean isGreaterThan(Object value, String operatorNum) {
        Boolean isGreaterThan = Boolean.FALSE;
        if (value == null) {
            return Boolean.FALSE;
        }
        boolean isStringGreaterThen = (value instanceof String) && ((String) value).length() > Integer.parseInt(operatorNum);
        boolean isLongGreaterThen = (value instanceof Long) && ((Long) value) > Long.parseLong(operatorNum);
        boolean isIntegerGreaterThen = (value instanceof Integer) && ((Integer) value) > Integer.parseInt(operatorNum);
        boolean isShortGreaterThen = (value instanceof Short) && ((Short) value) > Short.parseShort(operatorNum);
        boolean isFloatGreaterThen = (value instanceof Float) && ((Float) value) > Float.parseFloat(operatorNum);
        boolean isDoubleGreaterThen = (value instanceof Double) && ((Double) value) > Double.parseDouble(operatorNum);
        boolean isCollectionGreaterThen = (value instanceof Collection) && ((Collection<?>) value).size() > Integer.parseInt(operatorNum);
        if (isStringGreaterThen || isLongGreaterThen || isIntegerGreaterThen ||
                isShortGreaterThen || isFloatGreaterThen || isDoubleGreaterThen || isCollectionGreaterThen) {
            isGreaterThan = Boolean.TRUE;
        }
        return isGreaterThan;
    }

    /**
     * is greater than or equal to
     *
     * @param value       field value
     * @param operatorNum operatorNum
     * @return is greater than or equal to
     */
    private static Boolean isGreaterThanEqual(Object value, String operatorNum) {
        Boolean isGreaterThanEqual = Boolean.FALSE;
        if (value == null) {
            return Boolean.FALSE;
        }
        boolean isStringGreaterThenEqual = (value instanceof String) && ((String) value).length() >= Integer.parseInt(operatorNum);
        boolean isLongGreaterThenEqual = (value instanceof Long) && ((Long) value) >= Long.parseLong(operatorNum);
        boolean isIntegerGreaterThenEqual = (value instanceof Integer) && ((Integer) value) >= Integer.parseInt(operatorNum);
        boolean isShortGreaterThenEqual = (value instanceof Short) && ((Short) value) >= Short.parseShort(operatorNum);
        boolean isFloatGreaterThenEqual = (value instanceof Float) && ((Float) value) >= Float.parseFloat(operatorNum);
        boolean isDoubleGreaterThenEqual = (value instanceof Double) && ((Double) value) >= Double.parseDouble(operatorNum);
        boolean isCollectionGreaterThenEqual = (value instanceof Collection) && ((Collection<?>) value).size() >= Integer.parseInt(operatorNum);
        if (isStringGreaterThenEqual || isLongGreaterThenEqual || isIntegerGreaterThenEqual ||
                isShortGreaterThenEqual || isFloatGreaterThenEqual || isDoubleGreaterThenEqual || isCollectionGreaterThenEqual) {
            isGreaterThanEqual = Boolean.TRUE;
        }
        return isGreaterThanEqual;
    }

    /**
     * is less than
     *
     * @param value       field value
     * @param operatorNum operatorNum
     * @return is less than
     */
    private static Boolean isLessThan(Object value, String operatorNum) {
        Boolean isLessThan = Boolean.FALSE;
        if (value == null) {
            return Boolean.FALSE;
        }
        boolean isStringLessThen = (value instanceof String) && ((String) value).length() < Integer.parseInt(operatorNum);
        boolean isLongLessThen = (value instanceof Long) && ((Long) value) < Long.parseLong(operatorNum);
        boolean isIntegerLessThen = (value instanceof Integer) && ((Integer) value) < Integer.parseInt(operatorNum);
        boolean isShortLessThen = (value instanceof Short) && ((Short) value) < Short.parseShort(operatorNum);
        boolean isFloatLessThen = (value instanceof Float) && ((Float) value) < Float.parseFloat(operatorNum);
        boolean isDoubleLessThen = (value instanceof Double) && ((Double) value) < Double.parseDouble(operatorNum);
        boolean isCollectionLessThen = (value instanceof Collection) && ((Collection<?>) value).size() < Integer.parseInt(operatorNum);
        if (isStringLessThen || isLongLessThen || isIntegerLessThen ||
                isShortLessThen || isFloatLessThen || isDoubleLessThen || isCollectionLessThen) {
            isLessThan = Boolean.TRUE;
        }
        return isLessThan;
    }

    /**
     * is less than or equal to
     *
     * @param value       field value
     * @param operatorNum operatorNum
     * @return is less than or equal to
     */
    private static Boolean isLessThanEqual(Object value, String operatorNum) {
        Boolean isLessThanEqual = Boolean.FALSE;
        if (value == null) {
            return Boolean.FALSE;
        }
        boolean isStringLessThenEqual = (value instanceof String) && ((String) value).length() <= Integer.parseInt(operatorNum);
        boolean isLongLessThenEqual = (value instanceof Long) && ((Long) value) <= Long.parseLong(operatorNum);
        boolean isIntegerLessThenEqual = (value instanceof Integer) && ((Integer) value) <= Integer.parseInt(operatorNum);
        boolean isShortLessThenEqual = (value instanceof Short) && ((Short) value) <= Short.parseShort(operatorNum);
        boolean isFloatLessThenEqual = (value instanceof Float) && ((Float) value) <= Float.parseFloat(operatorNum);
        boolean isDoubleLessThenEqual = (value instanceof Double) && ((Double) value) <= Double.parseDouble(operatorNum);
        boolean isCollectionLessThenEqual = (value instanceof Collection) && ((Collection<?>) value).size() <= Integer.parseInt(operatorNum);
        if (isStringLessThenEqual || isLongLessThenEqual || isIntegerLessThenEqual ||
                isShortLessThenEqual || isFloatLessThenEqual || isDoubleLessThenEqual || isCollectionLessThenEqual) {
            isLessThanEqual = Boolean.TRUE;
        }
        return isLessThanEqual;
    }

    /**
     * is not equal
     *
     * @param value       field value
     * @param operatorNum operatorNum
     * @return is not equal
     */
    private static Boolean isNotEqual(Object value, String operatorNum) {
        Boolean isNotEqual = Boolean.FALSE;
        if (value == null) {
            return Boolean.FALSE;
        }
        boolean isStringNotEqual = (value instanceof String) && !value.equals(operatorNum);
        boolean isLongNotEqual = (value instanceof Long) && !value.equals(Long.valueOf(operatorNum));
        boolean isIntegerNotEqual = (value instanceof Integer) && !value.equals(Integer.valueOf(operatorNum));
        boolean isShortNotEqual = (value instanceof Short) && !value.equals(Short.valueOf(operatorNum));
        boolean isFloatNotEqual = (value instanceof Float) && !value.equals(Float.valueOf(operatorNum));
        boolean isDoubleNotEqual = (value instanceof Double) && !value.equals(Double.valueOf(operatorNum));
        boolean isCollectionNotEqual = (value instanceof Collection) && ((Collection<?>) value).size() != Integer.parseInt(operatorNum);
        if (isStringNotEqual || isLongNotEqual || isIntegerNotEqual ||
                isShortNotEqual || isFloatNotEqual || isDoubleNotEqual || isCollectionNotEqual) {
            isNotEqual = Boolean.TRUE;
        }
        return isNotEqual;
    }

    /**
     * is meets the parameter rules
     *
     * @param method    method
     * @param arguments arguments
     * @return is meets
     */
    private MergedAnnotation<Check> isCheck(Method method, Object[] arguments) {
        final MergedAnnotation<Check> mergedAnnotation = findCheckAnnotation(method);
        if (!mergedAnnotation.isPresent() || arguments == null) {
            return MergedAnnotation.missing();
        }
        return mergedAnnotation;
    }

    private static MergedAnnotation<Check> findCheckAnnotation(final Method method) {
        final MergedAnnotation<Check> methodAnnotation = MergedAnnotations.from(method).get(Check.class);
        if (methodAnnotation.isPresent()) {
            return methodAnnotation;
        }
        // try find from target class, we assume the method is not declared in proxy classes.
        final MergedAnnotation<Check> classAnnotation = MergedAnnotations.from(method.getDeclaringClass()).get(Check.class);
        if (classAnnotation.isPresent()) {
            return classAnnotation;
        }
        return MergedAnnotation.missing();
    }

    /**
     * file info
     */
    static class FieldInfo {
        /**
         * field
         */
        String field;
        /**
         * prompt message
         */
        String innerMsg;
        /**
         * operator
         */
        String operator;
        /**
         * num of operator
         */
        String operatorNum;
        /**
         * enum of operator
         */
        Operator optEnum;
    }

    /**
     * enum of operator
     */
    enum Operator {
        /**
         * spel expression
         */
        SPEL("match spel expression", null),
        /**
         * GreaterThan
         */
        GREATER_THAN(">", CheckerInterceptor::isGreaterThan),
        /**
         * GreaterThanEqual
         */
        GREATER_THAN_EQUAL(">=", CheckerInterceptor::isGreaterThanEqual),
        /**
         * LessThan
         */
        LESS_THAN("<", CheckerInterceptor::isLessThan),
        /**
         * LessThanEqual
         */
        LESS_THAN_EQUAL("<=", CheckerInterceptor::isLessThanEqual),
        /**
         * NotEqual
         */
        NOT_EQUAL("!=", CheckerInterceptor::isNotEqual),
        /**
         * NotNull
         */
        NOT_NULL("not null", CheckerInterceptor::isNotNull);

        private final String value;
        private final BiFunction<Object, String, Boolean> fun;

        Operator(String value, BiFunction<Object, String, Boolean> fun) {
            this.value = value;
            this.fun = fun;
        }
    }

}
