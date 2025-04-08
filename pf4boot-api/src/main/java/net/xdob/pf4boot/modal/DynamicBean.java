package net.xdob.pf4boot.modal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DynamicBean {
  private final Object bean;
  private final List<AutowiredElement> elements = new ArrayList<>();

  public DynamicBean(Object bean) {
    this.bean = bean;
  }

  public Object getBean() {
    return bean;
  }

  public List<AutowiredElement> getElements() {
    return elements;
  }

  public Optional<AutowiredElement> findElement(Class<?> elementClass){
    return elements.stream().filter(element -> element.match(elementClass))
        .findFirst();
  }
}
