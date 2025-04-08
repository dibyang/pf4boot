package net.xdob.pf4boot.modal;

import java.lang.reflect.Field;

public class AutowiredElement  {

  private final boolean required;
  private final String propertyName;
  private final Class<?> propertyType;


  public AutowiredElement(String propertyName, Class<?> propertyType, boolean required) {
    this.required = required;
    this.propertyName = propertyName;
    this.propertyType = propertyType;
  }

  public boolean isRequired() {
    return required;
  }

  public String getPropertyName() {
    return propertyName;
  }

  public Class<?> getPropertyType() {
    return propertyType;
  }

  public boolean match(Class<?> elementClass){
    return propertyType.isAssignableFrom(elementClass);
  }
}
