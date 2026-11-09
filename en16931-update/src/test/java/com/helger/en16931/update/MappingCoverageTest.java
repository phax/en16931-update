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
package com.helger.en16931.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * The coverage guard (plan item A10). <code>docs/update-rules.md</code> section 5 classifies every
 * ID of the EN 16931:2026 mapping table. This test makes sure that classification is complete and
 * that it matches both the mapping tables and the code - so a row can no longer be missed the way
 * BT-122-1 was missed in the 4.0.0 work of en16931-cii2ubl, which is not in the "new in 2026" list.
 * <ul>
 * <li>every ID of the 2026 table is classified, and nothing else is</li>
 * <li>the class is one of the known ones and names a rule that exists</li>
 * <li>an ID that is in the 2017 table cannot be NEW or IMPLICIT; an ID that is not cannot be CARRIED
 * (apart from the groups BG-1 to BG-32 and the discriminator notes)</li>
 * <li>every REWRITE and CHECK ID is named in the code of the updaters, and no NEW ID is - a NEW ID in
 * the code means someone started to implement it and the classification must be revisited</li>
 * <li>every rule of {@link EUpdateRule} is used in the classification (except R9, which has no row
 * in the mapping table) and is described in the rule document</li>
 * </ul>
 *
 * @author Philip Helger
 */
public final class MappingCoverageTest
{
  private static final Pattern ROW = Pattern.compile ("^\\| ((?:BT|BG)-\\d.*?) \\|");
  private static final Pattern PURE_ID = Pattern.compile ("(?:BT|BG)-\\d+(?:-\\d+)*");
  private static final Pattern CLASS = Pattern.compile ("CARRIED|NEW|IMPLICIT|NOTE|REWRITE (R\\d)|CHECK (C\\d)|CARRIED \\+ CHECK (C\\d)");
  /** Rules without a row in the mapping table */
  private static final Set <String> RULES_WITHOUT_ROW = Set.of ("R9");

  /** @return the IDs of a mapping table, in order */
  static Set <String> readIDs (final String sMarkdown)
  {
    final Set <String> ret = new LinkedHashSet <> ();
    for (final String sLine : sMarkdown.split ("\n"))
    {
      final Matcher aMatcher = ROW.matcher (sLine);
      if (aMatcher.find ())
        ret.add (_firstColumn (sLine));
    }
    return ret;
  }

  private static String _firstColumn (final String sLine)
  {
    // The first column ends at the first unescaped bar after the leading one
    final String [] aCols = sLine.split ("(?<!\\\\)\\|");
    return aCols[1].trim ();
  }

  /** @return ID -> class of the classification table in section 5 */
  static Map <String, String> readClassification (final String sRules)
  {
    final int nStart = sRules.indexOf ("## 5. Classification");
    assertTrue ("Section 5 not found", nStart >= 0);
    final Map <String, String> ret = new LinkedHashMap <> ();
    for (final String sLine : sRules.substring (nStart).split ("\n"))
      if (ROW.matcher (sLine).find ())
      {
        final String [] aCols = sLine.split ("(?<!\\\\)\\|");
        final String sID = aCols[1].trim ();
        final String sClass = aCols[aCols.length - 1].trim ();
        if (ret.put (sID, sClass) != null)
          throw new IllegalStateException ("Duplicate row " + sID);
      }
    return ret;
  }

  private static boolean _mentions (final String sSource, final String sID)
  {
    // Exact: BT-9 must not be satisfied by BT-91 or BT-9-1
    return Pattern.compile (Pattern.quote (sID) + "(?![0-9]|-[0-9])").matcher (sSource).find ();
  }

