/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AspectJTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.StringUtils;

/**
 * Parser for the {@code <context:component-scan/>} element.
 *
 * @author Mark Fisher
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @since 2.5
 */
public class ComponentScanBeanDefinitionParser implements BeanDefinitionParser {

    private static final String BASE_PACKAGE_ATTRIBUTE = "base-package";

    private static final String RESOURCE_PATTERN_ATTRIBUTE = "resource-pattern";

    private static final String USE_DEFAULT_FILTERS_ATTRIBUTE = "use-default-filters";

    private static final String ANNOTATION_CONFIG_ATTRIBUTE = "annotation-config";

    private static final String NAME_GENERATOR_ATTRIBUTE = "name-generator";

    private static final String SCOPE_RESOLVER_ATTRIBUTE = "scope-resolver";

    private static final String SCOPED_PROXY_ATTRIBUTE = "scoped-proxy";

    private static final String EXCLUDE_FILTER_ELEMENT = "exclude-filter";

    private static final String INCLUDE_FILTER_ELEMENT = "include-filter";

    private static final String FILTER_TYPE_ATTRIBUTE = "type";

    private static final String FILTER_EXPRESSION_ATTRIBUTE = "expression";


    /**
     * <context:component-scan base-package="com.wjx.betalot"  扫描的基本包路径
     *      annotation-config="true" 是否激活属性注入注解
     *      name-generator="org.springframework.context.annotation.AnnotationBeanNameGenerator"   Bean的ID策略生成器
     *      resource-pattern="*.class"  对资源进行筛选的正则表达式，这边是个大的范畴，具体细分在include-filter与exclude-filter中进行
     *      scope-resolver="org.springframework.context.annotation.AnnotationScopeMetadataResolver" scope解析器 ，与scoped-proxy只能同时配置一个
     *      scoped-proxy="no"  scope代理，与scope-resolver只能同时配置一个
     *      use-default-filters="false"  是否使用默认的过滤器，默认值true
     * >
     * 注意：若使用include-filter去定制扫描内容，要在use-default-filters="false"的情况下，不然会“失效”，被默认的过滤机制所覆盖
     * annotation是对注解进行扫描
     * <context:include-filter type="annotation" expression="org.springframework.stereotype.Component"/>
     * assignable是对类或接口进行扫描
     * <context:include-filter type="assignable" expression="com.wjx.betalot.performer.Performer"/>
     * <context:include-filter type="assignable" expression="com.wjx.betalot.performer.impl.Sonnet"/>
     *
     * 注意：在use-default-filters="false"的情况下，exclude-filter是针对include-filter里的内容进行排除
     * <context:exclude-filter type="annotation" expression="org.springframework.stereotype.Controller"/>
     * <context:exclude-filter type="assignable" expression="com.wjx.betalot.performer.impl.RainPoem"/>
     * <context:exclude-filter type="regex" expression=".service.*"/>
     *
     * </context:component-scan>
     *
     * @param element       the element that is to be parsed into one or more {@link BeanDefinition BeanDefinitions}
     * @param parserContext the object encapsulating the current state of the parsing process;
     *                      provides access to a {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
     * @return
     */
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        //获取basePackage
        String basePackage = element.getAttribute(BASE_PACKAGE_ATTRIBUTE);
        //basePackage可以是占位符，此处就是对占位符进行解析，寻找实际value，一般没什么用，因为basePackage通常是一个固定的包路径
        basePackage = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(basePackage);
        //解析含有分隔符的basePackage成多个字符串
        String[] basePackages = StringUtils.tokenizeToStringArray(basePackage,
                ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);

        // 创建并配置扫描器
        ClassPathBeanDefinitionScanner scanner = configureScanner(parserContext, element);
        //开始扫描basePackages路径下的Class啦
        Set<BeanDefinitionHolder> beanDefinitions = scanner.doScan(basePackages);
        registerComponents(parserContext.getReaderContext(), beanDefinitions, element);

