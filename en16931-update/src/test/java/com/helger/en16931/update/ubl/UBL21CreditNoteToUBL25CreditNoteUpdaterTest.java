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
package com.helger.en16931.update.ubl;

import static com.helger.en16931.update.UpdateTestHelper.count;
import static com.helger.en16931.update.UpdateTestHelper.countApplied;
import static com.helger.en16931.update.UpdateTestHelper.countRule;
import static com.helger.en16931.update.UpdateTestHelper.listXML;
import static com.helger.en16931.update.UpdateTestHelper.parse;
import static com.helger.en16931.update.UpdateTestHelper.read;
import static com.helger.en16931.update.UpdateTestHelper.string;
import static com.helger.en16931.update.UpdateTestHelper.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.w3c.dom.Document;

import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.update.ECreditNoteIDOnlyReferenceHandling;
import com.helger.en16931.update.EUpdateRule;

/**
 * Test class for class {@link UBL21CreditNoteToUBL25CreditNoteUpdater}. The expectations are
 * computed with XPath directly on the input document, independent of the JAXB code of the updater.
 *
 * @author Philip Helger
 */
public final class UBL21CreditNoteToUBL25CreditNoteUpdaterTest
{
  private static final String ID_2017 = "urn:cen.eu:en16931:2017";
  private static final String ID_2026 = "urn:cen.eu:en16931:2026";
  private static final String BASE = "src/test/resources/external/ubl21/cn/peppol/base-creditnote-correction.xml";
  private static final String NO_SCHEME = "[not(@schemeID) or @schemeID='']";
  private static final String REFS = "/cn:CreditNote/cac:AdditionalDocumentReference";

  private static List <File> _creditNotes () throws Exception
  {
    final List <File> ret = new ArrayList <> ();
    ret.addAll (listXML ("external/ubl21"));
    ret.addAll (listXML ("edge/ubl21"));
    ret.removeIf (x -> !read (x).getDocumentElement ().getLocalName ().equals ("CreditNote"));
    return ret;
  }

  private static String _readString (final String sFilename) throws Exception
  {
    return Files.readString (new File (sFilename).toPath (), StandardCharsets.UTF_8);
  }

  private static long _expectedC1 (final Document aDoc) throws Exception
  {
    long ret = 0;
    for (final String sParty : new String [] { "/cn:CreditNote/cac:AccountingSupplierParty/cac:Party",
                                               "/cn:CreditNote/cac:AccountingCustomerParty/cac:Party",
                                               "/cn:CreditNote/cac:PayeeParty" })
    {
      ret += count (aDoc, sParty + "/cac:PartyIdentification/cbc:ID" + NO_SCHEME);
      ret += count (aDoc, sParty + "/cac:PartyLegalEntity/cbc:CompanyID" + NO_SCHEME);
    }
    return ret + count (aDoc, "/cn:CreditNote/cac:Delivery/cac:DeliveryLocation/cbc:ID" + NO_SCHEME);
  }

  /** Insert the references before the Seller - after all other references and before the Seller */
  private static String _withReferences (final String sRefs) throws Exception
  {
    return _readString (BASE).replace ("<cac:AccountingSupplierParty>", sRefs + "<cac:AccountingSupplierParty>");
  }

  private static Document _update (final String sXML, final ECreditNoteIDOnlyReferenceHandling eHandling, final ErrorList aErrorList) throws Exception
  {
    final Serializable aResult = new UBL21CreditNoteToUBL25CreditNoteUpdater ().setCreditNoteIDOnlyReferenceHandling (eHandling)
                                                                                .update (parse (sXML), aErrorList);
    assertFalse (aErrorList.getAllErrors ().toString (), aErrorList.containsAtLeastOneError ());
    return write (aResult);
  }