  /**
   * @return All problems. Empty if the classification is complete and consistent.
   */
  static List <String> findProblems (final Set <String> aIDs2026,
                                     final Set <String> aIDs2017,
                                     final Map <String, String> aClassification,
                                     final String sMainSource,
                                     final String sRulesText)
  {
    final List <String> ret = new ArrayList <> ();

    for (final String sID : aIDs2026)
      if (!aClassification.containsKey (sID))
        ret.add ("Not classified: " + sID);
    for (final String sID : aClassification.keySet ())
      if (!aIDs2026.contains (sID))
        ret.add ("Classified, but not in the 2026 table: " + sID);

    final Set <String> aUsedRules = new LinkedHashSet <> ();
    for (final Map.Entry <String, String> aEntry : aClassification.entrySet ())
    {
      final String sID = aEntry.getKey ();
      final String sClass = aEntry.getValue ();
      final Matcher aClass = CLASS.matcher (sClass);
      if (!aClass.matches ())
      {
        ret.add ("Unknown class '" + sClass + "' of " + sID);
        continue;
      }
      for (int i = 1; i <= aClass.groupCount (); i++)
        if (aClass.group (i) != null)
        {
          aUsedRules.add (aClass.group (i));
          if (EUpdateRule.getFromIDOrNull (aClass.group (i)) == null)
            ret.add ("Unknown rule " + aClass.group (i) + " at " + sID);
        }

      final boolean bPure = PURE_ID.matcher (sID).matches ();
      if (!bPure != sClass.equals ("NOTE"))
        ret.add (sID + ": only the discriminator notes are NOTE, and every one of them is");
      final boolean bIn2017 = aIDs2017.contains (sID);
      final boolean bNewOrImplicit = sClass.equals ("NEW") || sClass.equals ("IMPLICIT");
      if (bIn2017 && bNewOrImplicit)
        ret.add (sID + " is in the 2017 table and cannot be " + sClass);
      if (sClass.equals ("NEW") && sID.startsWith ("BT-") == false && sID.startsWith ("BG-") == false)
        ret.add (sID + ": odd ID");
      final boolean bBG1To32 = sID.matches ("BG-([1-9]|[12][0-9]|3[0-2])");
      if (bPure && !bIn2017 && !bBG1To32 && sClass.startsWith ("CARRIED") && !sClass.contains ("CHECK"))
        ret.add (sID + " is not in the 2017 table and cannot be CARRIED");

      // The code
      final boolean bMustBeInCode = sClass.startsWith ("REWRITE") || sClass.contains ("CHECK");
      if (bMustBeInCode && !_mentions (sMainSource, sID))
        ret.add (sID + " is " + sClass + " but the code of the updaters never names it");
      if (sClass.equals ("NEW") && _mentions (sMainSource, sID))
        ret.add (sID + " is NEW (never emitted) but the code names it");
    }

    for (final EUpdateRule eRule : EUpdateRule.values ())
    {
      if (!RULES_WITHOUT_ROW.contains (eRule.getID ()) && !aUsedRules.contains (eRule.getID ()))
        ret.add ("Rule " + eRule.getID () + " is not used in the classification");
      if (!sRulesText.contains ("**" + eRule.getID () + "**"))
        ret.add ("Rule " + eRule.getID () + " is not described in the rule document");
    }
    return ret;
  }

  private static String _read (final String sPath) throws IOException
  {
    return Files.readString (Path.of (sPath), StandardCharsets.UTF_8);
  }

  private static String _mainSource () throws IOException
  {
    try (final Stream <Path> aStream = Files.walk (Path.of ("src/main/java")))
    {
      final StringBuilder aSB = new StringBuilder ();
      for (final Path aPath : aStream.filter (x -> x.toString ().endsWith (".java")).sorted ().collect (Collectors.toList ()))
        aSB.append (Files.readString (aPath, StandardCharsets.UTF_8)).append ('\n');
      return aSB.toString ();
    }
  }

  @Test
  public void testEveryRowOfTheMappingTableIsClassifiedAndMatchesTheCode () throws Exception
  {
    final Set <String> aIDs2026 = readIDs (_read ("../docs/en16931-2026-syntax.md"));
    final Set <String> aIDs2017 = readIDs (_read ("../docs/en16931-2017-syntax.md"));
    final String sRules = _read ("../docs/update-rules.md");
    final Map <String, String> aClassification = readClassification (sRules);

    // Guards against tables that silently changed
    assertEquals (299, aIDs2026.size ());
    assertEquals (299, aClassification.size ());
    assertEquals (186, aIDs2017.size ());

    final List <String> aProblems = findProblems (aIDs2026, aIDs2017, aClassification, _mainSource (), sRules);
    assertTrue (String.join ("\n", aProblems), aProblems.isEmpty ());
  }

  // ---- The guard must be able to fail

