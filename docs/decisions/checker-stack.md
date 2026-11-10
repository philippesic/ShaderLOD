# Checker compatibility

Gradle 9.2.1, ModDev 2.0.141 and Temurin 21.0.13+9 run the pinned checker stack on Linux ARM64. Spotless 8.11.0 with google-java-format 1.37.0 failed with InvocationTargetException. The approved alternative 2.0.0 passes without suppression. JUnit uses direct API and engine dependencies after dependency-analysis rejected the unused aggregator. No broad suppressions are accepted.
