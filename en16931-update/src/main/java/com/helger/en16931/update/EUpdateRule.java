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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.base.id.IHasID;
import com.helger.base.lang.EnumHelper;
import com.helger.base.name.IHasDisplayName;

/**
 * The rules of the update from EN 16931:2017 to EN 16931:2026. The identifiers are those of
 * <code>docs/update-rules.md</code>. Every applied rewrite and every failed check is reported
 * with its rule ID as the error ID. R8 of that document is model only and has no behaviour.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
public enum EUpdateRule implements IHasID <String>, IHasDisplayName
{
  /** BT-24 specification identifier */
  R1 ("R1", "BT-24 specification identifier"),
  /** BT-10 buyer reference */
  R2 ("R2", "BT-10 buyer reference"),
  /** BG-1 invoice note, BT-21 and BT-22 (UBL only) */
  R3 ("R3", "BG-1 invoice note"),
  /** BT-9 payment due date (UBL Credit Note only) */
  R4 ("R4", "BT-9 payment due date of a Credit Note"),
  /** BT-11 project reference (UBL Credit Note only) */
  R5 ("R5", "BT-11 project reference of a Credit Note"),
  /** BT-32-2 national tax scheme (UBL only) */
  R6 ("R6", "BT-32-2 national tax scheme"),
  /** BT-122-1 supporting document reference code (UBL only) */
  R7 ("R7", "BT-122-1 supporting document reference code"),
  /** UBL version identifier (UBL only) */
  R9 ("R9", "UBL version identifier"),
  /** Mandatory scheme identifier of a party identifier */
  C1 ("C1", "Party identifier scheme identifier"),
  /** Mandatory filename of an attached document */
  C2 ("C2", "BT-125-2 attached document filename");

  private final String m_sID;
  private final String m_sDisplayName;

  EUpdateRule (@NonNull @Nonempty final String sID, @NonNull @Nonempty final String sDisplayName)
  {
    m_sID = sID;
    m_sDisplayName = sDisplayName;
  }

  @NonNull
  @Nonempty
  public String getID ()
  {
    return m_sID;
  }

  @NonNull
  @Nonempty
  public String getDisplayName ()
  {
    return m_sDisplayName;
  }

  @Nullable
  public static EUpdateRule getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (EUpdateRule.class, sID);
  }
}
