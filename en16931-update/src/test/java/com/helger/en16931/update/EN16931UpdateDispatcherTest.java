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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;
import org.w3c.dom.Document;

import com.helger.base.state.ESuccess;
import com.helger.diagnostics.error.IError;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931Edition;
import com.helger.en16931.basics.EEN16931SyntaxKind;
import com.helger.xml.serialize.read.DOMReader;

import oasis.names.specification.ubl.schema.xsd.creditnote_25.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_25.InvoiceType;
import un.unece.uncefact.data.standard.cii.d25a.CrossIndustryInvoiceType;

/**
 * Test class for class {@link EN16931UpdateDispatcher}. The update rules themselves are tested
 * with the updaters.
 *
 * @author Philip Helger
 */
public final class EN16931UpdateDispatcherTest
{
  private static final String CII_2017 = "src/test/resources/external/cii/CII_example1.xml";
  private static final String CII_LEGACY_ZUGFERD = "src/test/resources/external/cii/CII_business_example_01.xml";
  private static final String UBL_INVOICE_2017 = "src/test/resources/external/ubl21/inv/peppol/base-example.xml";
  private static final String UBL_CREDIT_NOTE_2017 = "src/test/resources/external/ubl21/cn/peppol/base-creditnote-correction.xml";

  private static final String ID_2017 = "urn:cen.eu:en16931:2017";
  private static final String ID_2026 = "urn:cen.eu:en16931:2026";

  private static Document _as2026 (final String sFilename) throws IOException
  {
    final String sXML = Files.readString (new File (sFilename).toPath (), StandardCharsets.UTF_8);
    assertTrue (sXML.contains (ID_2017));
    final Document ret = DOMReader.readXMLDOM (sXML.replace (ID_2017, ID_2026));
    assertNotNull (ret);
    return ret;
  }

  private static void _assertNoError (final ErrorList aErrorList)
  {
    assertFalse (aErrorList.getAllErrors ().toString (), aErrorList.containsAtLeastOneError ());
  }

  @Test
  public void testRouteAndWrite ()
  {
    final Object [][] aCases = { { CII_2017, CrossIndustryInvoiceType.class },
                                 { UBL_INVOICE_2017, InvoiceType.class },
                                 { UBL_CREDIT_NOTE_2017, CreditNoteType.class } };
    for (final Object [] aCase : aCases)
    {
      final ErrorList aErrorList = new ErrorList ();
      final UpdateResult aResult = new EN16931UpdateDispatcher ().update (new File ((String) aCase[0]), aErrorList);
      _assertNoError (aErrorList);
      assertEquals (EUpdateStatus.UPDATED, aResult.getStatus ());
      assertTrue (aResult.hasDocument ());
      assertSame ((Class <?>) aCase[1], aResult.getDocument ().getClass ());

      // Writing validates against the 2026 schema
      final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
      assertTrue (EN16931UpdateDispatcher.writeDocument (aResult.getDocument (), aBAOS, aErrorList).isSuccess ());
      _assertNoError (aErrorList);
      assertTrue (aBAOS.size () > 0);
    }
  }

  @Test
  public void testAlready2026Skip () throws IOException
  {
    // SKIP is the default
    assertEquals (EAlready2026Handling.SKIP, new EN16931UpdateDispatcher ().getAlready2026Handling ());

    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = new EN16931UpdateDispatcher ().update (_as2026 (UBL_INVOICE_2017), aErrorList);
    assertEquals (EUpdateStatus.SKIPPED, aResult.getStatus ());
    assertFalse (aResult.hasDocument ());
    assertNull (aResult.getDocument ());
    // A warning, but no error
    _assertNoError (aErrorList);
    assertEquals (1, aErrorList.size ());
  }

  @Test
  public void testAlready2026Copy () throws IOException
  {
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = new EN16931UpdateDispatcher ().setAlready2026Handling (EAlready2026Handling.COPY)
                                                               .update (_as2026 (CII_2017), aErrorList);
    _assertNoError (aErrorList);
    assertEquals (EUpdateStatus.COPIED, aResult.getStatus ());
    assertTrue (aResult.getDocument () instanceof CrossIndustryInvoiceType);
  }

