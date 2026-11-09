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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import picocli.CommandLine;

/**
 * Test class for class {@link EN16931UpdateCLI}.
 *
 * @author Philip Helger
 */
public final class EN16931UpdateCLITest
{
  private static final String DIR = "src/test/resources/external/cli/";
  private static final String ID_2017 = "urn:cen.eu:en16931:2017";
  private static final String ID_2026 = "urn:cen.eu:en16931:2026";

  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  private static int _run (final String... aArgs)
  {
    final CommandLine aCmd = new CommandLine (new EN16931UpdateCLI ());
    aCmd.setCaseInsensitiveEnumValuesAllowed (true);
    return aCmd.execute (aArgs);
  }

  private static String _read (final File aFile) throws IOException
  {
    return Files.readString (aFile.toPath (), StandardCharsets.UTF_8);
  }

  private File _out (final String sName)
  {
    return new File (m_aTmp.getRoot (), sName);
  }

  private File _copyToTemp (final String sName) throws IOException
  {
    final File ret = _out ("in-" + sName);
    Files.copy (new File (DIR + sName).toPath (), ret.toPath (), StandardCopyOption.REPLACE_EXISTING);
    return ret;
  }

  @Test
  public void testUpdatesAllThreeKinds () throws Exception
  {
    final String sTarget = m_aTmp.getRoot ().getAbsolutePath ();
    assertEquals (0, _run ("-t", sTarget, DIR + "cii-2017.xml", DIR + "ubl-invoice-2017.xml", DIR + "ubl-creditnote-2017.xml"));
    for (final String sName : new String [] { "cii-2017-2026.xml", "ubl-invoice-2017-2026.xml", "ubl-creditnote-2017-2026.xml" })
    {
      assertTrue (sName, _out (sName).isFile ());
      final String sContent = _read (_out (sName));
      assertTrue (sName, sContent.contains (ID_2026));
      assertFalse (sName, sContent.contains (ID_2017));
    }
    // The CII one is D25A: BT-10 moved, which D16B cannot express
    assertTrue (_read (_out ("ubl-invoice-2017-2026.xml")).contains ("BuyerAssignedReference"));
  }

  @Test
  public void testWildcardAndOutputSuffix () throws Exception
  {
    final String sTarget = m_aTmp.getRoot ().getAbsolutePath ();
    assertEquals (0, _run ("-t", sTarget, "--output-suffix", "-new", DIR + "*-2017.xml"));
    assertTrue (_out ("cii-2017-new.xml").isFile ());
    assertTrue (_out ("ubl-invoice-2017-new.xml").isFile ());
    assertTrue (_out ("ubl-creditnote-2017-new.xml").isFile ());
    assertFalse (_out ("cii-legacy-new.xml").exists ());
  }

  @Test
  public void testDirectoryInputAndFailureExitCode () throws Exception
  {
    // The directory contains the legacy file without a usable BT-24: that one fails, the others are written
    final String sTarget = m_aTmp.getRoot ().getAbsolutePath ();
    assertEquals (1, _run ("-t", sTarget, DIR));
    assertTrue (_out ("cii-2017-2026.xml").isFile ());
    assertTrue (_out ("ubl-invoice-2017-2026.xml").isFile ());
    assertFalse (_out ("cii-legacy-2026.xml").exists ());
  }

  @Test
  public void testSourceEditionOverride () throws Exception
  {
    final String sTarget = m_aTmp.getRoot ().getAbsolutePath ();
    assertEquals (1, _run ("-t", sTarget, DIR + "cii-legacy.xml"));
    assertFalse (_out ("cii-legacy-2026.xml").exists ());

    assertEquals (0, _run ("-t", sTarget, "--source-edition", "2017", DIR + "cii-legacy.xml"));
    assertTrue (_read (_out ("cii-legacy-2026.xml")).contains (ID_2026));

    // Not an edition - a usage problem, not a crash
    assertEquals (1, _run ("-t", sTarget, "--source-edition", "1999", DIR + "cii-2017.xml"));
  }

