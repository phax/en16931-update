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
import com.helger.en16931.update.EUpdateRule;
import com.helger.ubl25.UBL25Marshaller;

import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.DocumentReferenceType;
import oasis.names.specification.ubl.schema.xsd.invoice_25.InvoiceType;

/**
 * Updates an EN 16931:2017 UBL 2.1 Invoice to an EN 16931:2026 UBL 2.5 Invoice. The document is
 * read with the UBL 2.5 model, so everything it contains is preserved. The rules are documented in
 * <code>docs/update-rules.md</code>:
 * <ul>
 * <li>R1 - BT-24 (and an optional BT-23 override)</li>
 * <li>R2 - BT-10 <code>cbc:BuyerReference</code> becomes
 * <code>cac:BuyerAssignedReference/cbc:BuyerReference</code></li>
 * <li>R3 - BG-1 <code>cbc:Note</code> becomes <code>cac:Annotation</code></li>
 * <li>R6 - BT-32-2 becomes <code>LOC</code></li>
 * <li>R7 - BG-24 gets the document type code <code>916</code></li>
 * <li>R9 - <code>cbc:UBLVersionID</code></li>
 * <li>C1 - party identifiers without a scheme identifier - reported, never changed</li>
 * <li>C2 - attached documents without a filename - reported, never changed</li>
 * </ul>
 * R4 and R5 only apply to the Credit Note.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
public class UBL21InvoiceToUBL25InvoiceUpdater extends AbstractUBL21ToUBL25Updater <UBL21InvoiceToUBL25InvoiceUpdater>
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

    // R9
    final String sNewVersion = getUpdatedUBLVersionID (ret.getUBLVersionIDValue (), aErrorList);
    if (sNewVersion != null)
      ret.setUBLVersionID (sNewVersion);

    // R1 - BT-24 and BT-23
    ret.setCustomizationID (getUpdatedSpecificationIdentifier (ret.getCustomizationIDValue (),
                                                                "cbc:CustomizationID",
                                                                aErrorList));
    if (getProfileID () != null)
    {
      ret.setProfileID (getProfileID ());
      reportApplied (aErrorList, EUpdateRule.R1, "cbc:ProfileID", "BT-23 set to the configured value '" + getProfileID () + "'");
    }

    // R3
    moveNotes (ret.getNote (), ret.getAnnotation (), aErrorList);

    // R2
    if (moveBuyerReference (ret.getBuyerReference (), ret.getBuyerAssignedReference (), aErrorList))
      ret.setBuyerReference ((oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_25.BuyerReferenceType) null);

    // R6
    if (ret.getAccountingSupplierParty () != null)
      updateSellerTaxScheme (ret.getAccountingSupplierParty ().getParty (), aErrorList);

    // R7 - on an Invoice, a supporting document is a reference without any type
    for (final DocumentReferenceType aRef : ret.getAdditionalDocumentReference ())
      if (hasNoDocumentType (aRef))
        setSupportingDocumentType (aRef, aErrorList);

    // C1, C2
    checkPartyIdentifiers (aErrorList,
                           ret.getAccountingSupplierParty () == null ? null : ret.getAccountingSupplierParty ().getParty (),
                           ret.getAccountingCustomerParty () == null ? null : ret.getAccountingCustomerParty ().getParty (),
                           ret.getPayeeParty (),
                           ret.getDelivery ());
    checkAttachmentFilenames (ret.getAdditionalDocumentReference (), aErrorList);

    return ret;
  }
}
