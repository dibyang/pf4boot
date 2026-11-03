package net.xdob.pf4boot;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.PluginDisposableBeanAdapter;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import net.xdob.pf4boot.util.PluginCleanup;
import java.util.stream.Collectors;

/**
 * PluginListableBeanFactory
 *
 * @author yangzj
 * @version 1.0
 */
public class PluginListableBeanFactory extends DefaultListableBeanFactory {

  private ClassLoader classLoader;
  private final PluginCleanup destructionFailures = new PluginCleanup();

  public PluginListableBeanFactory(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  /** Spring 在销毁中吞异常前，记录实际回调的失败。 */
  @Override
  public void registerDisposableBean(String beanName, DisposableBean bean) {
    super.registerDisposableBean(beanName, () -> PluginDisposableBeanAdapter.destroy(bean, destructionFailures));
  }

  /** 直接注册的插件实例也走相同的销毁观察路径。 */
  @Override
  public void destroyBean(Object existingBean) {
    PluginDisposableBeanAdapter.destroyInstance(existingBean, getBeanPostProcessors().stream()
        .filter(processor -> processor instanceof DestructionAwareBeanPostProcessor)
        .map(processor -> (DestructionAwareBeanPostProcessor) processor).collect(Collectors.toList()),
        getAccessControlContext(), destructionFailures);
  }

  /** 上下文关闭后统一报告失败，避免把尚未关闭的资源视为已释放。 */
  public void verifyDestruction() { destructionFailures.throwIfFailed(); }

  /** 只重试此前失败的实际销毁动作。 */
  public void retryDestruction() { destructionFailures.retry(); }

  protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
    /*
    try {
      return classLoader.loadClass(beanName);
    } catch (ClassNotFoundException ignored) {
    } // */
    return super.predictBeanType(beanName, mbd, typesToMatch);
  }

}