  @Test
  public void testAlready2026 () throws Exception
  {
    final String sTarget = m_aTmp.getRoot ().getAbsolutePath ();
    assertEquals (0, _run ("-t", sTarget, DIR + "ubl-invoice-2017.xml"));
    final File aUpdated = _out ("ubl-invoice-2017-2026.xml");
    assertTrue (aUpdated.isFile ());

    // skip is the default: no output, no failure
    final File aTarget2 = m_aTmp.newFolder ("second");
    assertEquals (0, _run ("-t", aTarget2.getAbsolutePath (), aUpdated.getAbsolutePath ()));
    assertEquals (0, aTarget2.list ().length);

    // copy
    assertEquals (0, _run ("-t", aTarget2.getAbsolutePath (), "--on-2026", "copy", aUpdated.getAbsolutePath ()));
    assertEquals (_read (aUpdated), _read (new File (aTarget2, "ubl-invoice-2017-2026-2026.xml")));

    // fail
    final File aTarget3 = m_aTmp.newFolder ("third");
    assertEquals (1, _run ("-t", aTarget3.getAbsolutePath (), "--on-2026", "fail", aUpdated.getAbsolutePath ()));
    assertEquals (0, aTarget3.list ().length);
  }

  @Test
  public void testStrictWritesNothing () throws Exception
  {
    // The CII example has party identifiers without a scheme identifier
    final String sTarget = m_aTmp.getRoot ().getAbsolutePath ();
    assertEquals (1, _run ("-t", sTarget, "--strict", DIR + "cii-2017.xml"));
    assertFalse (_out ("cii-2017-2026.xml").exists ());
    // ... and is fine without
    assertEquals (0, _run ("-t", sTarget, DIR + "cii-2017.xml"));
    assertTrue (_out ("cii-2017-2026.xml").isFile ());
  }

  @Test
  public void testOverrides () throws Exception
  {
    final String sTarget = m_aTmp.getRoot ().getAbsolutePath ();
    assertEquals (0, _run ("-t", sTarget, "--customization-id", "urn:example:custom", "--profile-id", "urn:example:profile", DIR + "ubl-invoice-2017.xml"));
    final String sContent = _read (_out ("ubl-invoice-2017-2026.xml"));
    assertTrue (sContent.contains (">urn:example:custom<"));
    assertTrue (sContent.contains (">urn:example:profile<"));
  }

  @Test
  public void testCreditNoteOption () throws Exception
  {
    final String sTarget = m_aTmp.getRoot ().getAbsolutePath ();
    for (final String sMode : new String [] { "keep", "project", "document", "KEEP" })
      assertEquals (sMode, 0, _run ("-t", sTarget, "--cn-id-only-reference", sMode, DIR + "ubl-creditnote-2017.xml"));
    // Picocli rejects a wrong value (exit code 2) before anything is done
    assertEquals (2, _run ("-t", sTarget, "--cn-id-only-reference", "maybe", DIR + "ubl-creditnote-2017.xml"));
  }

  @Test
  public void testSourceIsNeverOverwritten () throws Exception
  {
    final File aSource = _copyToTemp ("ubl-invoice-2017.xml");
    final String sBefore = _read (aSource);
    // Same directory and an empty suffix would produce the source name - the name has the "in-" prefix
    assertEquals (1, _run ("-t", m_aTmp.getRoot ().getAbsolutePath (), "--output-suffix", "", aSource.getAbsolutePath ()));
    assertEquals (sBefore, _read (aSource));
  }

  @Test
  public void testNotXMLAndMissingFile () throws Exception
  {
    final File aBad = _out ("bad.xml");
    Files.writeString (aBad.toPath (), "this is not xml", StandardCharsets.UTF_8);
    assertEquals (1, _run ("-t", m_aTmp.getRoot ().getAbsolutePath (), aBad.getAbsolutePath ()));
    assertFalse (_out ("bad-2026.xml").exists ());

    // A file that does not exist is ignored with a warning - nothing failed
    assertEquals (0, _run ("-t", m_aTmp.getRoot ().getAbsolutePath (), _out ("does-not-exist.xml").getAbsolutePath ()));
  }

  @Test
  public void testHelpAndVersionAndMissingArguments ()
  {
    assertEquals (0, _run ("--help"));
    assertEquals (0, _run ("--version"));
    // At least one source file is required
    assertEquals (2, _run ());
  }
}
