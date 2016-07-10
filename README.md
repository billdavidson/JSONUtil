
This is a JSON generation and parsing library for Java.  It is oriented
towards being used in web servers and includes several options for validation
and controlling the way that the JSON is generated.  Defaults for those options
can be set programmatically or most by using JNDI or JMX/MBean access which is normally
available with JEE web tier containers.  If you don't have those, a debug log message
may be generated, but it is harmless.  Those log messages can be suppressed
by setting system properties on the java command line.

#### Features
* Recursive traversal of Maps, Iterables (including Collection, List, Set etc.), Enumerations, arrays and ResourceBundles
* Data structure loop detection
* Property name validation (can use ECMAScript or JSON rules)
* Output to String or java.io.Writer
* JSON parsing to Java objects from Strings or java.io.Reader
* Options to fix certain types of bad data
* Automatic escaping of characters required to be escaped by the JSON standard.
* Several different escaping options
* Option to format dates on output and handle different formats when parsing
* Automatic unescaping and possible reescaping of Javascript escapes which are illegal for JSON
* ECMAScript 6 code point escapes are supported for reading and optionally for writing
* The default values for most configuration options can be changed.
* Most defaults can be set via JNDI
* Most defaults can be read or modified via a JMX MBean client
* Option to format output for debugging.

The trunk branch requires Java 8, but there are branches for Java 5,
Java 6 and Java 7, which maintain the same functionality except for
the default methods in an interface.

Files are included for the Maven project and an Eclipse project.

It has JUnit tests for most of the functionality.

There is a run time dependency on
[Apache Commons Logging](http://commons.apache.org/proper/commons-logging/)
facade so that it should work with whatever logging framework you already
use.

[Binaries](https://github.com/billdavidson/JSONUtil/releases)

[Getting Started Guide](https://github.com/billdavidson/JSONUtil/wiki/Getting-Started-Guide)

[Javadoc online](http://kopitubruk.org/JSONUtil/javadoc) (start with the JSONUtil class)

[JSONUtil at the Maven Central Repository](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.kopitubruk.util%22%20AND%20a%3A%22JSONUtil%22)

[Comments (Disqus)](http://kopitubruk.org/JSONUtil/#comments)

[Report Issues](https://github.com/billdavidson/JSONUtil/issues)
