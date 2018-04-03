package de.dynamicfiles.projects.maven.distributionbundleplugin.spi;

import java.io.File;

/**
 *
 * @author FibreFoX
 */
public interface NativeInstallerBundle {

    /**
     * When having multiple bundlers inside one JAR-file, this is used to identify them.
     *
     * @return
     */
    String getBundlerIdentifier();

    /**
     * Bundlers might depend on locally installed tooling, therefor checking their existence or all requirements being fulfilled
     * can be done by this method.
     *
     * @param jdkPath for checking and required resources or executables, has to be provided JDK
     *
     * @return
     */
    boolean checkRequirements(String jdkPath);

    File bundleApp(String jdkPath);

    /**
     * As there is no direct help via maven, this bundler can be enhanced for showing the used additional parameters.
     */
    void printHelp();

    /**
     * Returns true when this bundler is being able to run on given build system. Building bundles might be dependend to build system as
     * native tooling is used.
     *
     * @param os
     *
     * @return true, when this bundler is callable on provided OS, otherwise false
     */
    boolean executableOnBuildsystem(OS os);
}