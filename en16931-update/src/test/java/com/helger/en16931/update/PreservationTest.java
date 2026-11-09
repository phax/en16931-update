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

import static com.helger.en16931.update.UpdateTestHelper.countApplied;
import static com.helger.en16931.update.UpdateTestHelper.listXML;
import static com.helger.en16931.update.UpdateTestHelper.parse;
import static com.helger.en16931.update.UpdateTestHelper.read;
import static com.helger.en16931.update.UpdateTestHelper.writeToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;
import org.w3c.dom.Document;

import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931Edition;
import com.helger.en16931.update.XMLDiffer.Diff;
import com.helger.en16931.update.XMLDiffer.EKind;

/**
 * The preservation test (plan item A9, decision D6c): an update may only change what its rules
 * name. Every input of the corpus is updated and compared with the output element by element. Every
 * difference must be explained by an entry of the allow list below - which is tied to a rule - and
 * the number of differences of a rule must equal the number of rewrites the updater reported for
 * it. So nothing is lost silently, and nothing is reported without a change.
 *
 * @author Philip Helger
 */
public final class PreservationTest
{
  private static final String UBL_ROOT = "/(?:inv:Invoice|cn:CreditNote)\\[1\\]";
  private static final String CII_AGREEMENT = "/rsm:CrossIndustryInvoice\\[1\\]/rsm:SupplyChainTradeTransaction\\[1\\]/ram:ApplicableHeaderTradeAgreement\\[1\\]";

  /** One kind of difference a rule is allowed to cause */
  private record Allowed (EUpdateRule rule, EKind kind, Pattern path)
  {
    Allowed (final EUpdateRule eRule, final EKind eKind, final String sPathRegex)
    {
      this (eRule, eKind, Pattern.compile ("^" + sPathRegex + "$"));
    }

    boolean matches (final Diff aDiff)
    {
      return kind == aDiff.kind () && path.matcher (aDiff.path ()).matches ();
    }
  }

  private static final List <Allowed> ALLOWED = List.of (
                                                         // R1 BT-24
                                                         new Allowed (EUpdateRule.R1,
                                                                      EKind.TEXT_CHANGED,
                                                                      "/rsm:CrossIndustryInvoice\\[1\\]/rsm:ExchangedDocumentContext\\[1\\]/ram:GuidelineSpecifiedDocumentContextParameter\\[\\d+\\]/ram:ID\\[1\\]"),
                                                         new Allowed (EUpdateRule.R1,
                                                                      EKind.TEXT_CHANGED,
                                                                      UBL_ROOT + "/cbc:CustomizationID\\[1\\]"),
                                                         // R2 BT-10
                                                         new Allowed (EUpdateRule.R2,
                                                                      EKind.ELEMENT_LOST,
                                                                      CII_AGREEMENT + "/ram:BuyerReference\\[\\d+\\]"),
                                                         new Allowed (EUpdateRule.R2,
                                                                      EKind.ELEMENT_ADDED,
                                                                      CII_AGREEMENT +
                                                                                           "/ram:BuyerReferenceID\\[\\d+\\]"),
                                                         new Allowed (EUpdateRule.R2,
                                                                      EKind.ELEMENT_LOST,
                                                                      UBL_ROOT + "/cbc:BuyerReference\\[\\d+\\]"),
                                                         new Allowed (EUpdateRule.R2,
                                                                      EKind.ELEMENT_ADDED,
                                                                      UBL_ROOT +
                                                                                           "/cac:BuyerAssignedReference\\[\\d+\\]"),
                                                         // R3 BG-1 - the header notes only
                                                         new Allowed (EUpdateRule.R3,
                                                                      EKind.ELEMENT_LOST,
                                                                      UBL_ROOT + "/cbc:Note\\[\\d+\\]"),
                                                         new Allowed (EUpdateRule.R3,
                                                                      EKind.ELEMENT_ADDED,
                                                                      UBL_ROOT + "/cac:Annotation\\[\\d+\\]"),
                                                         // R4 BT-9 of the Credit Note
                                                         new Allowed (EUpdateRule.R4,
                                                                      EKind.ELEMENT_LOST,
                                                                      "/cn:CreditNote\\[1\\]/cac:PaymentMeans\\[\\d+\\]/cbc:PaymentDueDate\\[1\\]"),
                                                         new Allowed (EUpdateRule.R4,
                                                                      EKind.ELEMENT_ADDED,
                                                                      "/cn:CreditNote\\[1\\]/cbc:DueDate\\[1\\]"),
                                                         // R6 BT-32-2
                                                         new Allowed (EUpdateRule.R6,
                                                                      EKind.TEXT_CHANGED,
                                                                      UBL_ROOT +
                                                                                          "/cac:AccountingSupplierParty\\[1\\]/cac:Party\\[1\\]/cac:PartyTaxScheme\\[\\d+\\]/cac:TaxScheme\\[1\\]/cbc:ID\\[1\\]"),
                                                         // R7 BG-24
                                                         new Allowed (EUpdateRule.R7,
                                                                      EKind.ELEMENT_ADDED,
                                                                      UBL_ROOT +
                                                                                           "/cac:AdditionalDocumentReference\\[\\d+\\]/cbc:DocumentTypeCode\\[1\\]"),
                                                         // R9 UBL version
                                                         new Allowed (EUpdateRule.R9,
                                                                      EKind.TEXT_CHANGED,
                                                                      UBL_ROOT + "/cbc:UBLVersionID\\[1\\]"));

