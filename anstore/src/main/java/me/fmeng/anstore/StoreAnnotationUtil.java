package me.fmeng.anstore;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 存储注解关系,不可变对象
 *
 * @author fmeng
 * @since 2018/01/25
 */
@Slf4j
@SuppressWarnings("all")
public class StoreAnnotationUtil {

    private static final String BASE_PACKAGE;
    public static final String SCAN_PACKAGE_ARGS_NAME = "storeanns.base.package";
    public static final Multimap<Class<? extends Annotation>, Class<?>> ANNOTATION_CLASS_MAP;
    public static final Multimap<Class<? extends Annotation>, Field> ANNOTATION_FIELD_MAP;
    public static final Multimap<Class<? extends Annotation>, Method> ANNOTATION_METHOD_MAP;
    public static final Set<MarkedUnit> MARKED_UNITS;

    static {
        String appScanPackage = System.getProperty(SCAN_PACKAGE_ARGS_NAME);
        if (appScanPackage == null || appScanPackage.isEmpty()) {
            appScanPackage = System.getProperty("app.base.package");
        }
        if (appScanPackage != null && !appScanPackage.isEmpty()) {
            // 要扫描的基础包
            BASE_PACKAGE = appScanPackage;
            log.info("扫描支持@StoreAnnotation包{}", BASE_PACKAGE);
            final boolean detectInheritedAnnotationDisable = true;

            // 设置符合条件的Finder
            Reflections finder = new Reflections(new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forPackage(BASE_PACKAGE))
                    .setScanners(new TypeAnnotationsScanner().filterResultsBy(new FilterBuilder().includePackage(BASE_PACKAGE))
                            , new MethodAnnotationsScanner().filterResultsBy(new FilterBuilder().includePackage(BASE_PACKAGE))
                            , new FieldAnnotationsScanner().filterResultsBy(new FilterBuilder().includePackage(BASE_PACKAGE))
                            , new SubTypesScanner().filterResultsBy(new FilterBuilder().includePackage(BASE_PACKAGE))
                    )
                    .filterInputsBy(new FilterBuilder().includePackage(BASE_PACKAGE))
            );

            // 查找所有被StoreAnnotation标注的注解
            Set<Class<?>> annotationClassSet = finder.getTypesAnnotatedWith(StoreAnnotation.class, detectInheritedAnnotationDisable);
            Set<Class<? extends Annotation>> annotationTypeClassSet = annotationClassSet.stream()
                    .filter(c -> Annotation.class.isAssignableFrom(c))
                    .map(c -> (Class<? extends Annotation>) c)
                    .collect(Collectors.toSet());
            if (annotationTypeClassSet != null && !annotationClassSet.isEmpty()) {
                log.info("找到@StoreAnnotation标记的注解annotationTypeClassSet={}", annotationTypeClassSet);
                Multimap<Class<? extends Annotation>, Class<?>> tempClassMap = MultimapBuilder.hashKeys(5).hashSetValues(5).build();
                Multimap<Class<? extends Annotation>, Field> tempFieldMap = MultimapBuilder.hashKeys(5).hashSetValues(5).build();
                Multimap<Class<? extends Annotation>, Method> tempMethodMap = MultimapBuilder.hashKeys(5).hashSetValues(5).build();
                for (Class<? extends Annotation> ac : annotationTypeClassSet) {
                    // 业务注解可以注解的位置
                    Set<ElementType> elementTypes = Sets.newHashSet(ac.getAnnotation(Target.class).value());
                    // 在类上使用注解
                    if (elementTypes.contains(ElementType.TYPE)) {
                        Set<Class<?>> markedClassSet = finder.getTypesAnnotatedWith(ac, detectInheritedAnnotationDisable);
                        if (markedClassSet != null && !markedClassSet.isEmpty()) {
                            tempClassMap.putAll(ac, markedClassSet);
                            log.info("{}注解的类markedClassSet={}", ac, markedClassSet);
                        }
                    }
                    // 在属性上注解
                    if (elementTypes.contains(ElementType.FIELD)) {
                        Set<Field> markedFieldSet = finder.getFieldsAnnotatedWith(ac);
                        if (markedFieldSet != null && !markedFieldSet.isEmpty()) {
                            tempFieldMap.putAll(ac, markedFieldSet);
                            log.info("{}注解的属性markedFieldSet={}", ac, markedFieldSet);
                        }
                    }
                    // 在方法注解
                    if (elementTypes.contains(ElementType.METHOD)) {
                        Set<Method> markedMethodSet = finder.getMethodsAnnotatedWith(ac);
                        if (markedMethodSet != null && !markedMethodSet.isEmpty()) {
                            tempMethodMap.putAll(ac, markedMethodSet);
                            log.info("{}注解的方法markedMethodSet={}", ac, markedMethodSet);
                        }
                    }
                }
                // 构建不可变结构
                ANNOTATION_CLASS_MAP = ImmutableMultimap.copyOf(tempClassMap);
                ANNOTATION_FIELD_MAP = ImmutableMultimap.copyOf(tempFieldMap);
                ANNOTATION_METHOD_MAP = ImmutableMultimap.copyOf(tempMethodMap);
                Set<MarkedUnit> tempMarkedUnits = Sets.newLinkedHashSetWithExpectedSize(30);
                ANNOTATION_CLASS_MAP.forEach((a, c) -> tempMarkedUnits.add(new MarkedUnit(a, c, null, null, c.getAnnotation(a), MarkedUnit.STORE_LOCATION_CLASS)));
                ANNOTATION_FIELD_MAP.forEach((a, f) -> tempMarkedUnits.add(new MarkedUnit(a, f.getDeclaringClass(), f, null, f.getAnnotation(a), MarkedUnit.STORE_LOCATION_FIELD)));
                ANNOTATION_METHOD_MAP.forEach((a, m) -> tempMarkedUnits.add(new MarkedUnit(a, m.getDeclaringClass(), null, m, m.getAnnotation(a), MarkedUnit.STORE_LOCATION_METHOD)));
                MARKED_UNITS = ImmutableSet.copyOf(tempMarkedUnits);
            } else {
                log.warn("没有找到被@StoreAnnotation标记的注解");
                ANNOTATION_CLASS_MAP = ImmutableMultimap.of();
                ANNOTATION_FIELD_MAP = ImmutableMultimap.of();
                ANNOTATION_METHOD_MAP = ImmutableMultimap.of();
                MARKED_UNITS = ImmutableSet.of();
            }
        } else {
            log.warn("没有设置参数{}, 工具类不可用!, 请设置参数-D{}=base.san.package", SCAN_PACKAGE_ARGS_NAME, SCAN_PACKAGE_ARGS_NAME);
            BASE_PACKAGE = "";
            ANNOTATION_CLASS_MAP = ImmutableMultimap.of();
            ANNOTATION_FIELD_MAP = ImmutableMultimap.of();
            ANNOTATION_METHOD_MAP = ImmutableMultimap.of();
            MARKED_UNITS = ImmutableSet.of();
        }
    }
}
