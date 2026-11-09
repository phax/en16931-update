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

import java.io.Serializable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.tostring.ToStringGenerator;

/**
 * The result of {@link EN16931UpdateDispatcher}. The document is either a CII D25A
 * <code>CrossIndustryInvoiceType</code>, a UBL 2.5 <code>InvoiceType</code> or a UBL 2.5
 * <code>CreditNoteType</code>. Success is <code>hasDocument ()</code> <b>and</b> an error list
 * without errors - a skipped document is neither a success nor a failure.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
@Immutable
public final class UpdateResult
{
  private static final UpdateResult SKIPPED = new UpdateResult (EUpdateStatus.SKIPPED, null);
  private static final UpdateResult FAILED = new UpdateResult (EUpdateStatus.FAILED, null);

  private final EUpdateStatus m_eStatus;
  private final Serializable m_aDocument;

  private UpdateResult (@NonNull final EUpdateStatus eStatus, @Nullable final Serializable aDocument)
  {
    m_eStatus = eStatus;
    m_aDocument = aDocument;
  }

  @NonNull
  public EUpdateStatus getStatus ()
  {
    return m_eStatus;
  }

  /**
   * @return The document. Never <code>null</code> if {@link EUpdateStatus#hasDocument()} is
   *         <code>true</code>, always <code>null</code> otherwise.
   */
  @Nullable
  public Serializable getDocument ()
  {
    return m_aDocument;
  }

  public boolean hasDocument ()
  {
    return m_aDocument != null;
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (null).append ("Status", m_eStatus)
                                       .append ("Document", m_aDocument == null ? null : m_aDocument.getClass ().getName ())
                                       .getToString ();
  }

  @NonNull
  public static UpdateResult createUpdated (@NonNull final Serializable aDocument)
  {
    ValueEnforcer.notNull (aDocument, "Document");
    return new UpdateResult (EUpdateStatus.UPDATED, aDocument);
  }

  @NonNull
  public static UpdateResult createCopied (@NonNull final Serializable aDocument)
  {
    ValueEnforcer.notNull (aDocument, "Document");
    return new UpdateResult (EUpdateStatus.COPIED, aDocument);
  }

  @NonNull
  public static UpdateResult createSkipped ()
  {
    return SKIPPED;
  }

  @NonNull
  public static UpdateResult createFailed ()
  {
    return FAILED;
  }
}
