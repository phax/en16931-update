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
package com.helger.en16931.update.cii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.w3c.dom.Document;

import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931Edition;
import com.helger.en16931.update.EN16931UpdateDispatcher;
import com.helger.en16931.update.EUpdateRule;
import com.helger.en16931.update.EUpdateStatus;
import com.helger.en16931.update.UpdateResult;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Test class for class {@link CIID16BToCIID25AUpdater}. The expectations are computed with XPath
 * directly on the input document, independent of the JAXB code of the updater.
 *
 * @author Philip Helger
 */
public final class CIID16BToCIID25AUpdaterTest
{
  private static final String ID_2017 = "urn:cen.eu:en16931:2017";
  private static final String ID_2026 = "urn:cen.eu:en16931:2026";
  private static final String AGREEMENT = "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement";
  private static final String DELIVERY = "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeDelivery";
  private static final String SETTLEMENT = "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement";
  private static final String BT24 = "/rsm:CrossIndustryInvoice/rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID";

  private static final NamespaceContext NS = new NamespaceContext ()
  {
    public String getNamespaceURI (final String sPrefix)
    {
      return switch (sPrefix)
      {
        case "rsm" -> "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
        case "ram" -> "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
        default -> XMLConstants.NULL_NS_URI;
      };
    }

    public String getPrefix (final String sNamespaceURI)
    {
      return null;
    }

    public Iterator <String> getPrefixes (final String sNamespaceURI)
    {
      return null;
    }
  };

  private static List <File> _corpus () throws IOException
  {
    try (final Stream <Path> aStream = Files.walk (Path.of ("src/test/resources/external/cii")))
    {
      return aStream.filter (x -> x.toString ().endsWith (".xml")).map (Path::toFile).sorted ().collect (Collectors.toList ());
    }
  }

  private static XPath _xpath ()
  {
    final XPath ret = XPathFactory.newInstance ().newXPath ();
    ret.setNamespaceContext (NS);
    return ret;
  }

  private static double _count (final Document aDoc, final String sXPath) throws Exception
  {
    return ((Double) _xpath ().evaluate ("count(" + sXPath + ")", aDoc, XPathConstants.NUMBER)).doubleValue ();
  }

  private static String _string (final Document aDoc, final String sXPath) throws Exception
  {
    return (String) _xpath ().evaluate (sXPath, aDoc, XPathConstants.STRING);
  }

  private static Document _read (final File aFile)
  {
    final Document ret = DOMReader.readXMLDOM (aFile);
    assertNotNull (aFile.getPath (), ret);
    return ret;
  }

  /** @return The written and re-read result - writing validates against the D25A schema. */
  private static Document _write (final Serializable aResult) throws Exception
  {
    assertNotNull (aResult);
    final ErrorList aErrorList = new ErrorList ();
    final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
    assertTrue (aErrorList.getAllErrors ().toString (),
                EN16931UpdateDispatcher.writeDocument (aResult, aBAOS, aErrorList).isSuccess ());
    assertFalse (aErrorList.getAllErrors ().toString (), aErrorList.containsAtLeastOneError ());
    final Document ret = DOMReader.readXMLDOM (new ByteArrayInputStream (aBAOS.toByteArray ()));
    assertNotNull (ret);
    return ret;
  }

  private static long _countRule (final ErrorList aErrorList, final EUpdateRule eRule)
  {
    return aErrorList.stream ().filter (x -> eRule.getID ().equals (x.getErrorID ())).count ();
  }

  private static double _expectedC1 (final Document aDoc) throws Exception
  {
    double ret = 0;
    for (final String sParty : new String [] { AGREEMENT + "/ram:SellerTradeParty",
                                               AGREEMENT + "/ram:BuyerTradeParty",
                                               SETTLEMENT + "/ram:PayeeTradeParty",
                                               DELIVERY + "/ram:ShipToTradeParty" })
    {
      ret += _count (aDoc, sParty + "/ram:ID");
      ret += _count (aDoc, sParty + "/ram:GlobalID[not(@schemeID) or @schemeID='']");
      if (!sParty.endsWith ("ShipToTradeParty"))
        ret += _count (aDoc, sParty + "/ram:SpecifiedLegalOrganization/ram:ID[not(@schemeID) or @schemeID='']");
    }
    return ret;
  }

