package de.dynamicfiles.projects.maven.distributionbundleplugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Some methods which are used internally.
 *
 * @author FibreFoX
 */
public class InternalUtils {

    public boolean copyRecursive(Path sourceFolder, Path targetFolder) throws IOException {
        AtomicBoolean failed = new AtomicBoolean(false);
        Files.walkFileTree(sourceFolder, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path subfolder, BasicFileAttributes attrs) throws IOException {
                // do create subfolder (if needed)
                Files.createDirectories(targetFolder.resolve(sourceFolder.relativize(subfolder)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                // do copy, and replace, as the resource might already be existing
                Files.copy(sourceFile, targetFolder.resolve(sourceFolder.relativize(sourceFile)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path source, IOException ioe) throws IOException {
                // don't fail, just inform user
                failed.set(true);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path source, IOException ioe) throws IOException {
                // nothing to do here
                if( ioe != null ){
                    failed.set(true);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return failed.get();
    }

    public boolean deleteRecursive(Path sourceFolder) throws IOException {
        AtomicBoolean failed = new AtomicBoolean(false);
        Files.walkFileTree(sourceFolder, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path subfolder, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(sourceFile);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path source, IOException ioe) throws IOException {
                failed.set(true);
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path source, IOException ioe) throws IOException {
                if( ioe != null ){
                    failed.set(true);
                    return FileVisitResult.TERMINATE;
                }
                Files.deleteIfExists(source);
                return FileVisitResult.CONTINUE;
            }
        });
        return failed.get();
    }

    public void pack(final Path folder, final Path zipFilePath) throws IOException {
        // source of inspiration: http://stackoverflow.com/a/35158142/1961102
        try(FileOutputStream fos = new FileOutputStream(zipFilePath.toFile()); ZipOutputStream zos = new ZipOutputStream(fos)){
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // sanitize windows path parts, because ZIP only allows normal slash
                    zos.putNextEntry(new ZipEntry(folder.relativize(file).toString().replace("\\", "/")));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public boolean isClassInsideJarFile(String classname, String locationPrefix, File jarFile) {
        String requestedJarEntryName = locationPrefix + classname.replace(".", "/") + ".class";
        try{
            JarFile jarFileToSearchIn = new JarFile(jarFile, false, JarFile.OPEN_READ);
            return jarFileToSearchIn.stream().parallel().filter(jarEntry -> {
                return jarEntry.getName().equals(requestedJarEntryName);
            }).findAny().isPresent();
        } catch(IOException ex){
            // NO-OP
        }
        return false;
    }

    public boolean isPlatformWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
