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

/**
 * What to do with an ID-only <code>cac:AdditionalDocumentReference</code> of a UBL 2.1 Credit Note,
 * which carries neither a type, nor a description, nor an attachment. In the 2017 binding such a
 * reference is either BT-11 (project reference, which UBL 2.1 Credit Notes have no element for) or
 * a supporting document (BG-24) that consists of an identifier only. Nothing in the document tells
 * the two apart.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
public enum ECreditNoteIDOnlyReferenceHandling implements IHasID <String>
{
  /** Leave the reference unchanged and report a warning. This is the default. */
  KEEP ("keep"),
  /**
   * It is BT-11 and moves to <code>cac:ProjectReference</code>. Only done if there is exactly one
   * such reference and the Credit Note has no project reference yet.
   */
  PROJECT ("project"),
  /** It is a supporting document (BG-24) and gets the document type code 916. */
  DOCUMENT ("document");

  public static final ECreditNoteIDOnlyReferenceHandling DEFAULT = KEEP;

  private final String m_sID;

  ECreditNoteIDOnlyReferenceHandling (@NonNull @Nonempty final String sID)
  {
    m_sID = sID;
  }

  @NonNull
  @Nonempty
  public String getID ()
  {
    return m_sID;
  }

  @Nullable
  public static ECreditNoteIDOnlyReferenceHandling getFromIDOrNull (@Nullable final String sID)
  {
    return EnumHelper.getFromIDOrNull (ECreditNoteIDOnlyReferenceHandling.class, sID);
  }
}
