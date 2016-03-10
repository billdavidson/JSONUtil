# JSONUtil

This is a JSON generation and parsing library for Java.  It is oriented
towards being used in web servers and includes several options for validation
and controlling the way that the JSON is generated.  Defaults for those options
can be set using JNDI or JMX/MBean access which is normally available with
JEE web tier containers.  If you don't have those, a debug log message
may be generated, but it is harmless.  Those log messages can be suppressed
by setting system properties on the java command line.

The trunk branch requires Java 8, but there are branches for Java 5,
Java 6 and Java 7, which maintain the same functionality except for
the default methods in an interface.

For questions or suggestions please post a [comment here (Disqus)](http://kopitubruk.org/JSONUtil/#comments). Issues can be reported in the [issues section](https://github.com/billdavidson/JSONUtil/issues) of the github project.


## Overview

There is an interface provided called `JSONAble`, which `JSONUtil` knows to use for objects that generate their own JSON.

Configuration options are in `JSONConfig`, which is an optional configuration object to control validation and encoding options.

Defaults for configuration options can also be modified. Keep in mind that doing so will affect the defaults for the entire class loader.

The `JSONParser` class will parse JSON data. It converts Javascript objects to LinkedHashMap's with the property names being the keys. It converts Javascript arrays to ArrayList's. It is more permissive in the data that it accepts than the JSON standard. It allows Javascript strings with either type of quote. It allows unquoted identifiers. It allows full Javascript numbers. It also allows ECMAScript 6 style code point escapes.

Error messages in exceptions and log messages are internationalized, though the only localizations available are currently English (default) and Spanish. If you would like to submit other localizations or correct my Spanish, please submit an issue for this project.

## Install

To install the library, include its [maven dependency](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.kopitubruk.util%22%20AND%20a%3A%22JSONUtil%22) in your project pom.xml:

```xml
<dependency>
    <groupId>org.kopitubruk.util</groupId>
    <artifactId>JSONUtil</artifactId>
    <version>1.5-java7</version>
</dependency>
```

Or download the jar here:

https://github.com/billdavidson/JSONUtil/releases


## Usage

There is a run time dependency on [Apache Commons Logging](http://commons.apache.org/proper/commons-logging/)
facade so that it should work with whatever logging framework you already use. For building and testing, it is included as a Maven dependency as well as some other dependencies for JUnit testing.

Files are included for the Maven project and an Eclipse project.

Here a basic example:

```java
Integer x = 5;
Map<String,Object> myData = new LinkedHashMap<>();
myData.put("x", x);
myData.put("bundle", ResourceBundle.getBundle("some.bundle.name", locale));
myData.put("status", "success");
String jsonStr = JSONUtil.toJSON(myData);
```

## API

API Reference is available here: [Javadoc](http://kopitubruk.org/JSONUtil/javadoc)


## Test

It has JUnit tests for most of the functionality.

## License

???

