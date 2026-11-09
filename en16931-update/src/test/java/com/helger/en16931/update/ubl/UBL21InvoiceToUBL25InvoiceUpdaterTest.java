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
import java.util.regex.Pattern;

import org.junit.Test;
import org.w3c.dom.Document;

import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.update.EUpdateRule;

/**
 * Test class for class {@link UBL21InvoiceToUBL25InvoiceUpdater}. The expectations are computed
 * with XPath directly on the input document, independent of the JAXB code of the updater.
 *
 * @author Philip Helger
 */
public final class UBL21InvoiceToUBL25InvoiceUpdaterTest
{
  private static final String ID_2017 = "urn:cen.eu:en16931:2017";
  private static final String ID_2026 = "urn:cen.eu:en16931:2026";
  private static final String BASE = "src/test/resources/external/ubl21/inv/peppol/base-example.xml";
  private static final Pattern NOTE_WITH_CODE = Pattern.compile ("^#[^#\\r\\n]+#");
  private static final String NO_SCHEME = "[not(@schemeID) or @schemeID='']";

  private static List <File> _invoices () throws Exception
  {
    final List <File> ret = new ArrayList <> ();
    for (final File aFile : listXML ("external/ubl21"))
      ret.add (aFile);
    for (final File aFile : listXML ("edge/ubl21"))
      ret.add (aFile);
    ret.removeIf (x -> {
      try
      {
        return !read (x).getDocumentElement ().getLocalName ().equals ("Invoice");
      }
      catch (final RuntimeException ex)
      {
        return true;
      }
    });
    return ret;
  }

  private static String _readString (final String sFilename) throws Exception
  {
    return Files.readString (new File (sFilename).toPath (), StandardCharsets.UTF_8);
  }

  private static long _expectedC1 (final Document aDoc) throws Exception
  {
    long ret = 0;
    for (final String sParty : new String [] { "/inv:Invoice/cac:AccountingSupplierParty/cac:Party",
                                               "/inv:Invoice/cac:AccountingCustomerParty/cac:Party",
                                               "/inv:Invoice/cac:PayeeParty" })
    {
      ret += count (aDoc, sParty + "/cac:PartyIdentification/cbc:ID" + NO_SCHEME);
      ret += count (aDoc, sParty + "/cac:PartyLegalEntity/cbc:CompanyID" + NO_SCHEME);
    }
    return ret + count (aDoc, "/inv:Invoice/cac:Delivery/cac:DeliveryLocation/cbc:ID" + NO_SCHEME);
  }

