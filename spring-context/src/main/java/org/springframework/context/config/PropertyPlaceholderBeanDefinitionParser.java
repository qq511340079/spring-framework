/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.context.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.util.StringUtils;

/**
 * Parser for the {@code <context:property-placeholder/>} element.
 *
 * @author Juergen Hoeller
 * @author Dave Syer
 * @author Chris Beams
 * @since 2.5
 */
class PropertyPlaceholderBeanDefinitionParser extends AbstractPropertyLoadingBeanDefinitionParser {

	private static final String SYSTEM_PROPERTIES_MODE_ATTRIBUTE = "system-properties-mode";

	private static final String SYSTEM_PROPERTIES_MODE_DEFAULT = "ENVIRONMENT";


	@Override
	protected Class<?> getBeanClass(Element element) {
		// 从Spring 3.1开始，system-properties-mode的默认值已从'FALLBACK'更改为'ENVIRONMENT'。 后一个值表示占位符对系统属性的解析是Environment及其当前PropertySource集的函数。
		//system-properties-mode属性默认值是ENVIRONMENT
		if (SYSTEM_PROPERTIES_MODE_DEFAULT.equals(element.getAttribute(SYSTEM_PROPERTIES_MODE_ATTRIBUTE))) {
			//需要系统运行环境相关属性
			return PropertySourcesPlaceholderConfigurer.class;
		}

		// The user has explicitly specified a value for system-properties-mode: revert to
		// PropertyPlaceholderConfigurer to ensure backward compatibility with 3.0 and earlier.
		//不需要系统运行环境相关属性
		return PropertyPlaceholderConfigurer.class;
	}
	/**
	 *
	 <context:property-placeholder
	 location="属性文件，多个之间逗号分隔"
	 file-encoding="文件编码"
	 ignore-resource-not-found="是否忽略找不到的属性文件"
	 ignore-unresolvable="是否忽略解析不到的属性，如果不忽略，找不到将抛出异常"
	 properties-ref="本地Properties配置"
	 local-override="是否本地覆盖模式，即如果true，那么properties-ref的属性将覆盖location加载的属性，否则相反"
	 system-properties-mode="系统属性模式，默认ENVIRONMENT（表示先找ENVIRONMENT，再找properties-ref/location的），NEVER：表示永远不用ENVIRONMENT的，OVERRIDE类似于ENVIRONMENT"
	 order="顺序"/>
	 */
	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		super.doParse(element, builder);
		// ignore-unresolvable=是否忽略解析不到的属性，如果不忽略，找不到将抛出异常
		builder.addPropertyValue("ignoreUnresolvablePlaceholders",
				Boolean.valueOf(element.getAttribute("ignore-unresolvable")));
		//system-properties-mode属性
		String systemPropertiesModeName = element.getAttribute(SYSTEM_PROPERTIES_MODE_ATTRIBUTE);
		//如果配置了system-properties-mode且不是ENVIRONMENT，则将配置添加进propertyValues
		if (StringUtils.hasLength(systemPropertiesModeName) &&
				!systemPropertiesModeName.equals(SYSTEM_PROPERTIES_MODE_DEFAULT)) {
			builder.addPropertyValue("systemPropertiesModeName", "SYSTEM_PROPERTIES_MODE_" + systemPropertiesModeName);
		}

		if (element.hasAttribute("value-separator")) {
			builder.addPropertyValue("valueSeparator", element.getAttribute("value-separator"));
		}

		if (element.hasAttribute("null-value")) {
			builder.addPropertyValue("nullValue", element.getAttribute("null-value"));
		}
	}

}
