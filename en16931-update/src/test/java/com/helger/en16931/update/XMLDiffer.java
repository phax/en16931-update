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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * Compares two XML documents element by element and reports every difference with a path. It is the
 * tool of the preservation test: an update may only change what its rules name, so everything else
 * has to show up here as <em>no difference</em>. Whitespace-only text, comments and namespace
 * declarations are ignored. Children are paired by name and position among their same-named
 * siblings, which is exact as long as an update removes all or the last of a kind - the case for
 * every rule on the corpus.
 *
 * @author Philip Helger
 */
public final class XMLDiffer
{
  public enum EKind
  {
    ELEMENT_LOST,
    ELEMENT_ADDED,
    ATTRIBUTE_LOST,
    ATTRIBUTE_ADDED,
    ATTRIBUTE_CHANGED,
    TEXT_CHANGED,
    ORDER_CHANGED
  }

  public record Diff (EKind kind, String path, String detail)
  {
    @Override
    public String toString ()
    {
      return kind + " " + path + (detail.isEmpty () ? "" : " " + detail);
    }
  }

  private static final Map <String, String> PREFIXES = new LinkedHashMap <> ();
  static
  {
    PREFIXES.put ("urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100", "rsm");
    PREFIXES.put ("urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100", "ram");
    PREFIXES.put ("urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100", "udt");
    PREFIXES.put ("urn:un:unece:uncefact:data:standard:QualifiedDataType:100", "qdt");
    PREFIXES.put ("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2", "inv");
    PREFIXES.put ("urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2", "cn");
    PREFIXES.put ("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2", "cac");
    PREFIXES.put ("urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2", "cbc");
    PREFIXES.put ("urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2", "ext");
    PREFIXES.put ("http://www.w3.org/2001/XMLSchema-instance", "xsi");
  }

  private XMLDiffer ()
  {}

  private static String _name (final String sNS, final String sLocal)
  {
    final String sPrefix = PREFIXES.get (sNS);
    return sPrefix != null ? sPrefix + ":" + sLocal : sNS == null ? sLocal : "{" + sNS + "}" + sLocal;
  }

  private static String _name (final Node aNode)
  {
    return _name (aNode.getNamespaceURI (), aNode.getLocalName ());
  }

  private static List <Element> _children (final Element aElement)
  {
    final List <Element> ret = new ArrayList <> ();
    for (Node c = aElement.getFirstChild (); c != null; c = c.getNextSibling ())
      if (c instanceof final Element aChild)
        ret.add (aChild);
    return ret;
  }

