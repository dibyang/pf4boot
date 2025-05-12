package net.xdob.pf4boot.modal;

import org.springframework.cglib.proxy.InvocationHandler;

import java.lang.reflect.Method;
import java.util.Objects;

public class SharingBean {
	private final String beanName;
	private final Object bean;
	private final SharingScope scope;


	public SharingBean(String beanName, Object bean, SharingScope scope) {
		this.beanName = beanName;
		this.bean = bean;
		this.scope = scope;
	}

	public String getBeanName() {
		return beanName;
	}

	public Object getBean() {
		return bean;
	}

	public SharingScope getScope() {
		return scope;
	}

	public static  SharingBean of(String beanName, Object bean, SharingScope scope){
		return new SharingBean(beanName, bean, scope);
	}

	public static  SharingBean root(String beanName, Object bean){
		return of(beanName, bean, SharingScope.ROOT);
	}

	public static  SharingBean platform(String beanName, Object bean){
		return of(beanName, bean, SharingScope.PLATFORM);
	}

	public static  SharingBean application(String beanName, Object bean){
		return of(beanName, bean, SharingScope.APPLICATION);
	}

	@Override
	public boolean equals(Object object) {
		if (object == null || getClass() != object.getClass()) return false;
		SharingBean that = (SharingBean) object;
		return Objects.equals(beanName, that.beanName);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(beanName);
	}
}
