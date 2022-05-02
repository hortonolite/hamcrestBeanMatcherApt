package org.jresearch.hamcrest.beanmatcher.apt;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jresearch.hamcrest.beanmatcher.matcher.AbstractBeanMatcher;
import org.jresearch.hamcrest.beanmatcher.matcher.pecs.IsIterableContaining;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;
import com.squareup.javapoet.WildcardTypeName;

import one.util.streamex.EntryStream;
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

	private static final String TEST_VALUE_PARAMETER = "value";
	private static final String TEST_SIZE_PARAMETER = "size";
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
		// mark bean that it has at least one matching property
		hasProperties = true;
		messager.printMessage(Kind.NOTE, String.format("Process property %s", propertyInfo));

		// Add generic matcher method
		String genericMethodName = String.format("with%s", propertyInfo.getName());
		ParameterizedTypeName matcher = getMatcherParameterType(propertyInfo);
		ParameterSpec parameter = createParameter(matcher, "matcher");
		CodeBlock statement = CodeBlock.of("return add($S, matcher)", Introspector.decapitalize(propertyInfo.getName()));
		addMatcherMethod(genericMethodName, parameter, statement);

		switch (propertyInfo.getKind()) {
		case SCALAR:
			processScalarProperty(propertyInfo, genericMethodName);
			break;
		case ARRAY:
			processArrayProperty(propertyInfo, genericMethodName);
			break;
		case COLLECTION:
			processIterableProperty(propertyInfo, genericMethodName);
			processCollectionProperty(propertyInfo, genericMethodName);
			break;
		case ITERABLE:
			processIterableProperty(propertyInfo, genericMethodName);
			break;
		case MAP:
			processMapProperty(propertyInfo, genericMethodName);
			break;
		default:
			break;
		}

	}

	private void processMapProperty(PropertyInfo propertyInfo, String genericMethodName) {
		CodeBlock statement = CodeBlock.of("return $L(aMapWithSize($L))", genericMethodName, TEST_SIZE_PARAMETER);
		addSizeMethod(propertyInfo, statement);
		CodeBlock hasStatement = CodeBlock.of("return $L(hasEntry($L))", genericMethodName, formatParameters("key", TEST_VALUE_PARAMETER));
		addHasMethod(propertyInfo, hasStatement, "key", TEST_VALUE_PARAMETER);
	}

	private void processIterableProperty(PropertyInfo propertyInfo, String genericMethodName) {
		CodeBlock statement = CodeBlock.of("return $L($T.hasItem($L))", genericMethodName, IsIterableContaining.class, TEST_VALUE_PARAMETER);
		addHasMethod(propertyInfo, statement, TEST_VALUE_PARAMETER);
	}

	private void processCollectionProperty(PropertyInfo propertyInfo, String genericMethodName) {
		CodeBlock statement = CodeBlock.of("return $L(hasSize($L))", genericMethodName, TEST_SIZE_PARAMETER);
		addSizeMethod(propertyInfo, statement);
	}

	private void processArrayProperty(PropertyInfo propertyInfo, String genericMethodName) {
		CodeBlock statement = CodeBlock.of("return $L(arrayWithSize($L))", genericMethodName, TEST_SIZE_PARAMETER);
		addSizeMethod(propertyInfo, statement);
		CodeBlock hasStatement = CodeBlock.of("return $L(hasItemInArray($L))", genericMethodName, TEST_VALUE_PARAMETER);
		addHasMethod(propertyInfo, hasStatement, TEST_VALUE_PARAMETER);
	}

	private void addHasMethod(PropertyInfo propertyInfo, CodeBlock statement, String... parametersNames) {
		String methodName = String.format("has%s", propertyInfo.getName());
		List<ParameterSpec> parameters = createParameters(propertyInfo.getTypes(), 1, parametersNames);
		addMatcherMethod(methodName, parameters, statement);
	}

	@SuppressWarnings("resource")
	private static String formatParameters(String... parametersNames) {
		return StreamEx.of(parametersNames).joining(", ");
	}

	@SuppressWarnings("resource")
	private static List<ParameterSpec> createParameters(List<TypeMirror> types, int offset, String... names) {
		return EntryStream.of(names)
			.mapKeys(index -> types.get(index + offset))
			.mapKeyValue(BeanMatcherBuilder::createParameter)
			.toList();
	}

	private static ParameterSpec createParameter(TypeMirror type, String name) {
		return createParameter(TypeName.get(type), name);
	}

	private static ParameterSpec createParameter(TypeName type, String name) {
		return ParameterSpec.builder(type, name).build();
	}

	private void addSizeMethod(PropertyInfo propertyInfo, CodeBlock statement) {
		String methodName = String.format("with%sSize", propertyInfo.getName());
		ParameterSpec parameter = createParameter(TypeName.INT, TEST_SIZE_PARAMETER);
		addMatcherMethod(methodName, parameter, statement);
	}

	private void processScalarProperty(PropertyInfo propertyInfo, String genericMethodName) {
		String methodName = String.format("with%s", propertyInfo.getName());
		List<ParameterSpec> parameters = createParameters(propertyInfo.getTypes(), 0, TEST_VALUE_PARAMETER);
		CodeBlock statement = CodeBlock.of("return $L(equalTo($L))", genericMethodName, TEST_VALUE_PARAMETER);
		addMatcherMethod(methodName, parameters, statement);
	}

	private void addMatcherMethod(String methodName, ParameterSpec parameter, CodeBlock statement) {
		addMatcherMethod(methodName, ImmutableList.of(parameter), statement);
	}

	private void addMatcherMethod(String methodName, List<ParameterSpec> parameters, CodeBlock statement) {
		MethodSpec method = MethodSpec.methodBuilder(methodName)
			.addModifiers(Modifier.PUBLIC)
			.addParameters(parameters)
			.returns(mattcherClassName)
			.addStatement(statement)
			.build();
		poetBuilder.addMethod(method);
	}

	private ParameterizedTypeName getMatcherParameterType(PropertyInfo propertyInfo) {
		ClassName type = (ClassName) TypeName.get(propertyInfo.getTypes().get(0)).box();
		switch (propertyInfo.getKind()) {
		case SCALAR:
			WildcardTypeName type1 = WildcardTypeName.supertypeOf(type);
			return ParameterizedTypeName.get(ClassName.get(Matcher.class), type1);
		case COLLECTION:
		case ITERABLE:
			ParameterizedTypeName iterable = ParameterizedTypeName.get(type, WildcardTypeName.subtypeOf(TypeName.get(propertyInfo.getTypes().get(1))));
			WildcardTypeName type2 = WildcardTypeName.supertypeOf(iterable);
			return ParameterizedTypeName.get(ClassName.get(Matcher.class), type2);
		case MAP:
			TypeName[] wildcards = StreamEx.of(propertyInfo.getTypes())
				.skip(1)
				.map(TypeName::get)
				.map(WildcardTypeName::subtypeOf)
				.toArray(TypeName.class);
			ParameterizedTypeName map = ParameterizedTypeName.get(type, wildcards);
			WildcardTypeName type3 = WildcardTypeName.supertypeOf(map);
			return ParameterizedTypeName.get(ClassName.get(Matcher.class), type3);
		case ARRAY:
			ArrayTypeName array = ArrayTypeName.of(type);
			return ParameterizedTypeName.get(ClassName.get(Matcher.class), array);
		default:
			String msg = String.format("Unsupported property kind %s", propertyInfo.getKind());
			messager.printMessage(Kind.ERROR, msg);
			throw new IllegalStateException(msg);
		}
	}

	public List<ClassName> getStaticImports() {
		staticImports.add(ClassName.get(Matchers.class));
		return staticImports;
	}

	public TypeSpec build() {
		return poetBuilder.build();
	}

	public boolean hasProperties() {
		return hasProperties;
	}

}
