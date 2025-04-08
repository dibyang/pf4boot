package net.xdob.pf4boot.util;

import net.xdob.pf4boot.modal.AutowiredElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

public class Injections {
  static final Logger logger = LoggerFactory.getLogger(Injections.class);
  private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>(4);

  private String requiredParameterName = "required";

  private boolean requiredParameterValue = true;


  public Injections() {
    this.autowiredAnnotationTypes.add(Autowired.class);
    this.autowiredAnnotationTypes.add(Value.class);
    try {
      this.autowiredAnnotationTypes.add((Class<? extends Annotation>)
          ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
      logger.trace("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
    }
    catch (ClassNotFoundException ex) {
      // JSR-330 API not available - simply skip.
    }
  }

  public List<AutowiredElement> buildAutowiringMetadata(Class<?> clazz) {

    final List<AutowiredElement> elements = new ArrayList<>();
    if (!AnnotationUtils.isCandidateClass(clazz, this.autowiredAnnotationTypes)) {
      return elements;
    }
    Class<?> targetClass = clazz;

    do {
      final List<AutowiredElement> fieldElements = new ArrayList<>();
      ReflectionUtils.doWithLocalFields(targetClass, field -> {
        MergedAnnotation<?> ann = findAutowiredAnnotation(field);
        if (ann != null) {
          if (Modifier.isStatic(field.getModifiers())) {
            if (logger.isInfoEnabled()) {
              logger.info("Autowired annotation is not supported on static fields: " + field);
            }
            return;
          }
          boolean required = determineRequiredStatus(ann);
          fieldElements.add(new AutowiredElement(field.getName(), field.getType(), required));
        }
      });

      final List<AutowiredElement> methodElements = new ArrayList<>();
      ReflectionUtils.doWithLocalMethods(targetClass, method -> {
        Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
        if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
          return;
        }
        MergedAnnotation<?> ann = findAutowiredAnnotation(bridgedMethod);
        if (ann != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
          if (Modifier.isStatic(method.getModifiers())) {
            if (logger.isInfoEnabled()) {
              logger.info("Autowired annotation is not supported on static methods: " + method);
            }
            return;
          }
          if (method.getParameterCount() == 0) {
            if (logger.isInfoEnabled()) {
              logger.info("Autowired annotation should only be used on methods with parameters: " +
                  method);
            }
          }
          boolean required = determineRequiredStatus(ann);
          PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
          methodElements.add(new AutowiredElement(pd.getName(), pd.getPropertyType(), required));
        }
      });

      elements.addAll(0, methodElements);
      elements.addAll(0, fieldElements);
      targetClass = targetClass.getSuperclass();
    }
    while (targetClass != null && targetClass != Object.class);

    return elements;
  }


  @Nullable
  private MergedAnnotation<?> findAutowiredAnnotation(AccessibleObject ao) {
    MergedAnnotations annotations = MergedAnnotations.from(ao);
    for (Class<? extends Annotation> type : this.autowiredAnnotationTypes) {
      MergedAnnotation<?> annotation = annotations.get(type);
      if (annotation.isPresent()) {
        return annotation;
      }
    }
    return null;
  }

  protected boolean determineRequiredStatus(MergedAnnotation<?> ann) {
    return determineRequiredStatus(ann.<AnnotationAttributes> asMap(
        mergedAnnotation -> new AnnotationAttributes(mergedAnnotation.getType())));
  }

  protected boolean determineRequiredStatus(AnnotationAttributes ann) {
    return (!ann.containsKey(this.requiredParameterName) ||
        this.requiredParameterValue == ann.getBoolean(this.requiredParameterName));
  }

}
