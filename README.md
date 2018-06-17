[![Travis Build Status](https://travis-ci.org/FibreFoX/distribution-bundle-plugin-for-maven.svg?branch=master)](https://travis-ci.org/FibreFoX/distribution-bundle-plugin-for-maven)
[![AppVeyor Build status](https://ci.appveyor.com/api/projects/status/2f2tcy40htaws9p0/branch/master?svg=true)](https://ci.appveyor.com/project/FibreFoX/distribution-bundle-plugin-for-maven/branch/master)

# WORK IN PROGRESS

* This will replace the [javafx-maven-plugin](https://github.com/javafx-maven-plugin/javafx-maven-plugin), as this is a rewrite of the whole contept, without the requirement for the OpenJFX/JavaFX-part being installed. It'll be JavaFX-agnostic, making it more accessable to non-JFX applications.
* **pull-requests are getting ignored and closed unseed until this plugin is released**
* unlike the [javafx-maven-plugin](https://github.com/javafx-maven-plugin/javafx-maven-plugin), this maven-plugin does try to not rely on the installed JavaFX/OpenJFX parts, but on short term (to make the replacement-process faster) it re-uses the files that are coming with them, so make sure to have them installed aswell (the roadmap contains a rewrite of the native launcher part, probably using RUST)
* no batteries included: every bundle-type except "java-app" is done in a separate maven-project, making this maven-plugin like a nice bootstrapper for custom bundlers, and making it more flexible for having your very own way to bundle your stuff (like DromblerFX)
* please look in the [test-projects](maven-plugin/src/it/) to have some insight whats coming up
* I am aware of the proposal/JEP [JDK-8200758](https://bugs.openjdk.java.net/browse/JDK-8200758) regarding oracles own replacement for the javapackager, but as they did not a good job on that, and to avoid any deep dependency on that, I do not intend to re-integrate with their stuff (time will have to tell about the quality of their tool, might change my mind about it)

# Easy creationg of shareable software bundles for your Java application

Distributing Java software applications gets tricky sometimes, therefor this plugin integrates the creation of them into your build system (maven).

# What kind of "distribution bundles" are going to be created

There are three types of bundles:
1. (java-app bundle) executable jar-file with dependencies
2. (native-app bundle) executable jar-file with dependencies, with the addition of some OS native launcher files and some JRE aside
3. (native-installer bundle) all the above, packed and stuffed into some native installer file (MSI, DEB, PKG, ...) without a lot of restrictions