  @Test
  public void testWholeCorpus () throws Exception
  {
    int nInvoices = 0;
    long nNotes = 0;
    long nBuyerRefs = 0;
    long nSupportingDocs = 0;
    long nC1 = 0;
    for (final File aFile : _invoices ())
    {
      final String sName = aFile.getPath ();
      final Document aIn = read (aFile);
      final ErrorList aErrorList = new ErrorList ();
      final Serializable aResult = new UBL21InvoiceToUBL25InvoiceUpdater ().update (aIn, aErrorList);
      assertFalse (sName + ": " + aErrorList.getAllErrors (), aErrorList.containsAtLeastOneError ());
      final Document aOut = write (aResult);
      nInvoices++;

      // R1
      final String sOld = string (aIn, "/inv:Invoice/cbc:CustomizationID");
      assertTrue (sName, sOld.startsWith (ID_2017));
      assertEquals (sName, ID_2026 + sOld.substring (ID_2017.length ()), string (aOut, "/inv:Invoice/cbc:CustomizationID"));

      // R2
      final long nBT10 = count (aIn, "/inv:Invoice/cbc:BuyerReference");
      assertEquals (sName, 0, count (aOut, "/inv:Invoice/cbc:BuyerReference"));
      assertEquals (sName, nBT10, count (aOut, "/inv:Invoice/cac:BuyerAssignedReference/cbc:BuyerReference"));
      assertEquals (sName, string (aIn, "/inv:Invoice/cbc:BuyerReference"), string (aOut, "/inv:Invoice/cac:BuyerAssignedReference/cbc:BuyerReference"));
      assertEquals (sName, nBT10, countApplied (aErrorList, EUpdateRule.R2));
      nBuyerRefs += nBT10;

      // R3 - the header notes only, not the notes of the payment terms and of the lines
      final long nNotesIn = count (aIn, "/inv:Invoice/cbc:Note");
      assertEquals (sName, 0, count (aOut, "/inv:Invoice/cbc:Note"));
      assertEquals (sName, nNotesIn, count (aOut, "/inv:Invoice/cac:Annotation"));
      assertEquals (sName, nNotesIn, countApplied (aErrorList, EUpdateRule.R3));
      assertEquals (sName,
                    count (aIn, "/inv:Invoice/cac:PaymentTerms/cbc:Note"),
                    count (aOut, "/inv:Invoice/cac:PaymentTerms/cbc:Note"));
      assertEquals (sName,
                    count (aIn, "/inv:Invoice/cac:InvoiceLine/cbc:Note"),
                    count (aOut, "/inv:Invoice/cac:InvoiceLine/cbc:Note"));
      nNotes += nNotesIn;

      // R6 - nothing but VAT / LOC is left on the Seller
      assertEquals (sName,
                    0,
                    count (aOut,
                           "/inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme/cac:TaxScheme/cbc:ID[.!='VAT' and .!='LOC']"));

      // R7 - a reference without type is a supporting document
      final long nNoType = count (aIn,
                                  "/inv:Invoice/cac:AdditionalDocumentReference[not(cbc:DocumentTypeCode) and not(cbc:DocumentType)]");
      assertEquals (sName,
                    nNoType,
                    count (aOut,
                           "/inv:Invoice/cac:AdditionalDocumentReference/cbc:DocumentTypeCode[.='916' and @listID='1001']"));
      assertEquals (sName, nNoType, countApplied (aErrorList, EUpdateRule.R7));
      nSupportingDocs += nNoType;

      // C1 and C2 - warnings, counted independently
      assertEquals (sName, _expectedC1 (aIn), countRule (aErrorList, EUpdateRule.C1));
      nC1 += _expectedC1 (aIn);
      assertEquals (sName,
                    count (aIn,
                           "/inv:Invoice/cac:AdditionalDocumentReference/cac:Attachment/cbc:EmbeddedDocumentBinaryObject" + "[not(@filename) or @filename='']"),
                    countRule (aErrorList, EUpdateRule.C2));
    }
    // Guards against a corpus that silently shrank, and against rules that are never exercised
    assertEquals (15, nInvoices);
    assertTrue (nNotes > 0);
    assertTrue (nBuyerRefs > 5);
    assertTrue (nSupportingDocs > 0);
    assertTrue (nC1 > 5);
  }

