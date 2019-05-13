package cn.ciphermagic.common.checker;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.ast.Operator;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * aspect for param check
 *
 * @author: CipherCui
 */
@Aspect
public class Checker {

    // -====================== constant =========================

    private static final String SPLITOR = ":";
    private static final String FIELD_SPLITOR = ",";

    // -====================== log =========================

    private static final Logger LOG = LoggerFactory.getLogger(Checker.class);

    private ExpressionParser parser = new SpelExpressionParser();
    private LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();
    private Function<String, Object> unsuccess;

    private Checker() {
    }

    /**
     * Action performed when check fails
     *
     * @param unsuccess lambda of the action
     */
    public void setUnsuccess(Function<String, Object> unsuccess) {
        this.unsuccess = unsuccess;
    }

    /**
     * checker builder
     */
    public static class Builder {
        private Checker checker = new Checker();

        public Builder unsuccess(Function<String, Object> unsuccess) {
            checker.setUnsuccess(unsuccess);
            return this;
        }

        public Checker build() {
            return checker;
        }
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

    /**
     * aop around the method
     *
     * @param point ProceedingJoinPoint
     * @return method result
     * @throws Throwable method exception
     */
    @Around(value = "@annotation(cn.ciphermagic.common.checker.Check)")
    public Object check(ProceedingJoinPoint point) throws Throwable {
        Object obj;
        // check param
        String msg = doCheck(point);
        if (!StringUtils.isEmpty(msg)) {
            return unsuccess.apply(msg);
        }
        obj = point.proceed();
        return obj;
    }

