#!/bin/sh
APP_HOME=$(cd $(dirname $0) && pwd)
exec java -Xmx2048m -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
