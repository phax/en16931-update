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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.cii.d25a.CIID25ACrossIndustryInvoiceTypeMarshaller;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931SyntaxKind;
import com.helger.en16931.update.AbstractEN16931Updater;

import un.unece.uncefact.data.standard.cii.d25a.CrossIndustryInvoiceType;

/**
 * Updates an EN 16931:2017 CII D16B document to an EN 16931:2026 CII D25A document.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
public class CIID16BToCIID25AUpdater extends AbstractEN16931Updater <CIID16BToCIID25AUpdater>
{
  public CIID16BToCIID25AUpdater ()
  {}

  @NonNull
  public final EEN16931SyntaxKind getSyntaxKind ()
  {
    return EEN16931SyntaxKind.CII;
  }

  @Override
  @Nullable
  public CrossIndustryInvoiceType update (@NonNull final Node aNode, @NonNull final ErrorList aErrorList)
  {
    ValueEnforcer.notNull (aNode, "Node");
    ValueEnforcer.notNull (aErrorList, "ErrorList");

    // Reading validates against the D25A schema
    final CrossIndustryInvoiceType ret = new CIID25ACrossIndustryInvoiceTypeMarshaller ().setCollectErrors (aErrorList)
                                                                                         .read (aNode);
    if (ret == null)
      return null;

    return ret;
  }
}
