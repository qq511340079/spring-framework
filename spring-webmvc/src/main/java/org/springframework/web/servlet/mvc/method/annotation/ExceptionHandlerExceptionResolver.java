/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.AbstractHandlerMethodExceptionResolver;

/**
 * An {@link AbstractHandlerMethodExceptionResolver} that resolves exceptions
 * through {@code @ExceptionHandler} methods.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers}.
 * Or alternatively to re-configure all argument and return value types use
 * {@link #setArgumentResolvers} and {@link #setReturnValueHandlers(List)}.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ExceptionHandlerExceptionResolver extends AbstractHandlerMethodExceptionResolver
		implements ApplicationContextAware, InitializingBean {

	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	private HandlerMethodArgumentResolverComposite argumentResolvers;

	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	private List<HttpMessageConverter<?>> messageConverters;

	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	private final List<Object> responseBodyAdvice = new ArrayList<Object>();

	private ApplicationContext applicationContext;

	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache =
			new ConcurrentHashMap<Class<?>, ExceptionHandlerMethodResolver>(64);

	private final Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> exceptionHandlerAdviceCache =
			new LinkedHashMap<ControllerAdviceBean, ExceptionHandlerMethodResolver>();


	public ExceptionHandlerExceptionResolver() {
		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
		stringHttpMessageConverter.setWriteAcceptCharset(false);  // see SPR-7316

		this.messageConverters = new ArrayList<HttpMessageConverter<?>>();
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(stringHttpMessageConverter);
		this.messageConverters.add(new SourceHttpMessageConverter<Source>());
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument
	 * resolution use {@link #setArgumentResolvers} instead.
	 */
	public void setCustomArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers= argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 */
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if
	 * not initialized yet via {@link #afterPropertiesSet()}.
	 */
	public HandlerMethodArgumentResolverComposite getArgumentResolvers() {
		return this.argumentResolvers;
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are
	 * ordered after built-in ones. To override the built-in support for
	 * return value handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus
	 * overriding handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	public HandlerMethodReturnValueHandlerComposite getReturnValueHandlers() {
		return this.returnValueHandlers;
	}

	/**
	 * Set the message body converters to use.
	 * <p>These converters are used to convert from and to HTTP requests and responses.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Return the configured {@link ContentNegotiationManager}.
	 */
	public ContentNegotiationManager getContentNegotiationManager() {
		return this.contentNegotiationManager;
	}

	/**
	 * Add one or more components to be invoked after the execution of a controller
	 * method annotated with {@code @ResponseBody} or returning {@code ResponseEntity}
	 * but before the body is written to the response with the selected
	 * {@code HttpMessageConverter}.
	 */
	public void setResponseBodyAdvice(List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		this.responseBodyAdvice.clear();
		if (responseBodyAdvice != null) {
			this.responseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}


	@Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBodyAdvice beans
		initExceptionHandlerAdviceCache();

		if (this.argumentResolvers == null) {
		    //获取默认的参数解析器，用来解析@ExpcetionHandler标识的方法入参
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		if (this.returnValueHandlers == null) {
		    //获取默认的返回值处理器，用来处理@ExpcetionHandler标识的方法的返回值
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initExceptionHandlerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Looking for exception mappings: " + getApplicationContext());
		}
		//所有@ControllerAdvice注解标识的类信息
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());
		//排个序
		AnnotationAwareOrderComparator.sort(adviceBeans);

		//遍历被@ControllerAdvice标识的类
		for (ControllerAdviceBean adviceBean : adviceBeans) {
		    //创建ExceptionHandlerMethodResolver对象，解析被@ControllerAdvice标识的类中的@ExceptionHandler注解
			ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(adviceBean.getBeanType());
			//如果resolver解析到了异常-->处理该异常的method的映射关系
			if (resolver.hasExceptionMappings()) {
			    //将adviceBean和resolver的关系缓存起来
				this.exceptionHandlerAdviceCache.put(adviceBean, resolver);
				if (logger.isInfoEnabled()) {
					logger.info("Detected @ExceptionHandler methods in " + adviceBean);
				}
			}
			//如果class类型是ResponseBodyAdvice，将其缓存到responseBodyAdvice
			if (ResponseBodyAdvice.class.isAssignableFrom(adviceBean.getBeanType())) {
				this.responseBodyAdvice.add(adviceBean);
				if (logger.isInfoEnabled()) {
					logger.info("Detected ResponseBodyAdvice implementation in " + adviceBean);
				}
			}
		}
	}

	/**
	 * Return an unmodifiable Map with the {@link ControllerAdvice @ControllerAdvice}
	 * beans discovered in the ApplicationContext. The returned map will be empty if
	 * the method is invoked before the bean has been initialized via
	 * {@link #afterPropertiesSet()}.
	 */
	public Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> getExceptionHandlerAdviceCache() {
		return Collections.unmodifiableMap(this.exceptionHandlerAdviceCache);
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	protected List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<HandlerMethodArgumentResolver>();

		// Type-based argument resolution
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());
		resolvers.add(new ModelMethodProcessor());

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	protected List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<HandlerMethodReturnValueHandler>();

		// Single-purpose return value types
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		handlers.add(new ModelMethodProcessor());
		handlers.add(new ViewMethodReturnValueHandler());
		handlers.add(new HttpEntityMethodProcessor(
				getMessageConverters(), this.contentNegotiationManager, this.responseBodyAdvice));

		// Annotation-based return value types
		handlers.add(new ModelAttributeMethodProcessor(false));
		handlers.add(new RequestResponseBodyMethodProcessor(
				getMessageConverters(), this.contentNegotiationManager, this.responseBodyAdvice));

		// Multi-purpose return value types
		handlers.add(new ViewNameMethodReturnValueHandler());
		handlers.add(new MapMethodProcessor());

		// Custom return value types
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		handlers.add(new ModelAttributeMethodProcessor(true));

		return handlers;
	}


	/**
	 * Find an {@code @ExceptionHandler} method and invoke it to handle the raised exception.
     *
     * 为了方便描述
     * 1.用ControllerAdvice Bean来表示被@ControllerAdvice注解修饰的实例
     * 2.用ExceptionHandler Method来表示被@ExceptionHandler注解修饰的方法
	 */
	@Override
	protected ModelAndView doResolveHandlerMethodException(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod, Exception exception) {
        //寻找自定义的ExceptionHandler Method
		ServletInvocableHandlerMethod exceptionHandlerMethod = getExceptionHandlerMethod(handlerMethod, exception);
		//没有找到ServletInvocableHandlerMethod，则返回null，由下一个ExceptionHandlerResolver处理
		if (exceptionHandlerMethod == null) {
			return null;
		}
        //给exceptionHandlerMethod设置参数解析器和返回值处理器，用来解析并设置ExceptionHandler Method的入参和处理返回值
		exceptionHandlerMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		exceptionHandlerMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);

		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		ModelAndViewContainer mavContainer = new ModelAndViewContainer();

		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Invoking @ExceptionHandler method: " + exceptionHandlerMethod);
			}
			//执行ExceptionHandler Method
			exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, exception, handlerMethod);
		}
		catch (Exception invocationEx) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to invoke @ExceptionHandler method: " + exceptionHandlerMethod, invocationEx);
			}
			return null;
		}
        //如果请求已经被处理完成(比如则表示ExceptionHandler Method返回了JSON信息)，则返回空的ModelAndView
		if (mavContainer.isRequestHandled()) {
			return new ModelAndView();
		}
		else {//mavContainer.isRequestHandled()返回false，则可能ExceptionHandler Method返回的是一个ModelAndView，需要渲染视图
			ModelAndView mav = new ModelAndView().addAllObjects(mavContainer.getModel());
			mav.setViewName(mavContainer.getViewName());
			if (!mavContainer.isViewReference()) {
				mav.setView((View) mavContainer.getView());
			}
			return mav;
		}
	}

	/**
	 * Find an {@code @ExceptionHandler} method for the given exception. The default
	 * implementation searches methods in the class hierarchy of the controller first
	 * and if not found, it continues searching for additional {@code @ExceptionHandler}
	 * methods assuming some {@linkplain ControllerAdvice @ControllerAdvice}
	 * Spring-managed beans were detected.
	 * @param handlerMethod the method where the exception was raised (may be {@code null})
	 * @param exception the raised exception
	 * @return a method to handle the exception, or {@code null}
     *
     * 为了方便描述
     * 1.用ControllerAdvice Bean来表示被@ControllerAdvice注解修饰的实例
     * 2.用ExceptionHandler Method来表示被@ExceptionHandler注解修饰的方法
	 */
	protected ServletInvocableHandlerMethod getExceptionHandlerMethod(HandlerMethod handlerMethod, Exception exception) {
	    //获取controller的class
		Class<?> handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);

		//先从被请求的controller中寻找可以处理该异常的ExceptionHandler Method
		if (handlerMethod != null) {
		    //从缓存中获取ExceptionHandlerMethodResolver对象，ExceptionHandlerMethodResolver对象封装了当前controller和其中的所有ExceptionHandler Method
			ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache.get(handlerType);
			//缓存中没获取到的话则创建并放入缓存
			if (resolver == null) {
				resolver = new ExceptionHandlerMethodResolver(handlerType);
				this.exceptionHandlerCache.put(handlerType, resolver);
			}
			//解析出exception对应的处理方法
			Method method = resolver.resolveMethod(exception);
			//method!=null，则将controller和异常对应的处理方法封装到ServletInvocableHandlerMethod对象后返回
			if (method != null) {
				return new ServletInvocableHandlerMethod(handlerMethod.getBean(), method);
			}
		}
        //再从全局中寻找ExceptionHandler Method(@ControllerAdvice注解标识的类中被@ExceptionHandler注解标识的方法)
		for (Entry<ControllerAdviceBean, ExceptionHandlerMethodResolver> entry : this.exceptionHandlerAdviceCache.entrySet()) {
		    //判断ControllerAdvice Bean是否适用于当前请求的controller(根据@ControllerAdvice注解的几个属性来判断)
			if (entry.getKey().isApplicableToBeanType(handlerType)) {
				ExceptionHandlerMethodResolver resolver = entry.getValue();
				//寻找ControllerAdvice Bean中的ExceptionHandler Method
				Method method = resolver.resolveMethod(exception);
				//如果找到了处理该异常的方法，则将ControllerAdvice Bean和ExceptionHandler Method封装到ServletInvocableHandlerMethod对象后返回
				if (method != null) {
					return new ServletInvocableHandlerMethod(entry.getKey().resolveBean(), method);
				}
			}
		}
        //没有找到能处理exception的method
		return null;
	}

}
