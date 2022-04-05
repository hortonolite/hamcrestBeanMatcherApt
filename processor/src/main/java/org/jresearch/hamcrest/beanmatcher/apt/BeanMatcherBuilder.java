package org.jresearch.hamcrest.beanmatcher.apt;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import org.hamcrest.Matcher;
import org.hamcrest.core.IsEqual;
import org.jresearch.hamcrest.beanmatcher.matcher.AbstractBeanMatcher;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;
import com.squareup.javapoet.WildcardTypeName;

import one.util.streamex.StreamEx;

/**
 * <pre>
 * package org.jresearch.hamcrest.beanmatcher.test;
 *
 * import static org.hamcrest.core.IsEqual.*;
 *
 * import org.hamcrest.Matcher;
 * import org.jresearch.hamcrest.beanMatcher.matcher.AbstractBeanMatcher;
 * import org.jresearch.hamcrest.beanmatcher.matcher.test.TestBean01;
 *
 * public class TestBean01Matcher extends AbstractBeanMatcher<TestBean01> {
 *
 * 	public static TestBean01Matcher create() {
 * 		return new TestBean01Matcher();
 * 	}
 *
 * 	public static TestBean01Matcher testBean01Matcher() {
 * 		return create();
 * 	}
 *
 * 	protected TestBean01Matcher add(String propertyName, final Matcher<?> matcher) {
 * 		addPropertyMatcher(propertyName, matcher);
 * 		return this;
 * 	}
 *
 * 	public TestBean01Matcher withStringValue(String testValue) {
 * 		return withStringValue(equalTo(testValue));
 * 	}
 *
 * 	public TestBean01Matcher withStringValue(Matcher<String> matcher) {
 * 		return add("stringValue", matcher);
 * 	}
 *
 * }
 *
 * </pre>
 */
public class BeanMatcherBuilder {

	private final Builder poetBuilder;
	private final Messager messager;
	private final List<ClassName> staticImports = new ArrayList<>();
	private final ClassName mattcherClassName;
	private boolean hasProperties = false;

	private BeanMatcherBuilder(Messager messager, final CharSequence packageName, final CharSequence matcherClassName, final TypeElement beanClass) {
		this.messager = messager;

		mattcherClassName = ClassName.get(packageName.toString(), matcherClassName.toString());

		ParameterizedTypeName superclass = ParameterizedTypeName.get(ClassName.get(AbstractBeanMatcher.class), ClassName.get(beanClass));

		MethodSpec generalCreateMethod = MethodSpec.methodBuilder("create")
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.returns(mattcherClassName)
				.addStatement("return new $T()", mattcherClassName)
				.build();
		MethodSpec specificCreateMethod = MethodSpec.methodBuilder(Introspector.decapitalize(matcherClassName.toString()))
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.returns(mattcherClassName)
				.addStatement("return create()")
				.build();
		TypeName wildcard = WildcardTypeName.subtypeOf(TypeName.OBJECT);
		ParameterizedTypeName matcher = ParameterizedTypeName.get(ClassName.get(Matcher.class), wildcard);
		MethodSpec addMethod = MethodSpec.methodBuilder("add")
				.addModifiers(Modifier.PROTECTED)
				.addParameter(String.class, "propertyName")
				.addParameter(matcher, "matcher")
				.returns(mattcherClassName)
				.addStatement("addPropertyMatcher(propertyName, matcher)")
				.addStatement("return this")
				.build();

		poetBuilder = TypeSpec
				.classBuilder(mattcherClassName)
				.addModifiers(Modifier.PUBLIC)
				.superclass(superclass)
				.addMethod(generalCreateMethod)
				.addMethod(specificCreateMethod)
				.addMethod(addMethod);

	}

	public static BeanMatcherBuilder create(final Messager messager, final CharSequence packageName, final TypeElement beanClass) {
		return new BeanMatcherBuilder(messager, packageName, String.format("%sMatcher", beanClass.getSimpleName().toString()), beanClass);
	}

	public void add(PropertyInfo propertyInfo) {
		hasProperties = true;
		messager.printMessage(Kind.NOTE, String.format("Process property %s", propertyInfo));
		String methodName = String.format("with%s", propertyInfo.getName());

		ParameterizedTypeName matcher = getMatcherParameterType(propertyInfo);

		MethodSpec withMatcherMethod = MethodSpec.methodBuilder(methodName)
				.addModifiers(Modifier.PUBLIC)
				.addParameter(matcher, "matcher")
				.returns(mattcherClassName)
				.addStatement("return add($S, matcher)", Introspector.decapitalize(propertyInfo.getName()))
				.build();

		poetBuilder.addMethod(withMatcherMethod);

		if (propertyInfo.getKind() == PropertyKind.SCALAR && !propertyInfo.getTypes().isEmpty()) {
			MethodSpec withValueMethod = MethodSpec.methodBuilder(methodName)
					.addModifiers(Modifier.PUBLIC)
					.addParameter(TypeName.get(propertyInfo.getTypes().get(0)), "testValue")
					.returns(mattcherClassName)
					.addStatement("return $L(equalTo(testValue))", methodName)
					.build();

			poetBuilder.addMethod(withValueMethod);
		}
	}

	private ParameterizedTypeName getMatcherParameterType(PropertyInfo propertyInfo) {
		switch (propertyInfo.getKind()) {
		case SCALAR:
			return ParameterizedTypeName.get(ClassName.get(Matcher.class), WildcardTypeName.supertypeOf(TypeName.get(propertyInfo.getTypes().get(0)).box()));
		case ITERABLE:
			ParameterizedTypeName iterable = ParameterizedTypeName.get(ClassName.get(Iterable.class), WildcardTypeName.subtypeOf(TypeName.get(propertyInfo.getTypes().get(0))));
			return ParameterizedTypeName.get(ClassName.get(Matcher.class), iterable);
		case MAP:
			TypeName[] wildcards = StreamEx.of(propertyInfo.getTypes())
					.map(TypeName::get)
					.map(WildcardTypeName::subtypeOf)
					.toArray(TypeName.class);
			ParameterizedTypeName map = ParameterizedTypeName.get(ClassName.get(Map.class), wildcards);
			return ParameterizedTypeName.get(ClassName.get(Matcher.class), map);
		case ARRAY:
			ArrayTypeName array = ArrayTypeName.of(TypeName.get(propertyInfo.getTypes().get(0)));
			return ParameterizedTypeName.get(ClassName.get(Matcher.class), array);
		default:
			String msg = String.format("Unsupported property kind %s", propertyInfo.getKind());
			messager.printMessage(Kind.ERROR, msg);
			throw new IllegalStateException(msg);
		}
	}

	public List<ClassName> getStaticImports() {
		staticImports.add(ClassName.get(IsEqual.class));
		return staticImports;
	}

	public TypeSpec build() {
		return poetBuilder.build();
	}

	public boolean hasProperties() {
		return hasProperties;
	}

}
