/*
 * Copyright 2002-2011 the original author or authors.
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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;

/**
 * Abstract parser for &lt;context:property-.../&gt; elements.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @author Dave Syer
 * @since 2.5.2
 */
abstract class AbstractPropertyLoadingBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	@Override
	protected boolean shouldGenerateId() {
		return true;
	}


	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		//配置文件地址
		String location = element.getAttribute("location");
		//location不为空
		if (StringUtils.hasLength(location)) {
		    //将逗号分隔的location字符串解析为多个location。
			String[] locations = StringUtils.commaDelimitedListToStringArray(location);
			//添加到propertyValues中
			builder.addPropertyValue("locations", locations);
		}
        //properties-ref属性，表示本地java.util.Properties的引用
		String propertiesRef = element.getAttribute("properties-ref");
		if (StringUtils.hasLength(propertiesRef)) {
			builder.addPropertyReference("properties", propertiesRef);
		}
        //file-encoding属性，文件的编码
		String fileEncoding = element.getAttribute("file-encoding");
		if (StringUtils.hasLength(fileEncoding)) {
			builder.addPropertyValue("fileEncoding", fileEncoding);
		}
        //order属性
		String order = element.getAttribute("order");
		if (StringUtils.hasLength(order)) {
			builder.addPropertyValue("order", Integer.valueOf(order));
		}
        //ignore-resource-not-found，当配置文件找不到时是否忽略，默认是false，即不忽略，找不到文件时会抛出异常
		builder.addPropertyValue("ignoreResourceNotFound",
				Boolean.valueOf(element.getAttribute("ignore-resource-not-found")));
        //local-override=是否本地覆盖模式，即如果true，那么properties-ref的属性将覆盖location加载的属性，否则相反
		builder.addPropertyValue("localOverride",
				Boolean.valueOf(element.getAttribute("local-override")));
        //设置bean的角色是一个基础组件
		builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
	}

}