  @Test
  public void testWholeCorpus () throws Exception
  {
    int nCreditNotes = 0;
    for (final File aFile : _creditNotes ())
    {
      final String sName = aFile.getPath ();
      final Document aIn = read (aFile);
      final ErrorList aErrorList = new ErrorList ();
      final Serializable aResult = new UBL21CreditNoteToUBL25CreditNoteUpdater ().update (aIn, aErrorList);
      assertFalse (sName + ": " + aErrorList.getAllErrors (), aErrorList.containsAtLeastOneError ());
      final Document aOut = write (aResult);
      nCreditNotes++;

      // R1
      final String sOld = string (aIn, "/cn:CreditNote/cbc:CustomizationID");
      assertTrue (sName, sOld.startsWith (ID_2017));
      assertEquals (sName, ID_2026 + sOld.substring (ID_2017.length ()), string (aOut, "/cn:CreditNote/cbc:CustomizationID"));

      // R2
      assertEquals (sName, 0, count (aOut, "/cn:CreditNote/cbc:BuyerReference"));
      assertEquals (sName, string (aIn, "/cn:CreditNote/cbc:BuyerReference"), string (aOut, "/cn:CreditNote/cac:BuyerAssignedReference/cbc:BuyerReference"));

      // R3 - the header notes only
      final long nNotes = count (aIn, "/cn:CreditNote/cbc:Note");
      assertEquals (sName, 0, count (aOut, "/cn:CreditNote/cbc:Note"));
      assertEquals (sName, nNotes, count (aOut, "/cn:CreditNote/cac:Annotation"));
      assertEquals (sName, nNotes, countApplied (aErrorList, EUpdateRule.R3));
      assertEquals (sName, count (aIn, "/cn:CreditNote/cac:CreditNoteLine/cbc:Note"), count (aOut, "/cn:CreditNote/cac:CreditNoteLine/cbc:Note"));

      // R4 - nothing is left in the payment means
      assertEquals (sName, 0, count (aOut, "/cn:CreditNote/cac:PaymentMeans/cbc:PaymentDueDate"));
      assertEquals (sName,
                    count (aIn, "/cn:CreditNote/cac:PaymentMeans/cbc:PaymentDueDate") > 0 ? 1 : 0,
                    count (aOut, "/cn:CreditNote/cbc:DueDate"));

      // C1, C2
      assertEquals (sName, _expectedC1 (aIn), countRule (aErrorList, EUpdateRule.C1));
    }
    assertEquals (3, nCreditNotes);
  }

