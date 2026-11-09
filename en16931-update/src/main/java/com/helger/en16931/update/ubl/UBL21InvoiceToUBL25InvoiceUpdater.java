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
package com.helger.en16931.update.ubl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931SyntaxKind;
import com.helger.en16931.update.AbstractEN16931Updater;
import com.helger.ubl25.UBL25Marshaller;

import oasis.names.specification.ubl.schema.xsd.invoice_25.InvoiceType;

/**
 * Updates an EN 16931:2017 UBL 2.1 Invoice to an EN 16931:2026 UBL 2.5 Invoice.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
public class UBL21InvoiceToUBL25InvoiceUpdater extends AbstractEN16931Updater <UBL21InvoiceToUBL25InvoiceUpdater>
{
  public UBL21InvoiceToUBL25InvoiceUpdater ()
  {}

  @NonNull
  public final EEN16931SyntaxKind getSyntaxKind ()
  {
    return EEN16931SyntaxKind.UBL_INVOICE;
  }

  @Override
  @Nullable
  public InvoiceType update (@NonNull final Node aNode, @NonNull final ErrorList aErrorList)
  {
    ValueEnforcer.notNull (aNode, "Node");
    ValueEnforcer.notNull (aErrorList, "ErrorList");

    // Reading validates against the UBL 2.5 schema
    final InvoiceType ret = UBL25Marshaller.invoice ().setCollectErrors (aErrorList).read (aNode);
    if (ret == null)
      return null;

    return ret;
  }
}
