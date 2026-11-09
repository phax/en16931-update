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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
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

import org.w3c.dom.Document;

import com.helger.diagnostics.error.list.ErrorList;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Helpers of the updater tests. The expectations of the tests are computed with XPath directly on
 * the input document, independent of the JAXB code of the updaters.
 *
 * @author Philip Helger
 */
public final class UpdateTestHelper
{
  private static final NamespaceContext NS = new NamespaceContext ()
  {
    public String getNamespaceURI (final String sPrefix)
    {
      return switch (sPrefix)
      {
        case "rsm" -> "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
        case "ram" -> "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
        case "inv" -> "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        case "cn" -> "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
        case "cac" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
        case "cbc" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
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

  private UpdateTestHelper ()
  {}

  /**
   * @param sDir
   *        Directory below <code>src/test/resources</code>
   * @return All XML files below the directory, sorted
   * @throws IOException
   *         on error
   */
  public static List <File> listXML (final String sDir) throws IOException
  {
    try (final Stream <Path> aStream = Files.walk (Path.of ("src/test/resources", sDir)))
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

  public static long count (final Document aDoc, final String sXPath) throws Exception
  {
    return ((Double) _xpath ().evaluate ("count(" + sXPath + ")", aDoc, XPathConstants.NUMBER)).longValue ();
  }

  public static String string (final Document aDoc, final String sXPath) throws Exception
  {
    return (String) _xpath ().evaluate (sXPath, aDoc, XPathConstants.STRING);
  }

  public static Document read (final File aFile)
  {
    final Document ret = DOMReader.readXMLDOM (aFile);
    assertNotNull (aFile.getPath (), ret);
    return ret;
  }

  public static Document parse (final String sXML)
  {
    final Document ret = DOMReader.readXMLDOM (sXML);
    assertNotNull (ret);
    return ret;
  }

  /**
   * @param aResult
   *        The result of an updater
   * @return The written and re-read result - writing validates against the 2026 schema.
   * @throws Exception
   *         on error
   */
  public static Document write (final Serializable aResult) throws Exception
  {
    assertNotNull (aResult);
    final ErrorList aErrorList = new ErrorList ();
    final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
    assertTrue (aErrorList.getAllErrors ().toString (),
                EN16931UpdateDispatcher.writeDocument (aResult, aBAOS, aErrorList).isSuccess ());
    assertFalse (aErrorList.getAllErrors ().toString (), aErrorList.containsAtLeastOneError ());
    return parse (new String (aBAOS.toByteArray (), java.nio.charset.StandardCharsets.UTF_8));
  }

  /**
   * @param aErrorList
   *        The list to count in
   * @param eRule
   *        The rule
   * @return The number of entries of any level that belong to the rule
   */
  public static long countRule (final ErrorList aErrorList, final EUpdateRule eRule)
  {
    return aErrorList.stream ().filter (x -> eRule.getID ().equals (x.getErrorID ())).count ();
  }

  /**
   * @param aErrorList
   *        The list to count in
   * @param eRule
   *        The rule
   * @return The number of entries of level info that belong to the rule - the applied rewrites
   */
  public static long countApplied (final ErrorList aErrorList, final EUpdateRule eRule)
  {
    return aErrorList.stream ()
                     .filter (x -> eRule.getID ().equals (x.getErrorID ()) &&
                                   x.getErrorLevel ().isLT (com.helger.diagnostics.error.level.EErrorLevel.WARN))
                     .count ();
  }
}
