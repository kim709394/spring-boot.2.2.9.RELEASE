/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link DeferredImportSelector} to handle {@link EnableAutoConfiguration
 * auto-configuration}. This class can also be subclassed if a custom variant of
 * {@link EnableAutoConfiguration @EnableAutoConfiguration} is needed.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Madhura Bhave
 * @since 1.3.0
 * @see EnableAutoConfiguration
 */
/**
 * 实现了DeferredImportSelector接口和各种spring的Aware组件，是一个bean的注册器，通过实现各种Aware组件
 * 通过spring回调获得bean单例池、spring容器、类加载器等各种组件
 * 实现DeferredImportSelector接口，先调用getImportGroup()方法返回Group实现类对象，
 * 再调用Group实现类对象的process()方法，然后调用selectImports()方法，完成bean的相关注册
 * 同时实现了ImportSelector接口，将调用selectImports(AnnotationMetadata annotationMetadata)
 * 方法完成bean的注册
 */

public class AutoConfigurationImportSelector implements DeferredImportSelector, BeanClassLoaderAware,
		ResourceLoaderAware, BeanFactoryAware, EnvironmentAware, Ordered {

	private static final AutoConfigurationEntry EMPTY_ENTRY = new AutoConfigurationEntry();

	private static final String[] NO_IMPORTS = {};

	private static final Log logger = LogFactory.getLog(AutoConfigurationImportSelector.class);

	private static final String PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE = "spring.autoconfigure.exclude";

	private ConfigurableListableBeanFactory beanFactory;

	private Environment environment;

	private ClassLoader beanClassLoader;

	private ResourceLoader resourceLoader;

	// 返回注解元数据字符串提供注册bean
	@Override
	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		if (!isEnabled(annotationMetadata)) {
			return NO_IMPORTS;
		}
		AutoConfigurationMetadata autoConfigurationMetadata = AutoConfigurationMetadataLoader
				.loadMetadata(this.beanClassLoader);
		// 经过一系列的去重和排除以及过滤后得到最终有效的自动配置类集合
		// 和要排除的自动配置类集合的封装对象AutoConfigurationEntry
		AutoConfigurationEntry autoConfigurationEntry = getAutoConfigurationEntry(autoConfigurationMetadata,
				annotationMetadata);
		// 最终返回有效自动配置类给spring容器进行注册
		return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());
	}

	/**
	 * Return the {@link AutoConfigurationEntry} based on the {@link AnnotationMetadata}
	 * of the importing {@link Configuration @Configuration} class.
	 * @param autoConfigurationMetadata the auto-configuration metadata
	 * @param annotationMetadata the annotation metadata of the configuration class
	 * @return the auto-configurations that should be imported
	 * 经过一系列的去重和排除以及过滤后得到最终有效的自动配置类集合和要排除的自动配置类集合的封装对象AutoConfigurationEntry
	 */
	protected AutoConfigurationEntry getAutoConfigurationEntry(AutoConfigurationMetadata autoConfigurationMetadata,
			AnnotationMetadata annotationMetadata) {
		if (!isEnabled(annotationMetadata)) {
			return EMPTY_ENTRY;
		}
		// 获取所有注解元数据属性值
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
		// 获取/META-INF/spring.factories配置文件的自动配置类全路径 名，封装成一个字符串集合
		List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);
		// 利用LinkedHashSet移除重复的配置类
		configurations = removeDuplicates(configurations);
		// 获得要排除的自动配置类，比如exclude的配置类，例如：@SpringBootApplication(exclude=
		// RedisAutoConfiguration.class)
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		// 检查要排除的配置类，对于不属于自动配置类则抛出异常
		checkExcludedClasses(configurations, exclusions);
		// 删除排除的配置类
		configurations.removeAll(exclusions);
		/**
		 * 过滤自动配置类的@ConditionalOnClass,@ConditionalOnBean和@ConditionalOnWebApplication三种注解
		 * 之后的满足条件的自动配置类的结果集合
		 */
		configurations = filter(configurations, autoConfigurationMetadata);
		/**
		 * 获取了符合条件的自动配置类后，此时触发AutoConfigurationImportEvent事件，
		 * 目的是告诉ConditionEvaluationReport条件评估报告器对象来记录符合条件的自动配置类
		 * 该事件在刷新容器时调用invokeBeanFactoryPostProcessors后置处理器时触发
		 */
		fireAutoConfigurationImportEvents(configurations, exclusions);
		// 将符合条件和要排除的自动配置类封装进AutoConfigurationEntry对象，并返回
		return new AutoConfigurationEntry(configurations, exclusions);
	}

	/**
	 * 返回导入的group，返回的这个group接口实现org.springframework.context.annotation.DeferredImportSelector.Group接口
	 * 再调用group接口的process方法进行外部bean的注册处理
	 *
	 */
	@Override
	public Class<? extends Group> getImportGroup() {
		return AutoConfigurationGroup.class;
	}

	protected boolean isEnabled(AnnotationMetadata metadata) {
		if (getClass() == AutoConfigurationImportSelector.class) {
			return getEnvironment().getProperty(EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY, Boolean.class, true);
		}
		return true;
	}

	/**
	 * Return the appropriate {@link AnnotationAttributes} from the
	 * {@link AnnotationMetadata}. By default this method will return attributes for
	 * {@link #getAnnotationClass()}.
	 * @param metadata the annotation metadata
	 * @return annotation attributes
	 */
	protected AnnotationAttributes getAttributes(AnnotationMetadata metadata) {
		String name = getAnnotationClass().getName();
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(name, true));
		Assert.notNull(attributes, () -> "No auto-configuration attributes found. Is " + metadata.getClassName()
				+ " annotated with " + ClassUtils.getShortName(name) + "?");
		return attributes;
	}

	/**
	 * Return the source annotation class used by the selector.
	 * @return the annotation class
	 */
	protected Class<?> getAnnotationClass() {
		return EnableAutoConfiguration.class;
	}

	/**
	 * Return the auto-configuration class names that should be considered. By default
	 * this method will load candidates using {@link SpringFactoriesLoader} with
	 * {@link #getSpringFactoriesLoaderFactoryClass()}.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return a list of candidate configurations
	 */
	protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
		/**
		 * getSpringFactoriesLoaderFactoryClass()方法获取自动配置类注解：
		 * org.springframework.boot.autoconfigure.EnableAutoConfiguration.class
		 * SpringFactoriesLoader.loadFactoryNames()方法将加载spring.factories配置文件的所有配置类信息存进一个
		 * Map<String,List<String>>中，key值为配置文件的key，value为配置文件key对于的value字符串的集合，然后获取以key为
		 * "org.springframework.boot.autoconfigure.EnableAutoConfiguration"的value值的配置类的集合
		 */
		List<String> configurations = SpringFactoriesLoader.loadFactoryNames(getSpringFactoriesLoaderFactoryClass(),
				getBeanClassLoader());
		Assert.notEmpty(configurations, "No auto configuration classes found in META-INF/spring.factories. If you "
				+ "are using a custom packaging, make sure that file is correct.");
		return configurations;
	}

	/**
	 * Return the class used by {@link SpringFactoriesLoader} to load configuration
	 * candidates.
	 * @return the factory class
	 */
	protected Class<?> getSpringFactoriesLoaderFactoryClass() {
		return EnableAutoConfiguration.class;
	}

	private void checkExcludedClasses(List<String> configurations, Set<String> exclusions) {
		List<String> invalidExcludes = new ArrayList<>(exclusions.size());
		for (String exclusion : exclusions) {
			if (ClassUtils.isPresent(exclusion, getClass().getClassLoader()) && !configurations.contains(exclusion)) {
				invalidExcludes.add(exclusion);
			}
		}
		if (!invalidExcludes.isEmpty()) {
			handleInvalidExcludes(invalidExcludes);
		}
	}

	/**
	 * Handle any invalid excludes that have been specified.
	 * @param invalidExcludes the list of invalid excludes (will always have at least one
	 * element)
	 */
	protected void handleInvalidExcludes(List<String> invalidExcludes) {
		StringBuilder message = new StringBuilder();
		for (String exclude : invalidExcludes) {
			message.append("\t- ").append(exclude).append(String.format("%n"));
		}
		throw new IllegalStateException(String.format(
				"The following classes could not be excluded because they are not auto-configuration classes:%n%s",
				message));
	}

	/**
	 * Return any exclusions that limit the candidate configurations.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return exclusions or an empty set
	 */
	protected Set<String> getExclusions(AnnotationMetadata metadata, AnnotationAttributes attributes) {
		Set<String> excluded = new LinkedHashSet<>();
		excluded.addAll(asList(attributes, "exclude"));
		excluded.addAll(Arrays.asList(attributes.getStringArray("excludeName")));
		excluded.addAll(getExcludeAutoConfigurationsProperty());
		return excluded;
	}

	private List<String> getExcludeAutoConfigurationsProperty() {
		if (getEnvironment() instanceof ConfigurableEnvironment) {
			Binder binder = Binder.get(getEnvironment());
			return binder.bind(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class).map(Arrays::asList)
					.orElse(Collections.emptyList());
		}
		String[] excludes = getEnvironment().getProperty(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class);
		return (excludes != null) ? Arrays.asList(excludes) : Collections.emptyList();
	}

	private List<String> filter(List<String> configurations, AutoConfigurationMetadata autoConfigurationMetadata) {
		long startTime = System.nanoTime();
		// 将从spring.factories配置文件加载的所有自动配置类字符串转化为数组
		String[] candidates = StringUtils.toStringArray(configurations);
		// 定义个是否跳过的数组，boolean[]初始化值全部元素为false
		boolean[] skip = new boolean[candidates.length];
		// 标记一个是否存在至少一个自配配置类有跳过的变量值，初始化为false
		boolean skipped = false;
		/**
		 * getAutoConfigurationImportFilters()方法：
		 * 从spring.factories配置文件加载key为org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
		 * 的所有value值，即： org.springframework.boot.autoconfigure.condition.OnBeanCondition,\
		 * org.springframework.boot.autoconfigure.condition.OnClassCondition,\
		 * org.springframework.boot.autoconfigure.condition.OnWebApplicationCondition
		 * 这三个类，并且通过反射进行实例化，封装成AutoConfigurationImportFilter对象 然后进行遍历
		 */
		for (AutoConfigurationImportFilter filter : getAutoConfigurationImportFilters()) {
			/**
			 * 判断filter对象是否属于各种Aware，例如：BeanClassLoaderAware、BeanFactoryAware、EnvironmentAware
			 * ResourceLoaderAware，如果是则对其注入各种spring上下文或者环境变量上下文对象，例如：
			 * ((BeanClassLoaderAware) instance).setBeanClassLoader(this.beanClassLoader);
			 * ((BeanFactoryAware) instance).setBeanFactory(this.beanFactory);
			 * ((EnvironmentAware) instance).setEnvironment(this.environment);
			 * ((ResourceLoaderAware) instance).setResourceLoader(this.resourceLoader);
			 */
			invokeAwareMethods(filter);
			/**
			 * 将每个filter对所有自动配置类进行匹配,filter为OnBeanCondition、OnClassCondition、OnWebApplicationCondition
			 * 这里本质是获取自动配置类的 @ConditionalOnClass,@ConditionalOnBean
			 * 和@ConditionalOnWebApplication三种注解里面的对象在类路径或者spring容器或者spring上下文中是否存在
			 * 如果都存在则匹配为true，不存在则为false，只要有一个filter不匹配，都不能匹配成功 返回一个匹配变量数组
			 */
			boolean[] match = filter.match(candidates, autoConfigurationMetadata);
			// 遍历这个匹配变量数组
			for (int i = 0; i < match.length; i++) {
				/**
				 * 如果当前filter的当前自动配置类不匹配，则skip跳过变量数组记为true，当前自动配置类置为空
				 * skip[]跳过变量数组和candidates所有自动配置类数组的顺序一一对应
				 * 只要有一个不匹配，那么skipped至少存在跳过一个自动配置类标记变量记为true
				 */
				if (!match[i]) {
					skip[i] = true;
					candidates[i] = null;
					skipped = true;
				}
			}
		}
		// 如果skipped始终为false，意味着所有自动配置类都没有跳过，即都能匹配上，则之间返回原始的所有自动配置类集合
		if (!skipped) {
			return configurations;
		}
		// 定义一个所有自动配置类集合为匹配后的结果集合
		List<String> result = new ArrayList<>(candidates.length);
		// 遍历所有自动配置类集合，如果没有跳过的，意思是能够匹配上的，装进结果集合，得到的结果集合就是能够匹配上的
		// 所有自动配置类
		for (int i = 0; i < candidates.length; i++) {
			if (!skip[i]) {
				result.add(candidates[i]);
			}
		}
		// 打印日志
		if (logger.isTraceEnabled()) {
			int numberFiltered = configurations.size() - result.size();
			logger.trace("Filtered " + numberFiltered + " auto configuration class in "
					+ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");
		}
		/**
		 * 返回通过过滤自动配置类的@ConditionalOnClass,@ConditionalOnBean和@ConditionalOnWebApplication三种注解
		 * 之后的满足条件的自动配置类的结果集合
		 */
		return new ArrayList<>(result);
	}

	protected List<AutoConfigurationImportFilter> getAutoConfigurationImportFilters() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportFilter.class, this.beanClassLoader);
	}

	protected final <T> List<T> removeDuplicates(List<T> list) {
		return new ArrayList<>(new LinkedHashSet<>(list));
	}

	protected final List<String> asList(AnnotationAttributes attributes, String name) {
		String[] value = attributes.getStringArray(name);
		return Arrays.asList(value);
	}

	private void fireAutoConfigurationImportEvents(List<String> configurations, Set<String> exclusions) {
		List<AutoConfigurationImportListener> listeners = getAutoConfigurationImportListeners();
		if (!listeners.isEmpty()) {
			AutoConfigurationImportEvent event = new AutoConfigurationImportEvent(this, configurations, exclusions);
			for (AutoConfigurationImportListener listener : listeners) {
				invokeAwareMethods(listener);
				listener.onAutoConfigurationImportEvent(event);
			}
		}
	}

	protected List<AutoConfigurationImportListener> getAutoConfigurationImportListeners() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportListener.class, this.beanClassLoader);
	}

	private void invokeAwareMethods(Object instance) {
		if (instance instanceof Aware) {
			if (instance instanceof BeanClassLoaderAware) {
				((BeanClassLoaderAware) instance).setBeanClassLoader(this.beanClassLoader);
			}
			if (instance instanceof BeanFactoryAware) {
				((BeanFactoryAware) instance).setBeanFactory(this.beanFactory);
			}
			if (instance instanceof EnvironmentAware) {
				((EnvironmentAware) instance).setEnvironment(this.environment);
			}
			if (instance instanceof ResourceLoaderAware) {
				((ResourceLoaderAware) instance).setResourceLoader(this.resourceLoader);
			}
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory);
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}

	protected final ConfigurableListableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	protected ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	protected final Environment getEnvironment() {
		return this.environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	protected final ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	/**
	 * 实现DeferredImportSelector.Group接口的实现类，先执行 process(AnnotationMetadata
	 * annotationMetadata, DeferredImportSelector deferredImportSelector)方法
	 * 后执行selectImports() 方法
	 *
	 */

	private static class AutoConfigurationGroup
			implements DeferredImportSelector.Group, BeanClassLoaderAware, BeanFactoryAware, ResourceLoaderAware {

		private final Map<String, AnnotationMetadata> entries = new LinkedHashMap<>();

		private final List<AutoConfigurationEntry> autoConfigurationEntries = new ArrayList<>();

		private ClassLoader beanClassLoader;

		private BeanFactory beanFactory;

		private ResourceLoader resourceLoader;

		private AutoConfigurationMetadata autoConfigurationMetadata;

		@Override
		public void setBeanClassLoader(ClassLoader classLoader) {
			this.beanClassLoader = classLoader;
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public void setResourceLoader(ResourceLoader resourceLoader) {
			this.resourceLoader = resourceLoader;
		}

		/***
		 * bean的注册，自动配置入口方法
		 *
		 */
		@Override
		public void process(AnnotationMetadata annotationMetadata, DeferredImportSelector deferredImportSelector) {
			Assert.state(deferredImportSelector instanceof AutoConfigurationImportSelector,
					() -> String.format("Only %s implementations are supported, got %s",
							AutoConfigurationImportSelector.class.getSimpleName(),
							deferredImportSelector.getClass().getName()));

			/**
			 * getAutoConfigurationEntry()方法： * 经过一系列的去重和排除以及过滤后得到最终有效的自动配置类集合和要排除的自动配置类
			 * 集合的封装对象AutoConfigurationEntry
			 * getAutoConfigurationMetadata()方法加载所有类路径下的META-INF/spring-autoconfigure-metadata.properties
			 * 配置文件的自动配置元数据内容
			 */
			AutoConfigurationEntry autoConfigurationEntry = ((AutoConfigurationImportSelector) deferredImportSelector)
					.getAutoConfigurationEntry(getAutoConfigurationMetadata(), annotationMetadata);
			// 将自动配置内容放入集合
			this.autoConfigurationEntries.add(autoConfigurationEntry);
			// 遍历自动配置类，装进entries集合
			for (String importClassName : autoConfigurationEntry.getConfigurations()) {
				this.entries.putIfAbsent(importClassName, annotationMetadata);
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			if (this.autoConfigurationEntries.isEmpty()) {
				return Collections.emptyList();
			}
			// 转化要排除的自动配置类为set集合
			Set<String> allExclusions = this.autoConfigurationEntries.stream()
					.map(AutoConfigurationEntry::getExclusions).flatMap(Collection::stream).collect(Collectors.toSet());
			// 转化有效自动配置类集合为set集合
			Set<String> processedConfigurations = this.autoConfigurationEntries.stream()
					.map(AutoConfigurationEntry::getConfigurations).flatMap(Collection::stream)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			// 过滤删除要排除的自动配置类
			processedConfigurations.removeAll(allExclusions);
			// 对有@Order注解的自动配置类进行排序
			return sortAutoConfigurations(processedConfigurations, getAutoConfigurationMetadata()).stream()
					.map((importClassName) -> new Entry(this.entries.get(importClassName), importClassName))
					.collect(Collectors.toList());
		}

		private AutoConfigurationMetadata getAutoConfigurationMetadata() {
			if (this.autoConfigurationMetadata == null) {
				this.autoConfigurationMetadata = AutoConfigurationMetadataLoader.loadMetadata(this.beanClassLoader);
			}
			return this.autoConfigurationMetadata;
		}

		private List<String> sortAutoConfigurations(Set<String> configurations,
				AutoConfigurationMetadata autoConfigurationMetadata) {
			return new AutoConfigurationSorter(getMetadataReaderFactory(), autoConfigurationMetadata)
					.getInPriorityOrder(configurations);
		}

		private MetadataReaderFactory getMetadataReaderFactory() {
			try {
				return this.beanFactory.getBean(SharedMetadataReaderFactoryContextInitializer.BEAN_NAME,
						MetadataReaderFactory.class);
			}
			catch (NoSuchBeanDefinitionException ex) {
				return new CachingMetadataReaderFactory(this.resourceLoader);
			}
		}

	}

	protected static class AutoConfigurationEntry {

		private final List<String> configurations;

		private final Set<String> exclusions;

		private AutoConfigurationEntry() {
			this.configurations = Collections.emptyList();
			this.exclusions = Collections.emptySet();
		}

		/**
		 * Create an entry with the configurations that were contributed and their
		 * exclusions.
		 * @param configurations the configurations that should be imported
		 * @param exclusions the exclusions that were applied to the original list
		 */
		AutoConfigurationEntry(Collection<String> configurations, Collection<String> exclusions) {
			this.configurations = new ArrayList<>(configurations);
			this.exclusions = new HashSet<>(exclusions);
		}

		public List<String> getConfigurations() {
			return this.configurations;
		}

		public Set<String> getExclusions() {
			return this.exclusions;
		}

	}

}
