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

import static com.helger.en16931.update.UpdateTestHelper.count;
import static com.helger.en16931.update.UpdateTestHelper.listXML;
import static com.helger.en16931.update.UpdateTestHelper.parse;
import static com.helger.en16931.update.UpdateTestHelper.read;
import static com.helger.en16931.update.UpdateTestHelper.string;
import static com.helger.en16931.update.UpdateTestHelper.writeToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.w3c.dom.Document;

import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931Edition;
import com.helger.en16931.basics.EEN16931SyntaxKind;

/**
 * Hardening tests (plan item A8): XML attacks, idempotence, preservation of extension content and
 * attachments, and a large document.
 *
 * @author Philip Helger
 */
public final class HardeningTest
{
  private static final String BASE_UBL = "src/test/resources/external/ubl21/inv/peppol/base-example.xml";
  private static final String BASE_CN = "src/test/resources/external/ubl21/cn/peppol/base-creditnote-correction.xml";
  private static final String UBL_NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
  private static final String UBL_NS_CN = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
  private static final String UBL_NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
  private static final String EXT_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";

  private static UpdateResult _update (final File aFile, final ErrorList aErrorList)
  {
    return new EN16931UpdateDispatcher ().setSourceEdition (EEN16931Edition.EN2017).update (aFile, aErrorList);
  }

  private static File _tempFile (final String sContent) throws IOException
  {
    final File ret = File.createTempFile ("en16931-update-test", ".xml");
    ret.deleteOnExit ();
    Files.writeString (ret.toPath (), sContent, StandardCharsets.UTF_8);
    return ret;
  }

  private static String _readString (final String sFilename) throws IOException
  {
    return Files.readString (new File (sFilename).toPath (), StandardCharsets.UTF_8);
  }

  // ---- XML attacks

  private static void _assertRejected (final String sRootStart,
                                       final String sRootEnd,
                                       final String sDocType,
                                       final String sSecret) throws IOException
  {
    final String sPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            sDocType +
                            "\n" +
                            sRootStart +
                            "<cbc:ID xmlns:cbc=\"" +
                            UBL_NS_CBC +
                            "\">&xxe;</cbc:ID>" +
                            sRootEnd;
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = _update (_tempFile (sPayload), aErrorList);
    assertEquals (EUpdateStatus.FAILED, aResult.getStatus ());
    assertTrue (aErrorList.containsAtLeastOneError ());
    // Whatever happened, the secret must not show up anywhere
    assertFalse (aErrorList.getAllFailures ().toString ().contains (sSecret));
  }

  @Test
  public void testExternalEntityIsRejected () throws IOException
  {
    final File aSecret = _tempFile ("TOP-SECRET-CONTENT");
    final String sDocType = "<!DOCTYPE foo [\n  <!ENTITY xxe SYSTEM \"" + aSecret.toURI () + "\">\n]>";
    _assertRejected ("<Invoice xmlns=\"" + UBL_NS_INVOICE + "\">", "</Invoice>", sDocType, "TOP-SECRET-CONTENT");
    _assertRejected ("<CreditNote xmlns=\"" + UBL_NS_CN + "\">", "</CreditNote>", sDocType, "TOP-SECRET-CONTENT");
    _assertRejected ("<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\">",
                     "</rsm:CrossIndustryInvoice>",
                     sDocType,
                     "TOP-SECRET-CONTENT");
  }

