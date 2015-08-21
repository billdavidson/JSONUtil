
This is a JSON generation library for Java.  It is oriented towards being
used in web servers and includes several options for validation and
controling the way that the JSON is generated.  Defaults for those options
can be set using JNDI or JMX/MBean access which is normally available with
JEE web tier containers.  If you don't have those, a debug log message
may be generated, but it is harmless.  Those log messages can be supressed
by setting system properties on the java command line.

The trunk branch requires Java 8, but there are branches for Java 5,
Java 6 and Java 7, which maintain the same functionality except for
the default methods in an interface.

Files are included for the Maven project and an Eclipse project.

It has JUnit tests for most of the functionality.

There is a run time dependency on Apache Commons Logging.

Binaries are available [here](http://kopitubruk.org/JSONUtil/#downloads)