  private static String _text (final Element aElement)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (Node c = aElement.getFirstChild (); c != null; c = c.getNextSibling ())
      if (c instanceof final Text aText)
        aSB.append (aText.getNodeValue ());
    return aSB.toString ().trim ();
  }

  private static Map <String, String> _attributes (final Element aElement)
  {
    final Map <String, String> ret = new TreeMap <> ();
    final NamedNodeMap aMap = aElement.getAttributes ();
    for (int i = 0; i < aMap.getLength (); i++)
    {
      final Node aAttr = aMap.item (i);
      if (!"http://www.w3.org/2000/xmlns/".equals (aAttr.getNamespaceURI ()))
        ret.put (_name (aAttr.getNamespaceURI (), aAttr.getLocalName ()), aAttr.getNodeValue ());
    }
    return ret;
  }

  private static Map <String, List <Element>> _group (final List <Element> aChildren)
  {
    final Map <String, List <Element>> ret = new LinkedHashMap <> ();
    for (final Element aChild : aChildren)
      ret.computeIfAbsent (_name (aChild), k -> new ArrayList <> ()).add (aChild);
    return ret;
  }

  /**
   * @param aIn
   *        The document element of the input
   * @param aOut
   *        The document element of the output
   * @return All differences. Empty if the documents are the same.
   */
  public static List <Diff> diff (final Element aIn, final Element aOut)
  {
    final List <Diff> ret = new ArrayList <> ();
    if (!_name (aIn).equals (_name (aOut)))
      ret.add (new Diff (EKind.ELEMENT_LOST, "/" + _name (aIn), "document element is now " + _name (aOut)));
    else
      _diff (aIn, aOut, "/" + _name (aIn) + "[1]", ret);
    return ret;
  }

  private static List <String> _commonOrder (final List <Element> aKids,
                                             final Map <String, List <Element>> aInGroups,
                                             final Map <String, List <Element>> aOutGroups)
  {
    final List <String> ret = new ArrayList <> ();
    final Map <String, Integer> aSeen = new java.util.HashMap <> ();
    for (final Element aKid : aKids)
    {
      final String sName = _name (aKid);
      if (!aInGroups.containsKey (sName) || !aOutGroups.containsKey (sName))
        continue;
      final int nSeen = aSeen.merge (sName, Integer.valueOf (1), Integer::sum).intValue ();
      if (nSeen <= Math.min (aInGroups.get (sName).size (), aOutGroups.get (sName).size ()))
        ret.add (sName);
    }
    return ret;
  }

  private static void _diff (final Element aIn, final Element aOut, final String sPath, final List <Diff> aDiffs)
  {
    final Map <String, String> aInAttrs = _attributes (aIn);
    final Map <String, String> aOutAttrs = _attributes (aOut);
    for (final Map.Entry <String, String> aEntry : aInAttrs.entrySet ())
      if (!aOutAttrs.containsKey (aEntry.getKey ()))
        aDiffs.add (new Diff (EKind.ATTRIBUTE_LOST, sPath + "/@" + aEntry.getKey (), "'" + aEntry.getValue () + "'"));
      else
        if (!aOutAttrs.get (aEntry.getKey ()).equals (aEntry.getValue ()))
          aDiffs.add (new Diff (EKind.ATTRIBUTE_CHANGED,
                                sPath + "/@" + aEntry.getKey (),
                                "'" + aEntry.getValue () + "' -> '" + aOutAttrs.get (aEntry.getKey ()) + "'"));
    for (final Map.Entry <String, String> aEntry : aOutAttrs.entrySet ())
      if (!aInAttrs.containsKey (aEntry.getKey ()))
        aDiffs.add (new Diff (EKind.ATTRIBUTE_ADDED, sPath + "/@" + aEntry.getKey (), "'" + aEntry.getValue () + "'"));

    final List <Element> aInKids = _children (aIn);
    final List <Element> aOutKids = _children (aOut);
    if (aInKids.isEmpty () && aOutKids.isEmpty ())
    {
      if (!_text (aIn).equals (_text (aOut)))
        aDiffs.add (new Diff (EKind.TEXT_CHANGED, sPath, "'" + _text (aIn) + "' -> '" + _text (aOut) + "'"));
      return;
    }
    if (!_text (aIn).equals (_text (aOut)))
      aDiffs.add (new Diff (EKind.TEXT_CHANGED, sPath, "mixed content"));

    final Map <String, List <Element>> aInGroups = _group (aInKids);
    final Map <String, List <Element>> aOutGroups = _group (aOutKids);
    for (final Map.Entry <String, List <Element>> aEntry : aInGroups.entrySet ())
    {
      final List <Element> aInList = aEntry.getValue ();
      final List <Element> aOutList = aOutGroups.getOrDefault (aEntry.getKey (), List.of ());
      for (int i = 0; i < aInList.size (); i++)
      {
        final String sChild = sPath + "/" + aEntry.getKey () + "[" + (i + 1) + "]";
        if (i < aOutList.size ())
          _diff (aInList.get (i), aOutList.get (i), sChild, aDiffs);
        else
          aDiffs.add (new Diff (EKind.ELEMENT_LOST, sChild, ""));
      }
    }
    for (final Map.Entry <String, List <Element>> aEntry : aOutGroups.entrySet ())
    {
      final int nIn = aInGroups.getOrDefault (aEntry.getKey (), List.of ()).size ();
      for (int i = nIn; i < aEntry.getValue ().size (); i++)
        aDiffs.add (new Diff (EKind.ELEMENT_ADDED, sPath + "/" + aEntry.getKey () + "[" + (i + 1) + "]", ""));
    }

    // The order of what both documents have - of each name only as many as both have
    final List <String> aInOrder = _commonOrder (aInKids, aInGroups, aOutGroups);
    final List <String> aOutOrder = _commonOrder (aOutKids, aInGroups, aOutGroups);
    if (!aInOrder.equals (aOutOrder))
      aDiffs.add (new Diff (EKind.ORDER_CHANGED, sPath, aInOrder + " -> " + aOutOrder));
  }
}
