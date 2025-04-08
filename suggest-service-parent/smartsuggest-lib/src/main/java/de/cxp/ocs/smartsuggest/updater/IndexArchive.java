package de.cxp.ocs.smartsuggest.updater;

import java.io.File;

/**
 * Wrapper around an archive file that can be stored and loaded. The file is expected to be a .tar.gz file.
 *
 * @param zippedTarFile
 * 		The file is expected to be a .tar.gz file
 * @param dataModificationTime
 * 		modification time of the data in this archive (not the file creation time).
 */
public record IndexArchive(File zippedTarFile, long dataModificationTime) {

}