  /** The corpus has no BT-21 code, no BT-32 other than VAT, no version identifier - so build them */
  @Test
  public void testHandBuiltCases () throws Exception
  {
    String sXML = _readString (BASE);
    // Sequence of UBL: UBLVersionID, CustomizationID ... InvoiceTypeCode, Note ... AccountingSupplierParty
    sXML = sXML.replace ("<cbc:CustomizationID>", "<cbc:UBLVersionID>2.1</cbc:UBLVersionID><cbc:CustomizationID>");
    sXML = sXML.replaceFirst ("(</cbc:InvoiceTypeCode>)",
                              "$1<cbc:Note>#AAI#Hello world</cbc:Note>" +
                                                          "<cbc:Note>plain text</cbc:Note>" +
                                                          "<cbc:Note>##no code</cbc:Note>" +
                                                          "<cbc:Note>#ABC#line 1\nline 2</cbc:Note>" +
                                                          "<cbc:Note>#only code and no closing</cbc:Note>");
    sXML = sXML.replace ("<cac:AccountingSupplierParty>",
                         "<cac:AdditionalDocumentReference><cbc:ID>REF-1</cbc:ID></cac:AdditionalDocumentReference>" +
                                                           "<cac:AdditionalDocumentReference><cbc:ID>REF-2</cbc:ID><cbc:DocumentTypeCode>130</cbc:DocumentTypeCode></cac:AdditionalDocumentReference>" +
                                                           "<cac:AccountingSupplierParty>");
    // A second tax scheme of the Seller with another code than VAT (BT-32), before the legal entity
    sXML = sXML.replaceFirst ("<cac:PartyLegalEntity>",
                              "<cac:PartyTaxScheme><cbc:CompanyID>TAX-123</cbc:CompanyID><cac:TaxScheme><cbc:ID>GST</cbc:ID></cac:TaxScheme></cac:PartyTaxScheme><cac:PartyLegalEntity>");
    final Document aIn = parse (sXML);
    assertEquals (5, count (aIn, "/inv:Invoice/cbc:Note"));

    final ErrorList aErrorList = new ErrorList ();
    final Serializable aResult = new UBL21InvoiceToUBL25InvoiceUpdater ().update (aIn, aErrorList);
    assertFalse (aErrorList.getAllErrors ().toString (), aErrorList.containsAtLeastOneError ());
    final Document aOut = write (aResult);

    // R9
    assertEquals ("2.5", string (aOut, "/inv:Invoice/cbc:UBLVersionID"));
    assertEquals (1, countApplied (aErrorList, EUpdateRule.R9));

    // R3
    final String sNotes = "/inv:Invoice/cac:Annotation";
    assertEquals (0, count (aOut, "/inv:Invoice/cbc:Note"));
    assertEquals ("AAI", string (aOut, sNotes + "[1]/cbc:SubjectCode"));
    assertEquals ("Hello world", string (aOut, sNotes + "[1]/cbc:AnnotationContent"));
    assertEquals (0, count (aOut, sNotes + "[2]/cbc:SubjectCode"));
    assertEquals ("plain text", string (aOut, sNotes + "[2]/cbc:AnnotationContent"));
    // An empty code is no code: the text is kept as it is
    assertEquals (0, count (aOut, sNotes + "[3]/cbc:SubjectCode"));
    assertEquals ("##no code", string (aOut, sNotes + "[3]/cbc:AnnotationContent"));
    assertEquals ("ABC", string (aOut, sNotes + "[4]/cbc:SubjectCode"));
    assertEquals ("line 1\nline 2", string (aOut, sNotes + "[4]/cbc:AnnotationContent"));
    // No closing #
    assertEquals (0, count (aOut, sNotes + "[5]/cbc:SubjectCode"));
    assertEquals ("#only code and no closing", string (aOut, sNotes + "[5]/cbc:AnnotationContent"));

    // R6
    final String sTS = "/inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme";
    assertEquals (1, count (aOut, sTS + "/cac:TaxScheme/cbc:ID[.='LOC']"));
    assertEquals ("TAX-123", string (aOut, sTS + "[cac:TaxScheme/cbc:ID='LOC']/cbc:CompanyID"));
    assertEquals (1, countApplied (aErrorList, EUpdateRule.R6));
    // The VAT one is untouched
    assertTrue (count (aOut, sTS + "/cac:TaxScheme/cbc:ID[.='VAT']") >= 1);

    // R7 - only the reference without type
    final String sRef = "/inv:Invoice/cac:AdditionalDocumentReference";
    assertEquals ("916", string (aOut, sRef + "[cbc:ID='REF-1']/cbc:DocumentTypeCode"));
    assertEquals ("1001", string (aOut, sRef + "[cbc:ID='REF-1']/cbc:DocumentTypeCode/@listID"));
    assertEquals ("130", string (aOut, sRef + "[cbc:ID='REF-2']/cbc:DocumentTypeCode"));
    assertEquals (0, count (aOut, sRef + "[cbc:ID='REF-2']/cbc:DocumentTypeCode/@listID"));
    assertEquals (1, countApplied (aErrorList, EUpdateRule.R7));

    // The pattern the test itself relies on
    assertTrue (NOTE_WITH_CODE.matcher ("#AAI#x").find ());
  }

  @Test
  public void testPayeeAndDeliveryIdentifiers () throws Exception
  {
    // No corpus invoice has a Payee or a delivery location without a scheme identifier
    final String sBase = _readString (BASE);
    final String sXML = sBase.replace ("</cac:AccountingCustomerParty>",
                                       "</cac:AccountingCustomerParty><cac:PayeeParty><cac:PartyIdentification><cbc:ID>PAYEE-1</cbc:ID></cac:PartyIdentification>" +
                                                                       "<cac:PartyName><cbc:Name>Payee</cbc:Name></cac:PartyName>" +
                                                                       "<cac:PartyLegalEntity><cbc:RegistrationName>Payee</cbc:RegistrationName><cbc:CompanyID>PAYEE-LEGAL</cbc:CompanyID></cac:PartyLegalEntity></cac:PayeeParty>")
                          .replaceFirst ("<cac:PaymentMeans>",
                                         "<cac:Delivery><cac:DeliveryLocation><cbc:ID>LOC-1</cbc:ID></cac:DeliveryLocation></cac:Delivery><cac:PaymentMeans>");
    final Document aIn = parse (sXML);
    final long nExpected = _expectedC1 (aIn) - _expectedC1 (read (new File (BASE)));
    assertEquals (3, nExpected);

    final ErrorList aErrorList = new ErrorList ();
    assertNotNull (new UBL21InvoiceToUBL25InvoiceUpdater ().update (aIn, aErrorList));
    assertEquals (_expectedC1 (aIn), countRule (aErrorList, EUpdateRule.C1));
    assertEquals (1, aErrorList.stream ().filter (x -> "BT-60-1".equals (x.getErrorText (java.util.Locale.ROOT).substring (0, 7))).count ());
    assertEquals (1, aErrorList.stream ().filter (x -> "BT-61-1".equals (x.getErrorText (java.util.Locale.ROOT).substring (0, 7))).count ());
    assertEquals (1, aErrorList.stream ().filter (x -> "BT-71-1".equals (x.getErrorText (java.util.Locale.ROOT).substring (0, 7))).count ());
  }