  @Test
  public void testWholeCorpus () throws Exception
  {
    int nUpdated = 0;
    int nWithSuffix = 0;
    int nWithC1 = 0;
    for (final File aFile : _corpus ())
    {
      final Document aIn = _read (aFile);
      final String sOldBT24 = _string (aIn, BT24);

      final ErrorList aErrorList = new ErrorList ();
      final EN16931UpdateDispatcher aDispatcher = new EN16931UpdateDispatcher ().setSourceEdition (EEN16931Edition.EN2017);
      final UpdateResult aResult = aDispatcher.update (aIn, aErrorList);
      // Non-strict: C1 and C2 are warnings, so there must be no error at all
      assertFalse (aFile.getPath () + ": " + aErrorList.getAllErrors (), aErrorList.containsAtLeastOneError ());
      assertEquals (aFile.getPath (), EUpdateStatus.UPDATED, aResult.getStatus ());
      final Document aOut = _write (aResult.getDocument ());
      nUpdated++;

      // R1
      final String sNewBT24 = _string (aOut, BT24);
      if (sOldBT24.startsWith (ID_2017))
      {
        assertEquals (aFile.getPath (), ID_2026 + sOldBT24.substring (ID_2017.length ()), sNewBT24);
        if (sOldBT24.length () > ID_2017.length ())
          nWithSuffix++;
      }
      else
      {
        // Legacy identifier - only reachable because the edition was forced
        assertEquals (aFile.getPath (), ID_2026, sNewBT24);
      }
      assertEquals (aFile.getPath (), 1, _countRule (aErrorList, EUpdateRule.R1) - (sNewBT24.length () > ID_2026.length () ? 1 : 0));

      // R2: every BuyerReference is now a BuyerReferenceID, and no other one appeared
      final double nOldRef = _count (aIn, AGREEMENT + "/ram:BuyerReference");
      assertEquals (aFile.getPath (), 0.0, _count (aOut, AGREEMENT + "/ram:BuyerReference"), 0);
      assertEquals (aFile.getPath (),
                    _count (aIn, AGREEMENT + "/ram:BuyerReferenceID") + nOldRef,
                    _count (aOut, AGREEMENT + "/ram:BuyerReferenceID"),
                    0);
      assertEquals (aFile.getPath (), (long) nOldRef, _countRule (aErrorList, EUpdateRule.R2));

      // C1 - independent count
      final double nExpectedC1 = _expectedC1 (aIn);
      assertEquals (aFile.getPath (), (long) nExpectedC1, _countRule (aErrorList, EUpdateRule.C1));
      if (nExpectedC1 > 0)
        nWithC1++;

      // C2 - every attachment of the corpus has a filename
      assertEquals (aFile.getPath (), 0, _countRule (aErrorList, EUpdateRule.C2));
    }
    // Guards against a corpus that silently shrank
    assertEquals (103, nUpdated);
    assertTrue (nWithSuffix > 50);
    assertTrue (nWithC1 > 50);
  }

  @Test
  public void testR2OnAFileWithBuyerReference () throws Exception
  {
    // At least one file must carry BT-10, otherwise R2 is not tested at all
    int nWithBT10 = 0;
    for (final File aFile : _corpus ())
      if (_count (_read (aFile), AGREEMENT + "/ram:BuyerReference") > 0)
        nWithBT10++;
    assertTrue (nWithBT10 > 10);
  }