  /** Not a rule: the schema location of the root is dropped by JAXB (plan item A0) */
  private static final Pattern SCHEMA_LOCATION = Pattern.compile ("^/[^/]+\\[1\\]/@xsi:schemaLocation$");

  private static String _readString (final String sFilename) throws Exception
  {
    return Files.readString (new File (sFilename).toPath (), StandardCharsets.UTF_8);
  }

  /** @return The differences that no rule explains */
  private static List <Diff> _check (final String sName,
                                     final Document aIn,
                                     final ErrorList aErrorList,
                                     final Document aOut)
  {
    final Map <EUpdateRule, Map <EKind, Integer>> aCounts = new EnumMap <> (EUpdateRule.class);
    final List <Diff> aUnexplained = new ArrayList <> ();
    for (final Diff aDiff : XMLDiffer.diff (aIn.getDocumentElement (), aOut.getDocumentElement ()))
    {
      if (aDiff.kind () == EKind.ATTRIBUTE_LOST && SCHEMA_LOCATION.matcher (aDiff.path ()).matches ())
        continue;
      Allowed aMatch = null;
      for (final Allowed a : ALLOWED)
        if (a.matches (aDiff))
        {
          aMatch = a;
          break;
        }
      if (aMatch == null)
        aUnexplained.add (aDiff);
      else
        aCounts.computeIfAbsent (aMatch.rule (), k -> new EnumMap <> (EKind.class))
               .merge (aMatch.kind (), Integer.valueOf (1), Integer::sum);
    }

    // The reports and the differences must agree
    for (final EUpdateRule eRule : EUpdateRule.values ())
    {
      final Map <EKind, Integer> aOfRule = aCounts.getOrDefault (eRule, Map.of ());
      final long nApplied = countApplied (aErrorList, eRule);
      if (eRule == EUpdateRule.R1)
        assertEquals (sName + " R1", 1, aOfRule.getOrDefault (EKind.TEXT_CHANGED, Integer.valueOf (0)).intValue ());
      else
        if (eRule == EUpdateRule.R6 || eRule == EUpdateRule.R9)
          assertEquals (sName + " " + eRule,
                        nApplied,
                        aOfRule.getOrDefault (EKind.TEXT_CHANGED, Integer.valueOf (0)).intValue ());
        else
          if (eRule == EUpdateRule.R7)
            assertEquals (sName + " R7",
                          nApplied,
                          aOfRule.getOrDefault (EKind.ELEMENT_ADDED, Integer.valueOf (0)).intValue ());
          else
            if (eRule == EUpdateRule.C1 || eRule == EUpdateRule.C2 || eRule == EUpdateRule.R5)
              assertTrue (sName + " " + eRule + " must change nothing", aOfRule.isEmpty ());
            else
            {
              // R2, R3, R4: one element lost and one added per applied rewrite
              assertEquals (sName + " " + eRule + " lost",
                            nApplied,
                            aOfRule.getOrDefault (EKind.ELEMENT_LOST, Integer.valueOf (0)).intValue ());
              assertEquals (sName + " " + eRule + " added",
                            nApplied,
                            aOfRule.getOrDefault (EKind.ELEMENT_ADDED, Integer.valueOf (0)).intValue ());
            }
    }
    return aUnexplained;
  }

  private static void _checkFile (final String sName, final Document aIn) throws Exception
  {
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = new EN16931UpdateDispatcher ().setSourceEdition (EEN16931Edition.EN2017)
                                                               .update (aIn, aErrorList);
    assertFalse (sName + ": " + aErrorList.getAllErrors (), aErrorList.containsAtLeastOneError ());
    final Document aOut = parse (writeToString (aResult.getDocument ()));
    final List <Diff> aUnexplained = _check (sName, aIn, aErrorList, aOut);
    assertTrue (sName + " has changes no rule explains: " + aUnexplained, aUnexplained.isEmpty ());
  }

