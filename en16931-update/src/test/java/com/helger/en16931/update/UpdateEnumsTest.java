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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Test class for the small enums of this package.
 *
 * @author Philip Helger
 */
public final class UpdateEnumsTest
{
  @Test
  public void testRuleIDs ()
  {
    for (final EUpdateRule e : EUpdateRule.values ())
      assertSame (e, EUpdateRule.getFromIDOrNull (e.getID ()));
    assertNull (EUpdateRule.getFromIDOrNull ("R8"));
    assertNull (EUpdateRule.getFromIDOrNull (null));
  }

  @Test
  public void testAlready2026Handling ()
  {
    for (final EAlready2026Handling e : EAlready2026Handling.values ())
      assertSame (e, EAlready2026Handling.getFromIDOrNull (e.getID ()));
    assertNull (EAlready2026Handling.getFromIDOrNull ("other"));
    assertEquals (EAlready2026Handling.SKIP, EAlready2026Handling.DEFAULT);
  }

  @Test
  public void testStatus ()
  {
    assertEquals (true, EUpdateStatus.UPDATED.hasDocument ());
    assertEquals (true, EUpdateStatus.COPIED.hasDocument ());
    assertEquals (false, EUpdateStatus.SKIPPED.hasDocument ());
    assertEquals (false, EUpdateStatus.FAILED.hasDocument ());
  }
}
