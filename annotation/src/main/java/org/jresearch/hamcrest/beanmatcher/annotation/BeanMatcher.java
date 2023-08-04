package org.jresearch.hamcrest.beanmatcher.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PACKAGE)
@Documented
public @interface BeanMatcher {
	/** List of beans to generate matchers */
	Class<?>[] value();

	/**
	 * List of packages to ignore - any properties within these packages have been
	 * ignored
	 */
	String[] ignorePackages() default {};
}
