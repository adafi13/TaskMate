
#!/usr/bin/env sh
##############################################################################
# Gradle start up script for UN*X
##############################################################################
APP_BASE_NAME=`basename "$0"`
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec "$JAVA_HOME/bin/java" -Xmx64m -Xms64m -Dfile.encoding=UTF-8 -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