  @Test
  public void testWholeCorpus () throws Exception
  {
    final List <File> aFiles = new ArrayList <> ();
    aFiles.addAll (listXML ("external/cii"));
    aFiles.addAll (listXML ("external/ubl21"));
    aFiles.addAll (listXML ("edge/ubl21"));
    assertEquals (121, aFiles.size ());
    for (final File aFile : aFiles)
      _checkFile (aFile.getPath (), read (aFile));
  }

  @Test
  public void testHandBuiltInvoiceWithEveryRule () throws Exception
  {
    String sXML = _readString ("src/test/resources/external/ubl21/inv/peppol/base-example.xml");
    sXML = sXML.replace ("<cbc:CustomizationID>", "<cbc:UBLVersionID>2.1</cbc:UBLVersionID><cbc:CustomizationID>");
    sXML = sXML.replaceFirst ("(</cbc:InvoiceTypeCode>)",
                              "$1<cbc:Note>#AAI#Hello</cbc:Note><cbc:Note>plain</cbc:Note>");
    sXML = sXML.replace ("<cac:AccountingSupplierParty>",
                         "<cac:AdditionalDocumentReference><cbc:ID>REF-1</cbc:ID></cac:AdditionalDocumentReference><cac:AccountingSupplierParty>");
    sXML = sXML.replaceFirst ("<cac:PartyLegalEntity>",
                              "<cac:PartyTaxScheme><cbc:CompanyID>TAX-1</cbc:CompanyID><cac:TaxScheme><cbc:ID>GST</cbc:ID></cac:TaxScheme></cac:PartyTaxScheme><cac:PartyLegalEntity>");
    final Document aIn = parse (sXML);

    final ErrorList aErrorList = new ErrorList ();
    final Document aOut = parse (writeToString (new EN16931UpdateDispatcher ().update (aIn, aErrorList)
                                                                              .getDocument ()));
    assertTrue (_check ("hand-built invoice", aIn, aErrorList, aOut).isEmpty ());
    // Every rule of the Invoice really was exercised
    for (final EUpdateRule eRule : new EUpdateRule [] { EUpdateRule.R1,
                                                        EUpdateRule.R2,
                                                        EUpdateRule.R3,
                                                        EUpdateRule.R6,
                                                        EUpdateRule.R7,
                                                        EUpdateRule.R9 })
      assertTrue (eRule.getID (), countApplied (aErrorList, eRule) > 0);
  }

  @Test
  public void testHandBuiltCreditNoteWithEveryRule () throws Exception
  {
    String sXML = _readString ("src/test/resources/external/ubl21/cn/peppol/base-creditnote-correction.xml");
    sXML = sXML.replace ("</cbc:PaymentMeansCode>",
                         "</cbc:PaymentMeansCode><cbc:PaymentDueDate>2026-01-15</cbc:PaymentDueDate>");
    final Document aIn = parse (sXML);

    final ErrorList aErrorList = new ErrorList ();
    final Document aOut = parse (writeToString (new EN16931UpdateDispatcher ().update (aIn, aErrorList)
                                                                              .getDocument ()));
    assertTrue (_check ("hand-built credit note", aIn, aErrorList, aOut).isEmpty ());
    assertEquals (1, countApplied (aErrorList, EUpdateRule.R4));
  }

  /** The test must be able to fail: take a real update and damage the output */
  @Test
  public void testDamageIsDetected () throws Exception
  {
    final File aFile = new File ("src/test/resources/external/ubl21/inv/peppol/base-example.xml");
    final Document aIn = read (aFile);
    final ErrorList aErrorList = new ErrorList ();
    final String sOut = writeToString (new EN16931UpdateDispatcher ().update (aIn, aErrorList).getDocument ());

    // Change a text, change another text, lose an element, add an attribute
    for (final String [] aDamage : new String [] [] { { "(<cbc:Name>)[^<]*", "$1CHANGED" },
                                                      { "<cbc:DocumentCurrencyCode>EUR<",
                                                        "<cbc:DocumentCurrencyCode>USD<" },
                                                      { "<cbc:Name>[^<]*</cbc:Name>", "" },
                                                      { "(<cbc:DocumentCurrencyCode)>", "$1 listID=\"x\">" } })
    {
      assertTrue (aDamage[0], Pattern.compile (aDamage[0]).matcher (sOut).find ());
      final Document aDamaged = parse (sOut.replaceFirst (aDamage[0], aDamage[1]));
      assertFalse (aDamage[0], _check ("damaged", aIn, aErrorList, aDamaged).isEmpty ());
    }
  }
}
