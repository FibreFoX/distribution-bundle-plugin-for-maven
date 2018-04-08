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
