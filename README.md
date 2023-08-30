[![Maven Central](https://img.shields.io/maven-central/v/org.jresearch.hamcrest.beanMatcher/org.jresearch.hamcrest.beanMatcher.pom)](https://mvnrepository.com/artifact/org.jresearch.hamcrest.beanMatcher/org.jresearch.hamcrest.beanMatcher.pom)
[![Build](https://github.com/hortonolite/hamcrestBeanMatcherApt/actions/workflows/BuildSnapshot.yml/badge.svg)](https://github.com/hortonolite/hamcrestBeanMatcherApt/actions/workflows/BuildSnapshot.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=hortonolite_hamcrestBeanMatcherApt&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=hortonolite_hamcrestBeanMatcherApt)

## Hamcrest BeanMatcher APT generator 

Java APT generator for [Hamcrest](http://hamcrest.org/) matcher library. It automatically generate a matcher builder class for Java Beans.

For example, for Java bean
```Java
@Value
@Builder
public class TestBean01 {
	String stringValue;
}
```
it will generate the following matcher builder
```Java
public class TestBean01Matcher extends AbstractBeanMatcher<TestBean01> {
	public static TestBean01Matcher create() {
		return new TestBean01Matcher();
	}
	public static TestBean01Matcher testBean01Matcher() {
		return create();
	}
	protected TestBean01Matcher add(String propertyName, final Matcher<?> matcher) {
		addPropertyMatcher(propertyName, matcher);
		return this;
	}
	public TestBean01Matcher withStringValue(Matcher<? super String> matcher) {
		return add("stringValue", matcher);
	}
	public TestBean01Matcher withStringValue(String testValue) {
		return withStringValue(equalTo(testValue));
	}
}
```
The generated matcher builder you can use in your tests
```Java
	@ParameterizedTest
	@MethodSource
	void testTestBean01(Matcher<TestBean01> matcher) {
		TestBean01 bean = TestBean01.builder()
				.stringValue("value01")
				.build();
		assertThat(bean, matcher);
	}
	private static Stream<Arguments> testTestBean01() {
		return Stream.of(
				Arguments.of(testBean01Matcher().withStringValue("value01")),
				Arguments.of(testBean01Matcher().withStringValue(StringContains.containsStringIgnoringCase("lUe"))));
	}
```
## Generated methods
For each property generates general method accepted Matcher for this property:
- `withPropertyName(Matcher<PropertyType>)` - generic method allows to construct arbitrary matcher
### Scalar properties
- `withPropertyName(PropertyType)` - exact property value, underlines calls the `Matchers.equalTo` method 
### Iterable, collection and Array properties
- `withProperyNameSize(int)` - allows to check container size (except Iterable)
- `hasPropertyName(ValueType)` - alows to check if container has specific entry.
### Map properties
- `withProperyNameSize(int)` - allows to check map size
- `hasPropertyName(KeyType, ValueType)` - alows to check if map contions specific key/value entry.
### Bean properties
For each bean property the generator create a coresponded matcher builder class automatically.
For a property `MyAnotherBean beanValue;` will be generated `MyAnotherBeanMatcher`

## How to use
add the following project dependency to pom.xml
```xml
<dependency>
	<groupId>org.jresearch.hamcrest.beanMatcher</groupId>
	<artifactId>org.jresearch.hamcrest.beanMatcher.annotation</artifactId>
	<version>${beanMatcher.version}</version>
	<scope>test</scope>
</dependency>
```
and configure annotation processor
```xml
<build>
	<plugins>	
		<plugin>                                                                             
			<artifactId>maven-compiler-plugin</artifactId>                                   
			<configuration>                                                                  
				<annotationProcessorPaths>                                                   
					<path>                                                                   
						<groupId>org.jresearch.hamcrest.beanMatcher</groupId>                
						<artifactId>org.jresearch.hamcrest.beanMatcher.processor</artifactId>
						<version>${beanMatcher.version}</version>                                
					</path>                                                                  
				</annotationProcessorPaths>                                                  
			</configuration>                                                                 
		</plugin>                                                                            
	</plugins>                                                                               
</build>                                                                                     
```
After that add `@BeanMatcher(TestBean01.class)` to your **test** package (to do it create _package-info.java_ and add put the annotation into it)
```Java
@BeanMatcher(TestBean02.class)
package org.jresearch.hamcrest.beanmatcher.test;

import org.jresearch.hamcrest.beanmatcher.annotation.BeanMatcher;
```
See the [test module](https://github.com/hortonolite/hamcrestBeanMatcherApt/tree/main/test) for more information