  private static final Set <String> IDS_2026 = new LinkedHashSet <> (List.of ("BT-1", "BT-10", "BT-122-1", "BT-166", "BG-1"));
  private static final Set <String> IDS_2017 = new LinkedHashSet <> (List.of ("BT-1", "BT-10", "BG-1"));
  private static final String RULES_TEXT = "**R1** **R2** **R3** **R4** **R5** **R6** **R7** **R9** **C1** **C2**";
  // Names BT-1 through the rule, but never BT-166
  private static final String SOURCE = "BT-10 BT-122-1 Rule R1";

  private static Map <String, String> _good ()
  {
    final Map <String, String> ret = new LinkedHashMap <> ();
    ret.put ("BT-1", "CARRIED");
    ret.put ("BT-10", "REWRITE R2");
    ret.put ("BT-122-1", "REWRITE R7");
    ret.put ("BT-166", "NEW");
    ret.put ("BG-1", "CARRIED");
    return ret;
  }

  private static List <String> _problems (final Set <String> a2026, final Map <String, String> aClassification, final String sSource)
  {
    return findProblems (a2026, IDS_2017, aClassification, sSource, RULES_TEXT);
  }

  private static boolean _anyContains (final List <String> aProblems, final String sText)
  {
    return aProblems.stream ().anyMatch (x -> x.contains (sText));
  }

  @Test
  public void testGuardFindsMissingAndSurplusRows ()
  {
    Map <String, String> a = _good ();
    a.remove ("BT-166");
    assertTrue (_anyContains (_problems (IDS_2026, a, SOURCE), "Not classified: BT-166"));

    a = _good ();
    a.put ("BT-999", "NEW");
    assertTrue (_anyContains (_problems (IDS_2026, a, SOURCE), "Classified, but not in the 2026 table: BT-999"));
  }

  @Test
  public void testGuardFindsBadClasses ()
  {
    Map <String, String> a = _good ();
    a.put ("BT-1", "CARRIED?");
    assertTrue (_anyContains (_problems (IDS_2026, a, SOURCE), "Unknown class"));

    a = _good ();
    a.put ("BT-10", "REWRITE R8");
    assertTrue (_anyContains (_problems (IDS_2026, a, SOURCE), "Unknown rule R8"));

    // In the 2017 table, so it cannot be new
    a = _good ();
    a.put ("BT-10", "NEW");
    assertTrue (_anyContains (_problems (IDS_2026, a, SOURCE), "cannot be NEW"));

    // Not in the 2017 table, so it is not carried over
    a = _good ();
    a.put ("BT-166", "CARRIED");
    assertTrue (_anyContains (_problems (IDS_2026, a, SOURCE), "cannot be CARRIED"));
  }

  @Test
  public void testGuardTiesTheClassificationToTheCode ()
  {
    // A rewrite nobody implemented
    assertTrue (_anyContains (_problems (IDS_2026, _good (), "BT-122-1 Rule R1"), "BT-10 is REWRITE R2 but the code of the updaters never names it"));
    // BT-10 must not be satisfied by BT-10-1 or BT-101
    assertTrue (_anyContains (_problems (IDS_2026, _good (), "BT-10-1 BT-101 BT-122-1"), "BT-10 is REWRITE"));
    // Something classified NEW that the code already names
    assertTrue (_anyContains (_problems (IDS_2026, _good (), SOURCE + " BT-166"), "BT-166 is NEW"));
    // The clean case has no problem except the rules that this toy classification does not use
    assertTrue (_problems (IDS_2026, _good (), SOURCE).stream ().allMatch (x -> x.startsWith ("Rule ")));
  }

  @Test
  public void testGuardChecksTheRules ()
  {
    assertTrue (_anyContains (_problems (IDS_2026, _good (), SOURCE), "Rule R3 is not used"));
    final List <String> aProblems = findProblems (IDS_2026, IDS_2017, _good (), SOURCE, "**R1**");
    assertTrue (_anyContains (aProblems, "Rule R2 is not described"));
    // R9 has no row in the mapping table, so it needs none
    assertTrue (!_anyContains (_problems (IDS_2026, _good (), SOURCE), "Rule R9 is not used"));
  }

  @Test
  public void testReaders ()
  {
    final Set <String> a = readIDs ("| ID | Name |\n|----|----|\n| BT-1 | x | y |\n| BT-2 \\| z | a |\n| BG-3 | b |\nnot a row\n");
    assertEquals (List.of ("BT-1", "BT-2 \\| z", "BG-3"), new ArrayList <> (a));
  }
}
