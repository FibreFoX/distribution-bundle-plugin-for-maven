[![AppVeyor Build status](https://ci.appveyor.com/api/projects/status/2f2tcy40htaws9p0/branch/master?svg=true)](https://ci.appveyor.com/project/FibreFoX/distribution-bundle-plugin-for-maven/branch/master)

# WORK IN PROGRESS

* This is intended to replace the [javafx-maven-plugin](https://github.com/javafx-maven-plugin/javafx-maven-plugin), as this is a rewrite of the whole contept, without the requirement for the OpenJFX/JavaFX-part being installed.
* Issue-section will be opened once this plugin is released
* pull-requests are getting ignored and closed unseed

# Easy creationg of shareable software bundles for your Java application

Distributing Java software applications gets tricky sometimes, therefor this plugin integrates the creation of them into your build system (maven).

# What kind of "distribution bundles" are going to be created

There are three types of bundles:
1. (java-app bundle) executable jar-file with dependencies
2. (native-app bundle) executable jar-file with dependencies, with the addition of some OS native launcher files and some JRE aside
3. (native-installer bundle) all the above, packed and stuffed into some native installer file (MSI, DEB, PKG, ...) without a lot of restrictions