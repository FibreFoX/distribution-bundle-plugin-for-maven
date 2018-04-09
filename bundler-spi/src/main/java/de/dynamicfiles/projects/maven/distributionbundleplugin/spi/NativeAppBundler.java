/*
 * Copyright 2018 Danny Althoff
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.dynamicfiles.projects.maven.distributionbundleplugin.spi;

import de.dynamicfiles.projects.maven.distributionbundleplugin.api.NativeLauncher;
import de.dynamicfiles.projects.maven.distributionbundleplugin.api.OS;
import de.dynamicfiles.projects.maven.distributionbundleplugin.api.SharedInternalTools;
import java.io.File;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author FibreFoX
 */
public interface NativeAppBundler {

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

    File bundleApp(String jdkPath, SharedInternalTools internalUtils, File outputBaseFolder, File sourceFolder, File tempWorkfolder, MavenProject project, List<NativeLauncher> nativeLaunchers) throws MojoFailureException, MojoExecutionException;

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
    boolean creatableOnBuildsystem(OS os);

    OS getClientOS();
}