  @Test
  public void testStrictTurnsChecksIntoErrors () throws Exception
  {
    // CII_example2 has unscheme'd identifiers
    final File aFile = new File ("src/test/resources/external/cii/CII_example2.xml");
    assertTrue (_expectedC1 (_read (aFile)) > 0);

    final ErrorList aErrorList = new ErrorList ();
    final Serializable aResult = new CIID16BToCIID25AUpdater ().setStrict (true).update (aFile, aErrorList);
    // The document is still produced and unchanged where the check failed
    assertNotNull (aResult);
    assertTrue (aErrorList.containsAtLeastOneError ());
    assertTrue (aErrorList.getAllErrors ().stream ().allMatch (x -> EUpdateRule.C1.getID ().equals (x.getErrorID ())));
  }

  @Test
  public void testGlobalIDWithoutScheme () throws Exception
  {
    final File aFile = new File ("src/test/resources/external/cii/CII_example2.xml");
    final String sXML = Files.readString (aFile.toPath (), StandardCharsets.UTF_8);
    final String sStripped = sXML.replaceAll ("(<ram:GlobalID)\\s+schemeID=\"[^\"]*\"", "$1");
    assertTrue (sStripped.length () < sXML.length ());
    final Document aIn = DOMReader.readXMLDOM (sStripped);
    final double nExpected = _expectedC1 (aIn) - _expectedC1 (_read (aFile));
    assertTrue (nExpected > 0);

    final ErrorList aErrorList = new ErrorList ();
    assertNotNull (new CIID16BToCIID25AUpdater ().update (aIn, aErrorList));
    assertEquals ((long) _expectedC1 (aIn), _countRule (aErrorList, EUpdateRule.C1));
  }

  @Test
  public void testC2MissingFilename () throws Exception
  {
    final File aFile = new File ("src/test/resources/external/cii/CII_example2.xml");
    final String sXML = Files.readString (aFile.toPath (), StandardCharsets.UTF_8);
    final String sStripped = sXML.replaceAll ("\\s+filename=\"[^\"]*\"", "");
    assertTrue (sStripped.length () < sXML.length ());
    final Document aIn = DOMReader.readXMLDOM (sStripped);
    final double nAttachments = _count (aIn, AGREEMENT + "/ram:AdditionalReferencedDocument/ram:AttachmentBinaryObject");
    assertTrue (nAttachments > 0);

    final ErrorList aErrorList = new ErrorList ();
    assertNotNull (new CIID16BToCIID25AUpdater ().update (aIn, aErrorList));
    assertEquals ((long) nAttachments, _countRule (aErrorList, EUpdateRule.C2));
    assertFalse (aErrorList.containsAtLeastOneError ());

    final ErrorList aStrict = new ErrorList ();
    new CIID16BToCIID25AUpdater ().setStrict (true).update (aIn, aStrict);
    assertEquals ((long) nAttachments, aStrict.getAllErrors ().stream ().filter (x -> EUpdateRule.C2.getID ().equals (x.getErrorID ())).count ());
  }

  @Test
  public void testOverrides () throws Exception
  {
    final File aFile = new File ("src/test/resources/external/cii/CII_example1.xml");
    final ErrorList aErrorList = new ErrorList ();
    final Serializable aResult = new CIID16BToCIID25AUpdater ().setCustomizationID ("urn:example:custom")
                                                               .setProfileID ("urn:example:profile")
                                                               .update (aFile, aErrorList);
    assertFalse (aErrorList.containsAtLeastOneError ());
    final Document aOut = _write (aResult);
    assertEquals ("urn:example:custom", _string (aOut, BT24));
    assertEquals ("urn:example:profile",
                  _string (aOut,
                           "/rsm:CrossIndustryInvoice/rsm:ExchangedDocumentContext/ram:BusinessProcessSpecifiedDocumentContextParameter/ram:ID"));
  }

  @Test
  public void testNotEvenXML ()
  {
    final ErrorList aErrorList = new ErrorList ();
    assertEquals (null, new CIID16BToCIID25AUpdater ().update (new File ("src/test/resources/nope.xml"), aErrorList));
    assertTrue (aErrorList.containsAtLeastOneError ());
  }
}