        return null;
    }

    protected ClassPathBeanDefinitionScanner configureScanner(ParserContext parserContext, Element element) {
        //是否适用默认的filters
        boolean useDefaultFilters = true;
        if (element.hasAttribute(USE_DEFAULT_FILTERS_ATTRIBUTE)) {
            useDefaultFilters = Boolean.valueOf(element.getAttribute(USE_DEFAULT_FILTERS_ATTRIBUTE));
        }

        // Delegate bean definition registration to scanner class.
        //创建扫描器 ClassPathBeanDefinitionScanner
        ClassPathBeanDefinitionScanner scanner = createScanner(parserContext.getReaderContext(), useDefaultFilters);
        scanner.setResourceLoader(parserContext.getReaderContext().getResourceLoader());
        scanner.setEnvironment(parserContext.getReaderContext().getEnvironment());
        scanner.setBeanDefinitionDefaults(parserContext.getDelegate().getBeanDefinitionDefaults());
        scanner.setAutowireCandidatePatterns(parserContext.getDelegate().getAutowireCandidatePatterns());
        //是否配置了resource-pattern
        if (element.hasAttribute(RESOURCE_PATTERN_ATTRIBUTE)) {
            //给扫描器设置resource-pattern
            scanner.setResourcePattern(element.getAttribute(RESOURCE_PATTERN_ATTRIBUTE));
        }

        try {
            //解析name-generator配置
            parseBeanNameGenerator(element, scanner);
        } catch (Exception ex) {
            parserContext.getReaderContext().error(ex.getMessage(), parserContext.extractSource(element), ex.getCause());
        }

        try {
            //解析Scope配置
            parseScope(element, scanner);
        } catch (Exception ex) {
            parserContext.getReaderContext().error(ex.getMessage(), parserContext.extractSource(element), ex.getCause());
        }
        //解析子元素include和exclude标签
        parseTypeFilters(element, scanner, parserContext);

        return scanner;
    }

    protected ClassPathBeanDefinitionScanner createScanner(XmlReaderContext readerContext, boolean useDefaultFilters) {
        //创建ClassPathBeanDefinitionScanner实例
        return new ClassPathBeanDefinitionScanner(readerContext.getRegistry(), useDefaultFilters);
    }

    protected void registerComponents(
            XmlReaderContext readerContext, Set<BeanDefinitionHolder> beanDefinitions, Element element) {

        Object source = readerContext.extractSource(element);
        CompositeComponentDefinition compositeDef = new CompositeComponentDefinition(element.getTagName(), source);

        for (BeanDefinitionHolder beanDefHolder : beanDefinitions) {
            compositeDef.addNestedComponent(new BeanComponentDefinition(beanDefHolder));
        }

        // Register annotation config processors, if necessary.
        boolean annotationConfig = true;
        if (element.hasAttribute(ANNOTATION_CONFIG_ATTRIBUTE)) {
            annotationConfig = Boolean.valueOf(element.getAttribute(ANNOTATION_CONFIG_ATTRIBUTE));
        }
        if (annotationConfig) {
            Set<BeanDefinitionHolder> processorDefinitions =
                    AnnotationConfigUtils.registerAnnotationConfigProcessors(readerContext.getRegistry(), source);
            for (BeanDefinitionHolder processorDefinition : processorDefinitions) {
                compositeDef.addNestedComponent(new BeanComponentDefinition(processorDefinition));
            }
        }

        readerContext.fireComponentRegistered(compositeDef);
    }

    /**
     * 如果配置了name-generator，则创建nameGenerator实例设置给扫描器
     */
    protected void parseBeanNameGenerator(Element element, ClassPathBeanDefinitionScanner scanner) {
        if (element.hasAttribute(NAME_GENERATOR_ATTRIBUTE)) {
            //使用ResourceLoader里的类加载器加载配置的nameGenerator，加载成功后创建nameGenerator实例
            BeanNameGenerator beanNameGenerator = (BeanNameGenerator) instantiateUserDefinedStrategy(
                    element.getAttribute(NAME_GENERATOR_ATTRIBUTE), BeanNameGenerator.class,
                    scanner.getResourceLoader().getClassLoader());
            scanner.setBeanNameGenerator(beanNameGenerator);
        }
    }

    protected void parseScope(Element element, ClassPathBeanDefinitionScanner scanner) {
        // Register ScopeMetadataResolver if class name provided.
        if (element.hasAttribute(SCOPE_RESOLVER_ATTRIBUTE)) {
            if (element.hasAttribute(SCOPED_PROXY_ATTRIBUTE)) {
                //scope-resolver和scoped-proxy只能配置一个
                throw new IllegalArgumentException(
                        "Cannot define both 'scope-resolver' and 'scoped-proxy' on <component-scan> tag");
            }
            ScopeMetadataResolver scopeMetadataResolver = (ScopeMetadataResolver) instantiateUserDefinedStrategy(
                    element.getAttribute(SCOPE_RESOLVER_ATTRIBUTE), ScopeMetadataResolver.class,
                    scanner.getResourceLoader().getClassLoader());
            scanner.setScopeMetadataResolver(scopeMetadataResolver);
        }

        if (element.hasAttribute(SCOPED_PROXY_ATTRIBUTE)) {
            String mode = element.getAttribute(SCOPED_PROXY_ATTRIBUTE);
            if ("targetClass".equals(mode)) {
                scanner.setScopedProxyMode(ScopedProxyMode.TARGET_CLASS);
            } else if ("interfaces".equals(mode)) {
                scanner.setScopedProxyMode(ScopedProxyMode.INTERFACES);
            } else if ("no".equals(mode)) {
                scanner.setScopedProxyMode(ScopedProxyMode.NO);
            } else {
                throw new IllegalArgumentException("scoped-proxy only supports 'no', 'interfaces' and 'targetClass'");
            }
        }
    }

    protected void parseTypeFilters(Element element, ClassPathBeanDefinitionScanner scanner, ParserContext parserContext) {
        // Parse exclude and include filter elements.
        ClassLoader classLoader = scanner.getResourceLoader().getClassLoader();
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String localName = parserContext.getDelegate().getLocalName(node);
                try {
                    if (INCLUDE_FILTER_ELEMENT.equals(localName)) {
                        TypeFilter typeFilter = createTypeFilter((Element) node, classLoader, parserContext);
                        scanner.addIncludeFilter(typeFilter);
                    } else if (EXCLUDE_FILTER_ELEMENT.equals(localName)) {
                        TypeFilter typeFilter = createTypeFilter((Element) node, classLoader, parserContext);
                        scanner.addExcludeFilter(typeFilter);
                    }
                } catch (Exception ex) {
                    parserContext.getReaderContext().error(
                            ex.getMessage(), parserContext.extractSource(element), ex.getCause());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected TypeFilter createTypeFilter(Element element, ClassLoader classLoader, ParserContext parserContext) {
        String filterType = element.getAttribute(FILTER_TYPE_ATTRIBUTE);
        String expression = element.getAttribute(FILTER_EXPRESSION_ATTRIBUTE);
        expression = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(expression);
        try {
            if ("annotation".equals(filterType)) {
                return new AnnotationTypeFilter((Class<Annotation>) classLoader.loadClass(expression));
            } else if ("assignable".equals(filterType)) {
                return new AssignableTypeFilter(classLoader.loadClass(expression));
            } else if ("aspectj".equals(filterType)) {
                return new AspectJTypeFilter(expression, classLoader);
            } else if ("regex".equals(filterType)) {
                return new RegexPatternTypeFilter(Pattern.compile(expression));
            } else if ("custom".equals(filterType)) {
                Class<?> filterClass = classLoader.loadClass(expression);
                if (!TypeFilter.class.isAssignableFrom(filterClass)) {
                    throw new IllegalArgumentException(
                            "Class is not assignable to [" + TypeFilter.class.getName() + "]: " + expression);
                }
                return (TypeFilter) BeanUtils.instantiateClass(filterClass);
            } else {
                throw new IllegalArgumentException("Unsupported filter type: " + filterType);
            }
        } catch (ClassNotFoundException ex) {
            throw new FatalBeanException("Type filter class not found: " + expression, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Object instantiateUserDefinedStrategy(String className, Class<?> strategyType, ClassLoader classLoader) {
        Object result;
        try {
            result = classLoader.loadClass(className).newInstance();
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Class [" + className + "] for strategy [" +
                    strategyType.getName() + "] not found", ex);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to instantiate class [" + className + "] for strategy [" +
                    strategyType.getName() + "]: a zero-argument constructor is required", ex);
        }

        if (!strategyType.isAssignableFrom(result.getClass())) {
            throw new IllegalArgumentException("Provided class name must be an implementation of " + strategyType);
        }
        return result;
    }

}
