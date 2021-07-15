# Gradle chrome trace

* init.gradle
```
initscript {
    dependencies {
        classpath files('gradle/chrome-trace-0.17.0-alpha07.jar')
    }
}

rootProject {
    def date = new java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())
    ext.chromeTraceFile = new File(rootProject.buildDir, "reports/trace/trace-${date}.html")
}

apply plugin: org.gradle.trace.GradleTracingPlugin
```

* run with trace
```
./gradlew --init-script init.gradle -Dtrace build
```
