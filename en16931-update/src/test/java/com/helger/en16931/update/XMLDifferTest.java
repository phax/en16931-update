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

import static com.helger.en16931.update.UpdateTestHelper.parse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.helger.en16931.update.XMLDiffer.Diff;
import com.helger.en16931.update.XMLDiffer.EKind;

/**
 * Test class for class {@link XMLDiffer} - the preservation test is only worth something if the
 * differ sees every kind of change.
 *
 * @author Philip Helger
 */
public final class XMLDifferTest
{
  private static final String NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
  private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

  private static List <Diff> _diff (final String sIn, final String sOut)
  {
    final String sHead = "<Invoice xmlns=\"" + NS + "\" xmlns:cbc=\"" + CBC + "\">";
    return XMLDiffer.diff (parse (sHead + sIn + "</Invoice>").getDocumentElement (),
                           parse (sHead + sOut + "</Invoice>").getDocumentElement ());
  }

  @Test
  public void testSame ()
  {
    assertTrue (_diff ("<cbc:ID>1</cbc:ID><cbc:Note>a</cbc:Note>", "  <cbc:ID> 1 </cbc:ID>\n<!-- c --><cbc:Note>a</cbc:Note>").isEmpty ());
  }

  @Test
  public void testChangedText ()
  {
    final List <Diff> a = _diff ("<cbc:ID>1</cbc:ID>", "<cbc:ID>2</cbc:ID>");
    assertEquals (1, a.size ());
    assertEquals (EKind.TEXT_CHANGED, a.get (0).kind ());
    assertEquals ("/inv:Invoice[1]/cbc:ID[1]", a.get (0).path ());
  }

  @Test
  public void testLostAndAddedElements ()
  {
    List <Diff> a = _diff ("<cbc:ID>1</cbc:ID><cbc:Note>a</cbc:Note><cbc:Note>b</cbc:Note>", "<cbc:ID>1</cbc:ID><cbc:Note>a</cbc:Note>");
    assertEquals (1, a.size ());
    assertEquals (EKind.ELEMENT_LOST, a.get (0).kind ());
    assertEquals ("/inv:Invoice[1]/cbc:Note[2]", a.get (0).path ());

    a = _diff ("<cbc:ID>1</cbc:ID>", "<cbc:ID>1</cbc:ID><cbc:Note>a</cbc:Note>");
    assertEquals (1, a.size ());
    assertEquals (EKind.ELEMENT_ADDED, a.get (0).kind ());
  }

  @Test
  public void testAttributes ()
  {
    List <Diff> a = _diff ("<cbc:ID schemeID=\"x\">1</cbc:ID>", "<cbc:ID>1</cbc:ID>");
    assertEquals (EKind.ATTRIBUTE_LOST, a.get (0).kind ());
    a = _diff ("<cbc:ID>1</cbc:ID>", "<cbc:ID schemeID=\"x\">1</cbc:ID>");
    assertEquals (EKind.ATTRIBUTE_ADDED, a.get (0).kind ());
    a = _diff ("<cbc:ID schemeID=\"x\">1</cbc:ID>", "<cbc:ID schemeID=\"y\">1</cbc:ID>");
    assertEquals (EKind.ATTRIBUTE_CHANGED, a.get (0).kind ());
    assertEquals (1, a.size ());
  }

  @Test
  public void testOrder ()
  {
    final List <Diff> a = _diff ("<cbc:ID>1</cbc:ID><cbc:Note>a</cbc:Note>", "<cbc:Note>a</cbc:Note><cbc:ID>1</cbc:ID>");
    assertEquals (1, a.size ());
    assertEquals (EKind.ORDER_CHANGED, a.get (0).kind ());
  }

  @Test
  public void testDeepChange ()
  {
    final List <Diff> a = _diff ("<cbc:Note>a<cbc:ID>1</cbc:ID></cbc:Note>", "<cbc:Note>a<cbc:ID>2</cbc:ID></cbc:Note>");
    assertEquals (1, a.size ());
    assertEquals ("/inv:Invoice[1]/cbc:Note[1]/cbc:ID[1]", a.get (0).path ());
  }
}