  @Test
  public void testR4PaymentDueDate () throws Exception
  {
    final String sBase = _readString (BASE);
    final String sXML = sBase.replace ("</cbc:PaymentMeansCode>", "</cbc:PaymentMeansCode><cbc:PaymentDueDate>2026-01-15</cbc:PaymentDueDate>");
    assertEquals (1, count (parse (sXML), "/cn:CreditNote/cac:PaymentMeans/cbc:PaymentDueDate"));

    final ErrorList aErrorList = new ErrorList ();
    final Document aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.KEEP, aErrorList);
    assertEquals ("2026-01-15", string (aOut, "/cn:CreditNote/cbc:DueDate"));
    assertEquals (0, count (aOut, "/cn:CreditNote/cac:PaymentMeans/cbc:PaymentDueDate"));
    // The rest of the payment means is untouched
    assertEquals (string (read (new File (BASE)), "/cn:CreditNote/cac:PaymentMeans/cbc:PaymentMeansCode"),
                  string (aOut, "/cn:CreditNote/cac:PaymentMeans/cbc:PaymentMeansCode"));
    assertEquals (1, countApplied (aErrorList, EUpdateRule.R4));
    assertEquals (1, countRule (aErrorList, EUpdateRule.R4));
  }

  @Test
  public void testR4DifferingDatesAndExistingDueDate () throws Exception
  {
    final String sBase = _readString (BASE);
    final String sFirst = sBase.substring (sBase.indexOf ("<cac:PaymentMeans>"), sBase.indexOf ("</cac:PaymentMeans>") + "</cac:PaymentMeans>".length ());

    // Two payment means with different due dates: BT-9 is 0..1, so only the first one can move
    String sXML = sBase.replace (sFirst,
                                 sFirst.replace ("</cbc:PaymentMeansCode>", "</cbc:PaymentMeansCode><cbc:PaymentDueDate>2026-01-15</cbc:PaymentDueDate>") +
                                       sFirst.replace ("</cbc:PaymentMeansCode>", "</cbc:PaymentMeansCode><cbc:PaymentDueDate>2026-02-20</cbc:PaymentDueDate>"));
    ErrorList aErrorList = new ErrorList ();
    Document aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.KEEP, aErrorList);
    assertEquals ("2026-01-15", string (aOut, "/cn:CreditNote/cbc:DueDate"));
    assertEquals ("2026-02-20", string (aOut, "/cn:CreditNote/cac:PaymentMeans/cbc:PaymentDueDate"));
    assertEquals (1, countApplied (aErrorList, EUpdateRule.R4));
    assertEquals (2, countRule (aErrorList, EUpdateRule.R4));

    // A document that already has the new element is not a 2017 document: nothing is moved
    sXML = sBase.replace ("</cbc:PaymentMeansCode>", "</cbc:PaymentMeansCode><cbc:PaymentDueDate>2026-01-15</cbc:PaymentDueDate>")
                .replace ("<cbc:CreditNoteTypeCode", "<cbc:DueDate>2026-03-01</cbc:DueDate><cbc:CreditNoteTypeCode");
    aErrorList = new ErrorList ();
    aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.KEEP, aErrorList);
    assertEquals ("2026-03-01", string (aOut, "/cn:CreditNote/cbc:DueDate"));
    assertEquals ("2026-01-15", string (aOut, "/cn:CreditNote/cac:PaymentMeans/cbc:PaymentDueDate"));
    assertEquals (0, countApplied (aErrorList, EUpdateRule.R4));
    assertEquals (1, countRule (aErrorList, EUpdateRule.R4));
  }

  @Test
  public void testIDOnlyReferenceKeep () throws Exception
  {
    // KEEP is the default
    assertEquals (ECreditNoteIDOnlyReferenceHandling.KEEP, new UBL21CreditNoteToUBL25CreditNoteUpdater ().getCreditNoteIDOnlyReferenceHandling ());

    final String sXML = _withReferences ("<cac:AdditionalDocumentReference><cbc:ID>PRJ-1</cbc:ID></cac:AdditionalDocumentReference>");
    final ErrorList aErrorList = new ErrorList ();
    final Document aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.KEEP, aErrorList);
    assertEquals (1, count (aOut, REFS));
    assertEquals (0, count (aOut, REFS + "/cbc:DocumentTypeCode"));
    assertEquals (0, count (aOut, "/cn:CreditNote/cac:ProjectReference"));
    assertEquals (1, countRule (aErrorList, EUpdateRule.R5));
    assertEquals (0, countApplied (aErrorList, EUpdateRule.R5));
    assertEquals (0, countRule (aErrorList, EUpdateRule.R7));
  }

  @Test
  public void testIDOnlyReferenceProject () throws Exception
  {
    final String sXML = _withReferences ("<cac:AdditionalDocumentReference><cbc:ID>PRJ-1</cbc:ID></cac:AdditionalDocumentReference>");
    final ErrorList aErrorList = new ErrorList ();
    final Document aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.PROJECT, aErrorList);
    assertEquals (0, count (aOut, REFS));
    assertEquals ("PRJ-1", string (aOut, "/cn:CreditNote/cac:ProjectReference/cbc:ID"));
    assertEquals (1, countApplied (aErrorList, EUpdateRule.R5));
  }

  @Test
  public void testIDOnlyReferenceDocument () throws Exception
  {
    final String sXML = _withReferences ("<cac:AdditionalDocumentReference><cbc:ID>DOC-1</cbc:ID></cac:AdditionalDocumentReference>");
    final ErrorList aErrorList = new ErrorList ();
    final Document aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.DOCUMENT, aErrorList);
    assertEquals (1, count (aOut, REFS));
    assertEquals ("916", string (aOut, REFS + "/cbc:DocumentTypeCode"));
    assertEquals ("1001", string (aOut, REFS + "/cbc:DocumentTypeCode/@listID"));
    assertEquals (0, count (aOut, "/cn:CreditNote/cac:ProjectReference"));
    assertEquals (1, countApplied (aErrorList, EUpdateRule.R7));
    assertEquals (0, countRule (aErrorList, EUpdateRule.R5));
  }

  @Test
  public void testSeveralIDOnlyReferencesAreNeverMovedToProject () throws Exception
  {
    // BT-11 is 0..1, so at most one of them could be BT-11 - and which one is not known
    final String sXML = _withReferences ("<cac:AdditionalDocumentReference><cbc:ID>A</cbc:ID></cac:AdditionalDocumentReference>" +
                                         "<cac:AdditionalDocumentReference><cbc:ID>B</cbc:ID></cac:AdditionalDocumentReference>");
    ErrorList aErrorList = new ErrorList ();
    Document aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.PROJECT, aErrorList);
    assertEquals (2, count (aOut, REFS));
    assertEquals (0, count (aOut, "/cn:CreditNote/cac:ProjectReference"));
    assertEquals (2, countRule (aErrorList, EUpdateRule.R5));
    assertEquals (0, countApplied (aErrorList, EUpdateRule.R5));

    // ... but both are documents, if the user says so
    aErrorList = new ErrorList ();
    aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.DOCUMENT, aErrorList);
    assertEquals (2, count (aOut, REFS + "/cbc:DocumentTypeCode[.='916']"));
  }

  @Test
  public void testProjectReferenceAlreadyThere () throws Exception
  {
    final String sXML = _withReferences ("<cac:AdditionalDocumentReference><cbc:ID>PRJ-1</cbc:ID></cac:AdditionalDocumentReference>" +
                                         "<cac:ProjectReference><cbc:ID>EXISTING</cbc:ID></cac:ProjectReference>");
    final ErrorList aErrorList = new ErrorList ();
    final Document aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.PROJECT, aErrorList);
    assertEquals (1, count (aOut, REFS));
    assertEquals (1, count (aOut, "/cn:CreditNote/cac:ProjectReference"));
    assertEquals ("EXISTING", string (aOut, "/cn:CreditNote/cac:ProjectReference/cbc:ID"));
    assertEquals (1, countRule (aErrorList, EUpdateRule.R5));
    assertEquals (0, countApplied (aErrorList, EUpdateRule.R5));
  }

  @Test
  public void testTypedAndDescribedReferences () throws Exception
  {
    // BT-18 ('ATS'), a typed one, a described one (BG-24), and an ambiguous one
    final String sXML = _withReferences ("<cac:AdditionalDocumentReference><cbc:ID>OBJ-1</cbc:ID><cbc:DocumentType>ATS</cbc:DocumentType></cac:AdditionalDocumentReference>" +
                                         "<cac:AdditionalDocumentReference><cbc:ID>TYPED-1</cbc:ID><cbc:DocumentTypeCode>130</cbc:DocumentTypeCode></cac:AdditionalDocumentReference>" +
                                         "<cac:AdditionalDocumentReference><cbc:ID>DOC-1</cbc:ID><cbc:DocumentDescription>A description</cbc:DocumentDescription></cac:AdditionalDocumentReference>" +
                                         "<cac:AdditionalDocumentReference><cbc:ID>AMBIGUOUS</cbc:ID></cac:AdditionalDocumentReference>");
    for (final ECreditNoteIDOnlyReferenceHandling eHandling : ECreditNoteIDOnlyReferenceHandling.values ())
    {
      final ErrorList aErrorList = new ErrorList ();
      final Document aOut = _update (sXML, eHandling, aErrorList);
      // The described one is a supporting document in every mode
      assertEquals ("916", string (aOut, REFS + "[cbc:ID='DOC-1']/cbc:DocumentTypeCode"));
      // BT-18 and the typed one are never touched
      assertEquals ("ATS", string (aOut, REFS + "[cbc:ID='OBJ-1']/cbc:DocumentType"));
      assertEquals (0, count (aOut, REFS + "[cbc:ID='OBJ-1']/cbc:DocumentTypeCode"));
      assertEquals ("130", string (aOut, REFS + "[cbc:ID='TYPED-1']/cbc:DocumentTypeCode"));
    }
  }

  @Test
  public void testReferenceWithMoreThanAnIDIsNotMovedToProject () throws Exception
  {
    // Not ID-only (it has an issue date), but no type, description or attachment either
    final String sXML = _withReferences ("<cac:AdditionalDocumentReference><cbc:ID>PRJ-1</cbc:ID><cbc:IssueDate>2026-01-01</cbc:IssueDate></cac:AdditionalDocumentReference>");
    final ErrorList aErrorList = new ErrorList ();
    final Document aOut = _update (sXML, ECreditNoteIDOnlyReferenceHandling.PROJECT, aErrorList);
    assertEquals (1, count (aOut, REFS));
    assertEquals (0, count (aOut, "/cn:CreditNote/cac:ProjectReference"));
    assertEquals (1, countRule (aErrorList, EUpdateRule.R5));
    assertEquals (0, countApplied (aErrorList, EUpdateRule.R5));
  }

  @Test
  public void testStrictAndOverrides () throws Exception
  {
    final File aFile = new File (BASE);
    final ErrorList aErrorList = new ErrorList ();
    final Serializable aResult = new UBL21CreditNoteToUBL25CreditNoteUpdater ().setStrict (true)
                                                                                .setCustomizationID ("urn:example:custom")
                                                                                .setProfileID ("urn:example:profile")
                                                                                .update (aFile, aErrorList);
    assertNotNull (aResult);
    assertEquals (_expectedC1 (read (aFile)), aErrorList.getAllErrors ().size ());
    final Document aOut = write (aResult);
    assertEquals ("urn:example:custom", string (aOut, "/cn:CreditNote/cbc:CustomizationID"));
    assertEquals ("urn:example:profile", string (aOut, "/cn:CreditNote/cbc:ProfileID"));
  }
}
