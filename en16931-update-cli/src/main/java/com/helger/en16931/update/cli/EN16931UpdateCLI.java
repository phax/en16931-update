/*
 * Copyright (C) 2026 Philip Helger
 * http://www.helger.com
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.en16931.update.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.diagnostics.error.IError;
import com.helger.diagnostics.error.level.EErrorLevel;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931Edition;
import com.helger.en16931.update.EAlready2026Handling;
import com.helger.en16931.update.ECreditNoteIDOnlyReferenceHandling;
import com.helger.en16931.update.EN16931UpdateDispatcher;
import com.helger.en16931.update.EUpdateStatus;
import com.helger.en16931.update.UpdateResult;
import com.helger.en16931.update.UpdateVersion;
import com.helger.io.file.FileSystemIterator;
import com.helger.io.file.FileSystemRecursiveIterator;
import com.helger.io.file.FilenameHelper;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Main command line client. Updates EN 16931:2017 documents (CII D16B, UBL 2.1 Invoice and Credit
 * Note) to EN 16931:2026 (CII D25A, UBL 2.5). The exit code is 0 if no file failed and 1 if at
 * least one did. A file that is skipped because it already is EN 16931:2026 is not a failure.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
@Command (description = "Updater of EN 16931:2017 invoices and credit notes to EN 16931:2026 - CII D16B to D25A and UBL 2.1 to 2.5",
          name = "EN16931Update",
          mixinStandardHelpOptions = true,
          separator = " ")
public class EN16931UpdateCLI implements Callable <Integer>
{
  private static final Logger LOGGER = LoggerFactory.getLogger (EN16931UpdateCLI.class);

  @Option (names = "--source-edition",
           paramLabel = "edition",
           description = "Treat all source files as this EN 16931 edition ('2017' or '2026') instead of reading it " +
                         "from BT-24 of each file. Needed for the few documents without a usable BT-24, like " +
                         "legacy ZUGFeRD ones.")
  private String m_sSourceEdition;

  @Option (names = "--on-2026",
           paramLabel = "mode",
           defaultValue = "skip",
           description = "What to do with a file that already is EN 16931:2026: ${COMPLETION-CANDIDATES} " +
                         "(default: '${DEFAULT-VALUE}'). 'skip' writes nothing and warns, 'copy' writes the " +
                         "document unchanged, 'fail' is an error.")
  private EAlready2026Handling m_eAlready2026Handling;

  @Option (names = "--strict",
           paramLabel = "boolean",
           defaultValue = "false",
           description = "Make a failed check an error and write no file. Without it, an identifier without scheme " +
                         "identifier or an attachment without filename is a warning, and the element is left as it " +
                         "is (default: '${DEFAULT-VALUE}')")
  private boolean m_bStrict;

  @Option (names = "--cn-id-only-reference",
           paramLabel = "mode",
           defaultValue = "keep",
           description = "What to do with an ID-only additional document reference of a UBL Credit Note, which is " +
                         "either the project reference BT-11 or a supporting document and cannot be told apart: " +
                         "${COMPLETION-CANDIDATES} (default: '${DEFAULT-VALUE}'). 'keep' leaves it and warns, " +
                         "'project' moves it to cac:ProjectReference if it is the only one, 'document' makes it a " +
                         "supporting document.")
  private ECreditNoteIDOnlyReferenceHandling m_eCreditNoteIDOnlyReferenceHandling;

  @Option (names = "--customization-id",
           paramLabel = "BT-24",
           description = "Write this value as BT-24 (specification identifier) instead of changing the one of the " +
                         "source file. By default the EN 16931:2017 prefix is replaced and a CIUS suffix is kept.")
  private String m_sCustomizationID;

  @Option (names = "--profile-id",
           paramLabel = "BT-23",
           description = "Write this value as BT-23 (business process) instead of keeping the one of the source file")
  private String m_sProfileID;

  @Option (names = { "-t", "--target" },
           paramLabel = "directory",
           defaultValue = ".",
           description = "The target directory for result output (default: '${DEFAULT-VALUE}')")
  private String m_sOutputDir;

  @Option (names = "--output-suffix",
           paramLabel = "filename part",
           defaultValue = "-2026",
           description = "The suffix added to the output filename (default: '${DEFAULT-VALUE}')")
  private String m_sOutputFileSuffix;

  @Option (names = "--verbose",
           paramLabel = "boolean",
           defaultValue = "false",
           description = "Enable debug logging (default: '${DEFAULT-VALUE}')")
  private boolean m_bVerbose;

  @Option (names = "--disable-wildcard-expansion",
           paramLabel = "boolean",
           defaultValue = "false",
           description = "Disable wildcard expansion of filenames")
  private boolean m_bDisableWildcardExpansion;

  @Parameters (arity = "1..*", paramLabel = "source files", description = "One or more CII or UBL file(s) or directories")
  private List <String> m_aSourceFilenames;

  private void _verboseLog (@NonNull final Supplier <String> aSupplier)
  {
    if (m_bVerbose)
      LOGGER.info (aSupplier.get ());
  }

  @NonNull
  private String _normalizeOutputDirectory (@NonNull final String sDirectory)
  {
    _verboseLog ( () -> "CLI option output directory '" + sDirectory + "'");
    final String ret = Paths.get (sDirectory).toAbsolutePath ().normalize ().toString ();
    if (!sDirectory.equals (ret))
      _verboseLog ( () -> "Normalized output directory '" + ret + "'");
    return ret;
  }

  @NonNull
  private static File _normalizeFile (@NonNull final Path aPath)
  {
    return aPath.toAbsolutePath ().normalize ().toFile ();
  }

  @NonNull
  private ICommonsList <File> _resolveWildcards (@NonNull final List <String> aFilenames) throws IOException
  {
    final ICommonsList <File> ret = new CommonsArrayList <> (aFilenames.size ());

    final File aRootDir = new File (".").getCanonicalFile ();
    for (final String sFilename : aFilenames)
    {
      if (sFilename.indexOf ('*') >= 0 ||
          sFilename.indexOf ('?') >= 0 ||
          (sFilename.indexOf ('[') >= 0 && sFilename.indexOf (']') >= 0))
      {
        // Make search pattern absolute
        final String sRealName = new File (sFilename).getAbsolutePath ();
        _verboseLog ( () -> "Trying to resolve wildcards for '" + sRealName + "'");
        final PathMatcher aMatcher = FileSystems.getDefault ().getPathMatcher ("glob:" + sRealName);
        for (final File f : new FileSystemRecursiveIterator (aRootDir))
          if (aMatcher.matches (f.toPath ()))
          {
            _verboseLog ( () -> "  Found wildcard match '" + f + "'");
            ret.add (f);
          }
      }
      else
        ret.add (new File (sFilename));
    }
    return ret;
  }

  @NonNull
  private ICommonsList <File> _normalizeInputFiles (@NonNull final List <String> aFilenames) throws IOException
  {
    final ICommonsList <File> aFiles;
    if (m_bDisableWildcardExpansion)
    {
      aFiles = new CommonsArrayList <> (aFilenames, File::new);
      _verboseLog ( () -> "Using the input files '" + aFiles + "'");
    }
    else
    {
      _verboseLog ( () -> "Normalizing the input files '" + aFilenames + "'");
      aFiles = _resolveWildcards (aFilenames);
      _verboseLog ( () -> "Resolved wildcards of input files to '" + aFiles + "'");
    }

    final ICommonsList <File> ret = new CommonsArrayList <> ();
    for (final File aFile : aFiles)
    {
      if (aFile.isDirectory ())
      {
        _verboseLog ( () -> "Input '" + aFile + "' is a directory");
        for (final File aChildFile : new FileSystemIterator (aFile))
        {
          final Path p = aChildFile.toPath ();
          if (Files.isReadable (p) && !Files.isDirectory (p))
            ret.add (_normalizeFile (p));
        }
      }
      else
        if (aFile.canRead ())
          ret.add (_normalizeFile (aFile.toPath ()));
        else
          LOGGER.warn ("Ignoring non-existing file " + aFile.getAbsolutePath ());
    }
    _verboseLog ( () -> "Updating the following files: " + ret.getAllMapped (File::getAbsolutePath));
    return ret;
  }

  /**
   * Determine the source edition to force from <code>--source-edition</code>.
   *
   * @return <code>null</code> if the option was not given, meaning the edition is detected per
   *         source file from BT-24.
   * @throws IllegalArgumentException
   *         if the provided value names no known edition.
   */
  @Nullable
  private EEN16931Edition _determineSourceEdition ()
  {
    if (StringHelper.isEmpty (m_sSourceEdition))
      return null;

    final EEN16931Edition ret = EEN16931Edition.getFromIDOrNull (m_sSourceEdition.trim ());
    if (ret == null)
      throw new IllegalArgumentException ("The value '" +
                                          m_sSourceEdition +
                                          "' of --source-edition is unknown. Use '2017' or '2026'.");
    _verboseLog ( () -> "Treating all source files as EN 16931:" + ret.getID ());
    return ret;
  }

  private static void _log (@NonNull final IError aError)
  {
    // The applied rewrites are infos - the audit trail - and must not look like warnings
    final String sMsg = "  " + aError.getAsString (Locale.US);
    if (aError.getErrorLevel ().isGE (EErrorLevel.ERROR))
      LOGGER.error (sMsg);
    else
      if (aError.getErrorLevel ().isGE (EErrorLevel.WARN))
        LOGGER.warn (sMsg);
      else
        LOGGER.info (sMsg);
  }

  private static void _logAll (@NonNull final ErrorList aErrorList)
  {
    for (final IError aError : aErrorList)
      _log (aError);
  }

  /**
   * @return <code>true</code> if the file was updated or deliberately skipped, <code>false</code>
   *         if it failed
   */
  private boolean _update (@NonNull final EN16931UpdateDispatcher aDispatcher,
                           @NonNull final File aSourceFile,
                           @NonNull final int [] aCounts) throws IOException
  {
    final File aDestFile = new File (m_sOutputDir, FilenameHelper.getBaseName (aSourceFile) + m_sOutputFileSuffix + ".xml");
    LOGGER.info ("Updating '" + aSourceFile.getAbsolutePath () + "' to EN 16931:2026");

    // Never overwrite the source
    if (aDestFile.getCanonicalFile ().equals (aSourceFile.getCanonicalFile ()))
    {
      LOGGER.error ("The output file would overwrite the source file '" +
                    aSourceFile.getAbsolutePath () +
                    "' - choose another target directory or output suffix");
      aCounts[2]++;
      return false;
    }

    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = aDispatcher.update (aSourceFile, aErrorList);

    if (aResult.getStatus () == EUpdateStatus.SKIPPED)
    {
      _logAll (aErrorList);
      LOGGER.info ("Skipped '" + aSourceFile.getAbsolutePath () + "'");
      aCounts[1]++;
      return true;
    }

    // Success means a document and no error. Warnings and infos do not count.
    ByteArrayOutputStream aBAOS = null;
    if (aResult.hasDocument () && !aErrorList.containsAtLeastOneError ())
    {
      // Write to memory first, so that a failure never leaves a half written file behind
      aBAOS = new ByteArrayOutputStream ();
      if (EN16931UpdateDispatcher.writeDocument (aResult.getDocument (), aBAOS, aErrorList).isFailure ())
        aBAOS = null;
    }

    _logAll (aErrorList);
    if (aBAOS == null || aErrorList.containsAtLeastOneError ())
    {
      LOGGER.error ("Failed to update '" + aSourceFile.getAbsolutePath () + "' - nothing was written");
      aCounts[2]++;
      return false;
    }

    Files.createDirectories (aDestFile.getParentFile ().toPath ());
    Files.write (aDestFile.toPath (), aBAOS.toByteArray ());
    LOGGER.info ((aResult.getStatus () == EUpdateStatus.COPIED ? "Copied unchanged to '" : "Successfully wrote '") +
                 aDestFile.getAbsolutePath () +
                 "'");
    aCounts[0]++;
    return true;
  }

  // doing the business
  public Integer call () throws Exception
  {
    if (m_bVerbose)
      System.setProperty ("org.slf4j.simpleLogger.defaultLogLevel", "debug");

    m_sOutputDir = _normalizeOutputDirectory (m_sOutputDir);
    final EN16931UpdateDispatcher aDispatcher = new EN16931UpdateDispatcher ().setSourceEdition (_determineSourceEdition ())
                                                                              .setAlready2026Handling (m_eAlready2026Handling)
                                                                              .setStrict (m_bStrict)
                                                                              .setCreditNoteIDOnlyReferenceHandling (m_eCreditNoteIDOnlyReferenceHandling);
    // The values have no default - the setters of the sibling tools failed on null here
    if (StringHelper.isNotEmpty (m_sCustomizationID))
      aDispatcher.setCustomizationID (m_sCustomizationID);
    if (StringHelper.isNotEmpty (m_sProfileID))
      aDispatcher.setProfileID (m_sProfileID);

    final List <File> aSourceFiles = _normalizeInputFiles (m_aSourceFilenames);

    // updated or copied, skipped, failed
    final int [] aCounts = new int [3];
    for (final File aSourceFile : aSourceFiles)
      _update (aDispatcher, aSourceFile, aCounts);

    LOGGER.info ("Done: " +
                 aCounts[0] +
                 " written, " +
                 aCounts[1] +
                 " skipped (already EN 16931:2026), " +
                 aCounts[2] +
                 " failed");
    return Integer.valueOf (aCounts[2] > 0 ? 1 : 0);
  }

  public static void main (final String [] aArgs)
  {
    LOGGER.info ("EN 16931 Updater v" + UpdateVersion.BUILD_VERSION + " (build " + UpdateVersion.BUILD_TIMESTAMP + ")");

    final CommandLine cmd = new CommandLine (new EN16931UpdateCLI ());
    cmd.setCaseInsensitiveEnumValuesAllowed (true);
    final int nExitCode = cmd.execute (aArgs);
    System.exit (nExitCode);
  }
}