  @Test
  public void testVersionOtherThan21IsLeftAlone () throws Exception
  {
    final String sXML = _readString (BASE).replace ("<cbc:CustomizationID>", "<cbc:UBLVersionID>2.0</cbc:UBLVersionID><cbc:CustomizationID>");
    final ErrorList aErrorList = new ErrorList ();
    final Serializable aResult = new UBL21InvoiceToUBL25InvoiceUpdater ().update (parse (sXML), aErrorList);
    assertNotNull (aResult);
    assertEquals ("2.0", string (write (aResult), "/inv:Invoice/cbc:UBLVersionID"));
    assertEquals (0, countApplied (aErrorList, EUpdateRule.R9));
    assertEquals (1, countRule (aErrorList, EUpdateRule.R9));
  }

  @Test
  public void testStrictTurnsChecksIntoErrors () throws Exception
  {
    final File aFile = new File (BASE);
    assertTrue (_expectedC1 (read (aFile)) > 0);

    final ErrorList aErrorList = new ErrorList ();
    final Serializable aResult = new UBL21InvoiceToUBL25InvoiceUpdater ().setStrict (true).update (aFile, aErrorList);
    // The document is still produced and unchanged where the check failed
    assertNotNull (aResult);
    assertTrue (aErrorList.containsAtLeastOneError ());
    assertTrue (aErrorList.getAllErrors ().stream ().allMatch (x -> EUpdateRule.C1.getID ().equals (x.getErrorID ())));
    assertEquals (_expectedC1 (read (aFile)), aErrorList.getAllErrors ().size ());
  }

  @Test
  public void testC2MissingFilename () throws Exception
  {
    final File aFile = new File ("src/test/resources/external/ubl21/inv/coverage/en16931-coverage-invoice.xml");
    final String sXML = _readString (aFile.getPath ());
    final String sStripped = sXML.replaceAll ("\\s+filename=\"[^\"]*\"", "");
    assertTrue (sStripped.length () < sXML.length ());
    final Document aIn = parse (sStripped);
    final long nAttachments = count (aIn, "/inv:Invoice/cac:AdditionalDocumentReference/cac:Attachment/cbc:EmbeddedDocumentBinaryObject");
    assertTrue (nAttachments > 0);

    final ErrorList aErrorList = new ErrorList ();
    assertNotNull (new UBL21InvoiceToUBL25InvoiceUpdater ().update (aIn, aErrorList));
    assertEquals (nAttachments, countRule (aErrorList, EUpdateRule.C2));
    assertFalse (aErrorList.containsAtLeastOneError ());

    final ErrorList aStrict = new ErrorList ();
    new UBL21InvoiceToUBL25InvoiceUpdater ().setStrict (true).update (aIn, aStrict);
    assertEquals (nAttachments, aStrict.getAllErrors ().stream ().filter (x -> EUpdateRule.C2.getID ().equals (x.getErrorID ())).count ());
  }

  @Test
  public void testOverrides () throws Exception
  {
    final ErrorList aErrorList = new ErrorList ();
    final Serializable aResult = new UBL21InvoiceToUBL25InvoiceUpdater ().setCustomizationID ("urn:example:custom")
                                                                         .setProfileID ("urn:example:profile")
                                                                         .update (new File (BASE), aErrorList);
    assertFalse (aErrorList.containsAtLeastOneError ());
    final Document aOut = write (aResult);
    assertEquals ("urn:example:custom", string (aOut, "/inv:Invoice/cbc:CustomizationID"));
    assertEquals ("urn:example:profile", string (aOut, "/inv:Invoice/cbc:ProfileID"));
  }
}
