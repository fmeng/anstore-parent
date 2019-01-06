package me.fmeng.anstore;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 统一外部访问视图
 *
 * @author fmeng
 * @since 2018/01/25
 */
@ToString
@EqualsAndHashCode
public class MarkedUnit {

    @Getter
    private Class<? extends Annotation> annotationClass;
    @Getter
    private Class<?> markedClass;
    @Getter
    private Field field;
    @Getter
    private Method method;
    @Getter
    private Annotation annotation;

    static final int STORE_LOCATION_CLASS = 1;
    static final int STORE_LOCATION_FIELD = 2;
    static final int STORE_LOCATION_METHOD = 3;

    /**
     * 注解标记的位置 1:类上,2:属性上,3:方法上
     */
    private int storeLocation;

    public boolean isClassMarked() {
        return storeLocation == STORE_LOCATION_CLASS;
    }

    public boolean isFieldMarked() {
        return storeLocation == STORE_LOCATION_FIELD;
    }

    public boolean isMethodMarked() {
        return storeLocation == STORE_LOCATION_METHOD;
    }


    @java.beans.ConstructorProperties({"annotationClass", "markedClass", "field", "method", "annotation", "storeLocation"})
    MarkedUnit(Class<? extends Annotation> annotationClass, Class<?> markedClass, Field field, Method method, Annotation annotation, int storeLocation) {
        this.annotationClass = annotationClass;
        this.markedClass = markedClass;
        this.field = field;
        this.method = method;
        this.annotation = annotation;
        this.storeLocation = storeLocation;
    }
}