    /**
     * check param
     *
     * @param point ProceedingJoinPoint
     * @return error message
     */
    private String doCheck(ProceedingJoinPoint point) throws SQLException {
        // get arguments
        Object[] arguments = point.getArgs();
        // get method
        Method method = getMethod(point);
        String methodInfo = StringUtils.isEmpty(method.getName()) ? "" : " while calling " + method.getName();
        String msg = "";
        if (isCheck(method, arguments)) {
            Check annotation = method.getAnnotation(Check.class);
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
                        Object value = ReflectionUtils.invokeJdbcMethod(getMethod, vo);
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
        for (int len = 0; len < params.length; len++) {
            context.setVariable(params[len], arguments[len]);
        }
        try {
            Expression expression = parser.parseExpression(spel);
            return expression.getValue(context, Boolean.class);
        } catch (Exception e) {
            LOG.error("", e);
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
        if (fieldStr.contains(SPLITOR)) {
            if (fieldStr.split(SPLITOR).length == 2) {
                innerMsg = fieldStr.split(SPLITOR)[1].trim();
                fieldStr = fieldStr.split(SPLITOR)[0].trim();
            } else {
                throw new IllegalArgumentException("@Check annotation error: " + fieldStr);
            }
        }
        // parse operator
        if (fieldStr.startsWith("#")) {
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
        Boolean isStringNull = (value instanceof String) && StringUtils.isEmpty((String) value);
        Boolean isCollectionNull = (value instanceof Collection) && CollectionUtils.isEmpty((Collection) value);
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
        Boolean isStringGreaterThen = (value instanceof String) && ((String) value).length() > Integer.valueOf(operatorNum);
        Boolean isLongGreaterThen = (value instanceof Long) && ((Long) value) > Long.valueOf(operatorNum);
        Boolean isIntegerGreaterThen = (value instanceof Integer) && ((Integer) value) > Integer.valueOf(operatorNum);
        Boolean isShortGreaterThen = (value instanceof Short) && ((Short) value) > Short.valueOf(operatorNum);
        Boolean isFloatGreaterThen = (value instanceof Float) && ((Float) value) > Float.valueOf(operatorNum);
        Boolean isDoubleGreaterThen = (value instanceof Double) && ((Double) value) > Double.valueOf(operatorNum);
        Boolean isCollectionGreaterThen = (value instanceof Collection) && ((Collection) value).size() > Integer.valueOf(operatorNum);
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
        Boolean isStringGreaterThenEqual = (value instanceof String) && ((String) value).length() >= Integer.valueOf(operatorNum);
        Boolean isLongGreaterThenEqual = (value instanceof Long) && ((Long) value) >= Long.valueOf(operatorNum);
        Boolean isIntegerGreaterThenEqual = (value instanceof Integer) && ((Integer) value) >= Integer.valueOf(operatorNum);
        Boolean isShortGreaterThenEqual = (value instanceof Short) && ((Short) value) >= Short.valueOf(operatorNum);
        Boolean isFloatGreaterThenEqual = (value instanceof Float) && ((Float) value) >= Float.valueOf(operatorNum);
        Boolean isDoubleGreaterThenEqual = (value instanceof Double) && ((Double) value) >= Double.valueOf(operatorNum);
        Boolean isCollectionGreaterThenEqual = (value instanceof Collection) && ((Collection) value).size() >= Integer.valueOf(operatorNum);
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
        Boolean isStringLessThen = (value instanceof String) && ((String) value).length() < Integer.valueOf(operatorNum);
        Boolean isLongLessThen = (value instanceof Long) && ((Long) value) < Long.valueOf(operatorNum);
        Boolean isIntegerLessThen = (value instanceof Integer) && ((Integer) value) < Integer.valueOf(operatorNum);
        Boolean isShortLessThen = (value instanceof Short) && ((Short) value) < Short.valueOf(operatorNum);
        Boolean isFloatLessThen = (value instanceof Float) && ((Float) value) < Float.valueOf(operatorNum);
        Boolean isDoubleLessThen = (value instanceof Double) && ((Double) value) < Double.valueOf(operatorNum);
        Boolean isCollectionLessThen = (value instanceof Collection) && ((Collection) value).size() < Integer.valueOf(operatorNum);
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
        Boolean isStringLessThenEqual = (value instanceof String) && ((String) value).length() <= Integer.valueOf(operatorNum);
        Boolean isLongLessThenEqual = (value instanceof Long) && ((Long) value) <= Long.valueOf(operatorNum);
        Boolean isIntegerLessThenEqual = (value instanceof Integer) && ((Integer) value) <= Integer.valueOf(operatorNum);
        Boolean isShortLessThenEqual = (value instanceof Short) && ((Short) value) <= Short.valueOf(operatorNum);
        Boolean isFloatLessThenEqual = (value instanceof Float) && ((Float) value) <= Float.valueOf(operatorNum);
        Boolean isDoubleLessThenEqual = (value instanceof Double) && ((Double) value) <= Double.valueOf(operatorNum);
        Boolean isCollectionLessThenEqual = (value instanceof Collection) && ((Collection) value).size() <= Integer.valueOf(operatorNum);
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
        Boolean isStringNotEqual = (value instanceof String) && !value.equals(operatorNum);
        Boolean isLongNotEqual = (value instanceof Long) && !value.equals(Long.valueOf(operatorNum));
        Boolean isIntegerNotEqual = (value instanceof Integer) && !value.equals(Integer.valueOf(operatorNum));
        Boolean isShortNotEqual = (value instanceof Short) && !value.equals(Short.valueOf(operatorNum));
        Boolean isFloatNotEqual = (value instanceof Float) && !value.equals(Float.valueOf(operatorNum));
        Boolean isDoubleNotEqual = (value instanceof Double) && !value.equals(Double.valueOf(operatorNum));
        Boolean isCollectionNotEqual = (value instanceof Collection) && ((Collection) value).size() != Integer.valueOf(operatorNum);
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
    private Boolean isCheck(Method method, Object[] arguments) {
        Boolean isCheck = Boolean.TRUE;
        if (!method.isAnnotationPresent(Check.class) || arguments == null) {
            isCheck = Boolean.FALSE;
        }
        return isCheck;
    }

    /**
     * get the method
     *
     * @param joinPoint ProceedingJoinPoint
     * @return method
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.getDeclaringClass().isInterface()) {
            try {
                method = joinPoint
                        .getTarget()
                        .getClass()
                        .getDeclaredMethod(joinPoint.getSignature().getName(), method.getParameterTypes());
            } catch (SecurityException | NoSuchMethodException e) {
                LOG.error("" + e);
            }
        }
        return method;
    }

    /**
     * file info
     */
    class FieldInfo {
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
        GREATER_THAN(">", Checker::isGreaterThan),
        /**
         * GreaterThanEqual
         */
        GREATER_THAN_EQUAL(">=", Checker::isGreaterThanEqual),
        /**
         * LessThan
         */
        LESS_THAN("<", Checker::isLessThan),
        /**
         * LessThanEqual
         */
        LESS_THAN_EQUAL("<=", Checker::isLessThanEqual),
        /**
         * NotEqual
         */
        NOT_EQUAL("!=", Checker::isNotEqual),
        /**
         * NotNull
         */
        NOT_NULL("not null", Checker::isNotNull);

        private String value;
        private BiFunction<Object, String, Boolean> fun;

        Operator(String value, BiFunction<Object, String, Boolean> fun) {
            this.value = value;
            this.fun = fun;
        }
    }

}
