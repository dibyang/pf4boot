package net.xdob.pf4boot;

import java.util.Optional;

public abstract class TypeWrapper {
  public static  <T> Optional<T> wrapper(Object obj, Class<T> clazz){
    T value = null;
    if(clazz.isInstance(obj)){
      value = (T)obj;
    }
    return Optional.ofNullable(value);
  }

}