  @Test
  public void testAlready2026Fail () throws IOException
  {
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = new EN16931UpdateDispatcher ().setAlready2026Handling (EAlready2026Handling.FAIL)
                                                               .update (_as2026 (UBL_CREDIT_NOTE_2017), aErrorList);
    assertTrue (aErrorList.containsAtLeastOneError ());
    assertEquals (EUpdateStatus.FAILED, aResult.getStatus ());
    assertNull (aResult.getDocument ());
  }

  @Test
  public void testMissingBT24 ()
  {
    // The legacy ZUGFeRD identifier is not an EN 16931 edition
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = new EN16931UpdateDispatcher ().update (new File (CII_LEGACY_ZUGFERD), aErrorList);
    assertEquals (EUpdateStatus.FAILED, aResult.getStatus ());
    assertTrue (aErrorList.containsAtLeastOneError ());
    final IError aError = aErrorList.getAllErrors ().getFirstOrNull ();
    assertNotNull (aError);
    assertTrue (aError.getErrorText (java.util.Locale.ROOT).contains ("Set the source edition explicitly"));
  }

  @Test
  public void testForcedSourceEdition ()
  {
    // ... but works, if the source edition is given
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = new EN16931UpdateDispatcher ().setSourceEdition (EEN16931Edition.EN2017)
                                                               .update (new File (CII_LEGACY_ZUGFERD), aErrorList);
    _assertNoError (aErrorList);
    assertEquals (EUpdateStatus.UPDATED, aResult.getStatus ());
  }

  @Test
  public void testUnsupportedRootElement ()
  {
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = new EN16931UpdateDispatcher ().update (DOMReader.readXMLDOM ("<foo/>"), aErrorList);
    assertEquals (EUpdateStatus.FAILED, aResult.getStatus ());
    assertTrue (aErrorList.containsAtLeastOneError ());
  }

  @Test
  public void testFileProblems ()
  {
    final ErrorList aErrorList = new ErrorList ();
    assertEquals (EUpdateStatus.FAILED,
                  new EN16931UpdateDispatcher ().update (new File ("src/test/resources/does-not-exist.xml"),
                                                         aErrorList)
                                                .getStatus ());
    assertTrue (aErrorList.containsAtLeastOneError ());
  }

  @Test
  public void testInvalidAgainstSchemaIsRejected () throws IOException
  {
    // Reading is strict: an unknown element makes the document invalid
    final String sXML = Files.readString (new File (UBL_INVOICE_2017).toPath (), StandardCharsets.UTF_8)
                             .replace ("<cbc:ID>", "<cbc:Bogus>x</cbc:Bogus><cbc:ID>");
    final ErrorList aErrorList = new ErrorList ();
    final UpdateResult aResult = new EN16931UpdateDispatcher ().update (DOMReader.readXMLDOM (sXML), aErrorList);
    assertEquals (EUpdateStatus.FAILED, aResult.getStatus ());
    assertTrue (aErrorList.containsAtLeastOneError ());
  }

  @Test
  public void testSettingsAreHandedOver ()
  {
    final EN16931UpdateDispatcher aDispatcher = new EN16931UpdateDispatcher ().setStrict (true)
                                                                              .setCustomizationID ("abc")
                                                                              .setProfileID ("def");
    for (final EEN16931SyntaxKind eSyntaxKind : EEN16931SyntaxKind.values ())
    {
      final AbstractEN16931Updater <?> aUpdater = aDispatcher.createUpdater (eSyntaxKind);
      assertEquals (eSyntaxKind, aUpdater.getSyntaxKind ());
      assertTrue (aUpdater.isStrict ());
      assertEquals ("abc", aUpdater.getCustomizationID ());
      assertEquals ("def", aUpdater.getProfileID ());
    }
    assertFalse (new EN16931UpdateDispatcher ().createUpdater (EEN16931SyntaxKind.CII).isStrict ());
  }

  @Test
  public void testWriteUnsupportedObject ()
  {
    final ErrorList aErrorList = new ErrorList ();
    final Serializable aObj = "not a document";
    assertEquals (ESuccess.FAILURE,
                  EN16931UpdateDispatcher.writeDocument (aObj, new ByteArrayOutputStream (), aErrorList));
    assertTrue (aErrorList.containsAtLeastOneError ());
  }
}
