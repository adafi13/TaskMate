
@ECHO OFF
SETLOCAL
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
"%JAVA_HOME%\bin\java.exe" -Xmx64m -Xms64m -Dfile.encoding=UTF-8 -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