  @Test
  public void testAnyDoctypeIsRejected () throws IOException
  {
    // Control: a harmless internal entity that is defined. It would expand fine if DTDs were
    // allowed,
    // so this proves that the rejection comes from the DOCTYPE and not from an undefined entity.
    final String sPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE foo [\n  <!ENTITY xxe \"harmless\">\n]>\n" +
                            "<Invoice xmlns=\"" +
                            UBL_NS_INVOICE +
                            "\"><cbc:ID xmlns:cbc=\"" +
                            UBL_NS_CBC +
                            "\">&xxe;</cbc:ID></Invoice>";
    final ErrorList aErrorList = new ErrorList ();
    assertEquals (EUpdateStatus.FAILED, _update (_tempFile (sPayload), aErrorList).getStatus ());
    assertTrue (aErrorList.containsAtLeastOneError ());
  }

  @Test
  public void testExternalParameterEntityIsRejected () throws IOException
  {
    // Nothing listens on port 1 - a rejected document never tries to connect
    final String sDocType = "<!DOCTYPE foo [\n  <!ENTITY % xxe SYSTEM \"http://127.0.0.1:1/xxe.dtd\">\n  %xxe;\n]>";
    _assertRejected ("<Invoice xmlns=\"" + UBL_NS_INVOICE + "\">", "</Invoice>", sDocType, "does-not-matter");
  }

  @Test (timeout = 30000)
  public void testBillionLaughsIsRejected () throws IOException
  {
    final String sDocType = "<!DOCTYPE lolz [\n" +
                            "  <!ENTITY lol \"lol\">\n" +
                            "  <!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">\n" +
                            "  <!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">\n" +
                            "  <!ENTITY xxe \"&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;\">\n" +
                            "]>";
    _assertRejected ("<Invoice xmlns=\"" + UBL_NS_INVOICE + "\">", "</Invoice>", sDocType, "does-not-matter");
  }

  // ---- Idempotence

  private static AbstractEN16931Updater <?> _updater (final File aFile)
  {
    final EEN16931SyntaxKind eKind = EEN16931SyntaxKind.getFromNodeOrNull (read (aFile));
    assertNotNull (aFile.getPath (), eKind);
    return new EN16931UpdateDispatcher ().createUpdater (eKind);
  }

  @Test
  public void testUpdatingTwiceChangesNothing () throws Exception
  {
    final List <File> aFiles = new ArrayList <> ();
    aFiles.addAll (listXML ("external/cii"));
    aFiles.addAll (listXML ("external/ubl21"));
    aFiles.addAll (listXML ("edge/ubl21"));
    assertEquals (121, aFiles.size ());

    for (final File aFile : aFiles)
    {
      final String sName = aFile.getPath ();
      final Serializable aFirst = _updater (aFile).update (aFile, new ErrorList ());
      final String sFirst = writeToString (aFirst);

      // The updater itself - without the dispatcher that would skip the document
      final ErrorList aErrorList = new ErrorList ();
      final Serializable aSecond = _updater (aFile).update (parse (sFirst), aErrorList);
      assertFalse (sName + ": " + aErrorList.getAllErrors (), aErrorList.containsAtLeastOneError ());
      assertEquals (sName, sFirst, writeToString (aSecond));
    }
  }

  @Test
  public void testDispatcherSkipsTheResult () throws Exception
  {
    final File aFile = new File (BASE_UBL);
    final UpdateResult aFirst = _update (aFile, new ErrorList ());
    final File aOutput = _tempFile (writeToString (aFirst.getDocument ()));

    final ErrorList aErrorList = new ErrorList ();
    assertEquals (EUpdateStatus.SKIPPED, new EN16931UpdateDispatcher ().update (aOutput, aErrorList).getStatus ());
    assertFalse (aErrorList.containsAtLeastOneError ());
  }

  // ---- Extension content and attachments

  private static final String EXTENSIONS = "<ext:UBLExtensions xmlns:ext=\"" +
                                           EXT_NS +
                                           "\"><ext:UBLExtension><cbc:ID xmlns:cbc=\"" +
                                           UBL_NS_CBC +
                                           "\">ext-1</cbc:ID><ext:ExtensionContent>" +
                                           "<foo:Bar xmlns:foo=\"urn:example:foreign\" kind=\"test\"><foo:Child>a &amp; b</foo:Child><foo:Child>second</foo:Child></foo:Bar>" +
                                           "</ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>";

  private static void _assertExtensionsPreserved (final Document aOut, final String sRoot) throws Exception
  {
    final String sExt = "/" + sRoot + "/ext:UBLExtensions/ext:UBLExtension";
    assertEquals (1, count (aOut, sExt));
    assertEquals ("ext-1", string (aOut, sExt + "/cbc:ID"));
    assertEquals ("test", string (aOut, sExt + "/ext:ExtensionContent/foo:Bar/@kind"));
    assertEquals (2, count (aOut, sExt + "/ext:ExtensionContent/foo:Bar/foo:Child"));
    assertEquals ("a & b", string (aOut, sExt + "/ext:ExtensionContent/foo:Bar/foo:Child[1]"));
    assertEquals ("second", string (aOut, sExt + "/ext:ExtensionContent/foo:Bar/foo:Child[2]"));
  }

  @Test
  public void testUBLExtensionsArePreserved () throws Exception
  {
    // UBLExtensions is the first child of the document element
    for (final boolean bInvoice : new boolean [] { true, false })
    {
      final String sBase = _readString (bInvoice ? BASE_UBL : BASE_CN);
      final String sTag = bInvoice ? "<Invoice " : "<CreditNote ";
      final int nStart = sBase.indexOf (sTag);
      final int nEnd = sBase.indexOf ('>', nStart);
      final String sXML = sBase.substring (0, nEnd + 1) + EXTENSIONS + sBase.substring (nEnd + 1);

      final ErrorList aErrorList = new ErrorList ();
      final UpdateResult aResult = _update (_tempFile (sXML), aErrorList);
      assertFalse (aErrorList.getAllErrors ().toString (), aErrorList.containsAtLeastOneError ());
      assertEquals (EUpdateStatus.UPDATED, aResult.getStatus ());
      _assertExtensionsPreserved (parse (writeToString (aResult.getDocument ())),
                                  bInvoice ? "inv:Invoice" : "cn:CreditNote");
    }
  }

  private static List <byte []> _decodeAll (final Document aDoc, final String sXPath) throws Exception
  {
    final List <byte []> ret = new ArrayList <> ();
    // XPath returns a count only, so walk by index
    final long nCount = count (aDoc, sXPath);
    for (int i = 1; i <= nCount; i++)
      ret.add (Base64.getMimeDecoder ().decode (string (aDoc, "(" + sXPath + ")[" + i + "]")));
    return ret;
  }

  @Test
  public void testAttachmentsAreByteIdentical () throws Exception
  {
    long nAttachments = 0;
    final String sCII = "//ram:AdditionalReferencedDocument/ram:AttachmentBinaryObject";
    for (final File aFile : listXML ("external/cii"))
    {
      final Document aIn = read (aFile);
      if (count (aIn, sCII) == 0)
        continue;
      final String sOut = writeToString (_updater (aFile).update (aFile, new ErrorList ()));
      final List <byte []> aInBytes = _decodeAll (aIn, sCII);
      final List <byte []> aOutBytes = _decodeAll (parse (sOut), sCII);
      assertEquals (aFile.getPath (), aInBytes.size (), aOutBytes.size ());
      for (int i = 0; i < aInBytes.size (); i++)
        assertTrue (aFile.getPath (), java.util.Arrays.equals (aInBytes.get (i), aOutBytes.get (i)));
      nAttachments += aInBytes.size ();
    }
    assertTrue (nAttachments > 20);

    final File aUBL = new File ("src/test/resources/external/ubl21/inv/coverage/en16931-coverage-invoice.xml");
    final String sUBL = "//cac:Attachment/cbc:EmbeddedDocumentBinaryObject";
    final List <byte []> aInBytes = _decodeAll (read (aUBL), sUBL);
    assertTrue (aInBytes.size () > 0);
    final List <byte []> aOutBytes = _decodeAll (parse (writeToString (_updater (aUBL).update (aUBL,
                                                                                               new ErrorList ()))),
                                                 sUBL);
    assertEquals (aInBytes.size (), aOutBytes.size ());
    for (int i = 0; i < aInBytes.size (); i++)
      assertTrue (java.util.Arrays.equals (aInBytes.get (i), aOutBytes.get (i)));
  }

  @Test
  public void testNoListIDOrSchemeAttributeAppears () throws Exception
  {
    // The D25A schema declares default values for @listID and @listAgencyID of some code types.
    // JAXB reports them through the getters, but they must never be written if the input had none.
    for (final File aFile : listXML ("external/cii"))
    {
      final Document aIn = read (aFile);
      final Document aOut = parse (writeToString (_updater (aFile).update (aFile, new ErrorList ())));
      for (final String sAttr : new String [] { "listID",
                                                "listAgencyID",
                                                "listVersionID",
                                                "schemeID",
                                                "schemeAgencyID",
                                                "unitCode",
                                                "currencyID",
                                                "format" })
        assertEquals (aFile.getPath () + " @" + sAttr, count (aIn, "//@" + sAttr), count (aOut, "//@" + sAttr));
    }
  }

  // ---- Large document

  @Test (timeout = 120000)
  public void testLargeInvoice () throws Exception
  {
    final String sBase = _readString (BASE_UBL);
    final Matcher aMatcher = Pattern.compile ("<cac:InvoiceLine>.*?</cac:InvoiceLine>", Pattern.DOTALL).matcher (sBase);
    assertTrue (aMatcher.find ());
    final String sLine = aMatcher.group ();
    final int nOriginal = (int) count (parse (sBase), "/inv:Invoice/cac:InvoiceLine");
    final int nLines = 4000;
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 1; i <= nLines; i++)
      aSB.append (sLine.replaceFirst ("<cbc:ID>[^<]*</cbc:ID>", "<cbc:ID>L" + i + "</cbc:ID>"));
    final String sXML = sBase.replace (sLine, aSB.toString ());

    final File aFile = _tempFile (sXML);
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = _update (aFile, aErrorList);
    assertFalse (aErrorList.getAllErrors ().toString (), aErrorList.containsAtLeastOneError ());
    assertEquals (EUpdateStatus.UPDATED, aResult.getStatus ());
    final Document aOut = parse (writeToString (aResult.getDocument ()));
    // The first line was replaced by the generated ones, the others stay
    assertEquals (nLines + nOriginal - 1, count (aOut, "/inv:Invoice/cac:InvoiceLine"));
    assertEquals ("L" + nLines, string (aOut, "/inv:Invoice/cac:InvoiceLine[" + nLines + "]/cbc:ID"));
  }
}
