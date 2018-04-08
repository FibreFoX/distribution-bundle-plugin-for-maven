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
import java.io.IOException;
import java.nio.file.Path;

/**
 *
 * @author FibreFoX
 */
public interface SharedInternalTools {

    public boolean copyRecursive(Path sourceFolder, Path targetFolder) throws IOException;

    public boolean deleteRecursive(Path sourceFolder) throws IOException;
    
    public void pack(final Path folder, final Path zipFilePath) throws IOException;

    public boolean isClassInsideJarFile(String classname, String locationPrefix, File jarFile);

    public boolean isPlatformWindows();

    public boolean isPlatformLinux();

    public boolean isPlatformMac();

    public boolean isLinuxExecutable64bit(Path linuxBinary);

    public boolean isWindowsExecutable64bit(Path windowsBinary);
}
