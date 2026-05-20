@rem
@rem Gradle startup script for Windows
@rem

@if "%DEBUG%"=="" @echo off

set DEFAULT_JVM_OPTS="-Xmx2048m" "-Xms256m"

set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%

set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo.
    echo ERROR: gradle-wrapper.jar not found.
    echo.
    echo Please open this project in Android Studio — it will auto-generate the wrapper.
    echo Or run: gradle wrapper --gradle-version 8.9
    echo.
    exit /b 1
)

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
goto execute

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
