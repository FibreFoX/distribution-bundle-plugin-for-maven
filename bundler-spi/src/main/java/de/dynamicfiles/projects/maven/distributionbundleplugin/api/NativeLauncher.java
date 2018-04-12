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
package de.dynamicfiles.projects.maven.distributionbundleplugin.api;

import java.io.File;

/**
 *
 * @author FibreFoX
 */
public class NativeLauncher {

    /**
     * The filename of the native launcher file (without file extension).
     */
    private String filename = null;

    /**
     * The file extension of the native launcher file (e.g. ".exe", ".scr", ...).
     */
    private String extension = null;

    /**
     * The executable native launcher sometimes needs a custom configuration, e.G. some special
     * JVM-options or even a different main-class or some special agentlib-configuration.
     * TODO: add more documentation e.g. for template and replacement-strings
     */
    private String configuration = null;

    /**
     * Same as "configuration", but is ment to point to a template-file. Using this parameter overrides the made
     * configuration inside "configuration"-parameter.
     */
    private File configurationFile = null;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public File getConfigurationFile() {
        return configurationFile;
    }

    public void setConfigurationFile(File configurationFile) {
        this.configurationFile = configurationFile;
    }

}
