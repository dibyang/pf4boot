package net.xdob.pf4boot.modal;

import net.xdob.pf4boot.annotation.PluginStarter;

import java.util.Objects;

public class SharingBean {
	private final String beanName;
	private final Object bean;
	private final SharingScope scope;
	private final String group;


	public SharingBean(String beanName, Object bean, SharingScope scope, String group) {
		this.beanName = beanName;
		this.bean = bean;
		this.scope = scope;
		this.group = group;
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

	public String getGroup() {
		return group;
	}

	public static  SharingBean of(String beanName, Object bean, SharingScope scope, String group){
		return new SharingBean(beanName, bean, scope, group);
	}

	public static  SharingBean root(String beanName, Object bean){
		return of(beanName, bean, SharingScope.ROOT, PluginStarter.EMPTY);
	}

	public static  SharingBean platform(String beanName, Object bean, String group){
		return of(beanName, bean, SharingScope.PLATFORM, group);
	}

	public static  SharingBean application(String beanName, Object bean){
		return of(beanName, bean, SharingScope.APPLICATION, PluginStarter.EMPTY);
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
