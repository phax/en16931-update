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

import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.trait.IGenericImplTrait;
import com.helger.diagnostics.error.IError;
import com.helger.diagnostics.error.SingleError;
import com.helger.diagnostics.error.SingleErrorBuilder;
import com.helger.diagnostics.error.list.ErrorList;

/**
 * Base class of everything that updates EN 16931:2017 documents to EN 16931:2026: the settings
 * API and the reporting helpers. It deliberately references neither a UBL nor a CII type.
 *
 * @author Philip Helger
 * @param <IMPLTYPE>
 *        The implementation type
 * @since 1.0.0
 */
public abstract class AbstractEN16931UpdaterBase <IMPLTYPE extends AbstractEN16931UpdaterBase <IMPLTYPE>> implements
                                                 IGenericImplTrait <IMPLTYPE>
{
  public static final boolean DEFAULT_STRICT = false;

  private boolean m_bStrict = DEFAULT_STRICT;
  private String m_sCustomizationID;
  private String m_sProfileID;

  protected AbstractEN16931UpdaterBase ()
  {}

  /**
   * @return <code>true</code> if a failed check (rule C1, C2) is an error, <code>false</code> if
   *         it is a warning and the element is left as it is. Default is
   *         {@value #DEFAULT_STRICT}.
   */
  public final boolean isStrict ()
  {
    return m_bStrict;
  }

  @NonNull
  public final IMPLTYPE setStrict (final boolean bStrict)
  {
    m_bStrict = bStrict;
    return thisAsT ();
  }

  /**
   * @return The BT-24 value to write instead of the one derived from the source document, or
   *         <code>null</code> to derive it.
   */
  @Nullable
  public final String getCustomizationID ()
  {
    return m_sCustomizationID;
  }

  @NonNull
  public final IMPLTYPE setCustomizationID (@Nullable final String sCustomizationID)
  {
    m_sCustomizationID = sCustomizationID;
    return thisAsT ();
  }

  /**
   * @return The BT-23 value to write instead of the one of the source document, or
   *         <code>null</code> to keep it.
   */
  @Nullable
  public final String getProfileID ()
  {
    return m_sProfileID;
  }

  @NonNull
  public final IMPLTYPE setProfileID (@Nullable final String sProfileID)
  {
    m_sProfileID = sProfileID;
    return thisAsT ();
  }

  /**
   * Copy the settings of this object to another one.
   *
   * @param aTarget
   *        The target. May not be <code>null</code>.
   * @return The target for chaining.
   */
  @NonNull
  protected final <T extends AbstractEN16931UpdaterBase <?>> T copySettingsTo (@NonNull final T aTarget)
  {
    ValueEnforcer.notNull (aTarget, "Target");
    aTarget.setStrict (m_bStrict);
    aTarget.setCustomizationID (m_sCustomizationID);
    aTarget.setProfileID (m_sProfileID);
    return aTarget;
  }

  /**
   * Report an applied rewrite - every rewrite is auditable.
   *
   * @param aErrorList
   *        The error list to add to. May not be <code>null</code>.
   * @param eRule
   *        The rule that was applied. May not be <code>null</code>.
   * @param sPath
   *        The path of the changed element. May be <code>null</code>.
   * @param sMsg
   *        What was changed. May not be <code>null</code>.
   */
  protected static void reportApplied (@NonNull final ErrorList aErrorList,
                                       @NonNull final EUpdateRule eRule,
                                       @Nullable final String sPath,
                                       @NonNull final String sMsg)
  {
    aErrorList.add (_build (SingleError.builderInfo (), eRule, sPath, sMsg));
  }

  /**
   * Report a warning of a rule.
   *
   * @param aErrorList
   *        The error list to add to. May not be <code>null</code>.
   * @param eRule
   *        The rule. May not be <code>null</code>.
   * @param sPath
   *        The path of the element. May be <code>null</code>.
   * @param sMsg
   *        The message. May not be <code>null</code>.
   */
  protected static void reportWarning (@NonNull final ErrorList aErrorList,
                                       @NonNull final EUpdateRule eRule,
                                       @Nullable final String sPath,
                                       @NonNull final String sMsg)
  {
    aErrorList.add (_build (SingleError.builderWarn (), eRule, sPath, sMsg));
  }

  /**
   * Report a failed check: an error in {@link #isStrict() strict} mode, otherwise a warning. In
   * both cases the document itself is not touched.
   *
   * @param aErrorList
   *        The error list to add to. May not be <code>null</code>.
   * @param eRule
   *        The check. May not be <code>null</code>.
   * @param sPath
   *        The path of the element. May be <code>null</code>.
   * @param sMsg
   *        The message. May not be <code>null</code>.
   */
  protected final void reportCheckFailure (@NonNull final ErrorList aErrorList,
                                           @NonNull final EUpdateRule eRule,
                                           @Nullable final String sPath,
                                           @NonNull final String sMsg)
  {
    if (m_bStrict)
      aErrorList.add (_build (SingleError.builderError (), eRule, sPath, sMsg));
    else
      reportWarning (aErrorList, eRule, sPath, sMsg);
  }

  @NonNull
  private static IError _build (@NonNull final SingleErrorBuilder aBuilder,
                                @NonNull final EUpdateRule eRule,
                                @Nullable final String sPath,
                                @NonNull final String sMsg)
  {
    return aBuilder.errorID (eRule.getID ()).errorFieldName (sPath).errorText (sMsg).build ();
  }

  /**
   * Add a warning that is not tied to a rule.
   *
   * @param aErrorList
   *        The error list to add to. May not be <code>null</code>.
   * @param sMsg
   *        The message. May not be <code>null</code>.
   */
  protected static void reportWarning (@NonNull final ErrorList aErrorList, @NonNull final String sMsg)
  {
    aErrorList.add (SingleError.builderWarn ().errorText (sMsg).build ());
  }

  /**
   * Add an error that is not tied to a rule.
   *
   * @param aErrorList
   *        The error list to add to. May not be <code>null</code>.
   * @param sMsg
   *        The message. May not be <code>null</code>.
   */
  protected static void reportError (@NonNull final ErrorList aErrorList, @NonNull final String sMsg)
  {
    aErrorList.add (SingleError.builderError ().errorText (sMsg).build ());
  }
}
