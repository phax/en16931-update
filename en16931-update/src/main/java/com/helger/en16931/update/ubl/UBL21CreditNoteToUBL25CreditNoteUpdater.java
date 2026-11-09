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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.datetime.xml.XMLOffsetDate;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931SyntaxKind;
import com.helger.en16931.update.EUpdateRule;
import com.helger.ubl25.UBL25Marshaller;

import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.DocumentReferenceType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.PaymentMeansType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.ProjectReferenceType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_25.BuyerReferenceType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_25.PaymentDueDateType;
import oasis.names.specification.ubl.schema.xsd.creditnote_25.CreditNoteType;

/**
 * Updates an EN 16931:2017 UBL 2.1 Credit Note to an EN 16931:2026 UBL 2.5 Credit Note. The
 * document is read with the UBL 2.5 model, so everything it contains is preserved. The rules are
 * documented in <code>docs/update-rules.md</code>. Like for the Invoice they are R1, R2, R3, R6,
 * R9, C1 and C2, and additionally:
 * <ul>
 * <li>R4 - BT-9 moves from <code>cac:PaymentMeans/cbc:PaymentDueDate</code> to
 * <code>cbc:DueDate</code></li>
 * <li>R5 - BT-11 moves from <code>cac:AdditionalDocumentReference</code> to
 * <code>cac:ProjectReference</code>, but only as far as it can be told apart from a supporting
 * document, see {@link com.helger.en16931.update.ECreditNoteIDOnlyReferenceHandling}</li>
 * <li>R7 - BG-24 gets the document type code <code>916</code>, if it can be told from BT-11 and
 * BT-18</li>
 * </ul>
 *
 * @author Philip Helger
 * @since 1.0.0
 */
public class UBL21CreditNoteToUBL25CreditNoteUpdater extends
                                                     AbstractUBL21ToUBL25Updater <UBL21CreditNoteToUBL25CreditNoteUpdater>
{
  public UBL21CreditNoteToUBL25CreditNoteUpdater ()
  {}

  @Override
  @NonNull
  public final EEN16931SyntaxKind getSyntaxKind ()
  {
    return EEN16931SyntaxKind.UBL_CREDIT_NOTE;
  }

  private static void _r4 (@NonNull final CreditNoteType aCN, @NonNull final ErrorList aErrorList)
  {
    XMLOffsetDate aMoved = null;
    for (final PaymentMeansType aPM : aCN.getPaymentMeans ())
      if (aPM.getPaymentDueDate () != null)
      {
        aMoved = aPM.getPaymentDueDateValue ();
        break;
      }
    if (aMoved == null)
      return;

    if (aCN.getDueDate () != null)
    {
      // Not an EN 16931:2017 document - the new element is already there
      reportWarning (aErrorList,
                     EUpdateRule.R4,
                     "cac:PaymentMeans/cbc:PaymentDueDate",
                     "The Credit Note already has a cbc:DueDate, so BT-9 was left unchanged in cac:PaymentMeans");
      return;
    }

    aCN.setDueDate (aMoved);
    for (final PaymentMeansType aPM : aCN.getPaymentMeans ())
      if (aPM.getPaymentDueDate () != null)
      {
        if (aMoved.equals (aPM.getPaymentDueDateValue ()))
          aPM.setPaymentDueDate ((PaymentDueDateType) null);
        else
          reportWarning (aErrorList,
                         EUpdateRule.R4,
                         "cac:PaymentMeans/cbc:PaymentDueDate",
                         "BT-9 is 0..1 but cac:PaymentMeans has the differing due date '" +
                                                                aPM.getPaymentDueDateValue () +
                                                                "' - it was left unchanged");
      }
    reportApplied (aErrorList,
                   EUpdateRule.R4,
                   "cac:PaymentMeans/cbc:PaymentDueDate",
                   "BT-9 '" + aMoved + "' moved to cbc:DueDate");
  }

  /** @return <code>true</code> if the reference carries nothing but its identifier */
  private static boolean _isIDOnly (@NonNull final DocumentReferenceType aRef)
  {
    return aRef.getID () != null &&
           aRef.getUUID () == null &&
           aRef.getIssueDate () == null &&
           aRef.getIssueTime () == null &&
           aRef.getDocumentTypeCode () == null &&
           aRef.getDocumentType ().isEmpty () &&
           aRef.getXPath ().isEmpty () &&
           aRef.getReferencedDocumentInternalAddress () == null &&
           aRef.getLanguageID () == null &&
           aRef.getLocaleCode () == null &&
           aRef.getVersionID () == null &&
           aRef.getDocumentStatusCode () == null &&
           aRef.getDocumentDescription ().isEmpty () &&
           aRef.getAttachment () == null &&
           aRef.getValidityPeriod () == null &&
           aRef.getIssuerParty () == null &&
           aRef.getResultOfVerification () == null;
  }

  private void _r5r7 (@NonNull final CreditNoteType aCN, @NonNull final ErrorList aErrorList)
  {
    final List <DocumentReferenceType> aRefs = aCN.getAdditionalDocumentReference ();

    // A reference with a type is BT-18 (code 130 or 'ATS') or something else, and is not touched.
    // One with a description or an attachment can only be BG-24. The rest is ambiguous.
    final List <DocumentReferenceType> aAmbiguous = new ArrayList <> ();
    for (final DocumentReferenceType aRef : aRefs)
      if (hasNoDocumentType (aRef))
      {
        if (hasDescriptionOrAttachment (aRef))
          setSupportingDocumentType (aRef, aErrorList);
        else
          aAmbiguous.add (aRef);
      }

    for (final DocumentReferenceType aRef : aAmbiguous)
    {
      final String sID = aRef.getIDValue ();
      switch (getCreditNoteIDOnlyReferenceHandling ())
      {
        case DOCUMENT:
          setSupportingDocumentType (aRef, aErrorList);
          break;
        case PROJECT:
        {
          if (aAmbiguous.size () == 1 && _isIDOnly (aRef) && aCN.getProjectReference ().isEmpty ())
          {
            final ProjectReferenceType aProject = new ProjectReferenceType ();
            aProject.setID (aRef.getID ());
            aCN.getProjectReference ().add (aProject);
            aRefs.remove (aRef);
            reportApplied (aErrorList,
                           EUpdateRule.R5,
                           "cac:AdditionalDocumentReference",
                           "BT-11 '" + sID + "' moved to cac:ProjectReference");
          }
          else
            reportWarning (aErrorList,
                           EUpdateRule.R5,
                           "cac:AdditionalDocumentReference",
                           "The reference '" +
                                                              sID +
                                                              "' can be BT-11 or a supporting document and cannot be moved to cac:ProjectReference safely (there is more than one such reference, it carries more than an ID, or the Credit Note has a project reference already). It was left unchanged");
          break;
        }
        case KEEP:
        default:
          reportWarning (aErrorList,
                         EUpdateRule.R5,
                         "cac:AdditionalDocumentReference",
                         "The reference '" +
                                                            sID +
                                                            "' is either BT-11 (project reference) or a supporting document (BG-24) and the document does not tell which. It was left unchanged - see the option for ID-only references of Credit Notes");
          break;
      }
    }
  }

  @Override
  @Nullable
  public CreditNoteType update (@NonNull final Node aNode, @NonNull final ErrorList aErrorList)
  {
    ValueEnforcer.notNull (aNode, "Node");
    ValueEnforcer.notNull (aErrorList, "ErrorList");

    // Reading validates against the UBL 2.5 schema
    final CreditNoteType ret = UBL25Marshaller.creditNote ().setCollectErrors (aErrorList).read (aNode);
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
      reportApplied (aErrorList,
                     EUpdateRule.R1,
                     "cbc:ProfileID",
                     "BT-23 set to the configured value '" + getProfileID () + "'");
    }

    // R3
    moveNotes (ret.getNote (), ret.getAnnotation (), aErrorList);

    // R2
    if (moveBuyerReference (ret.getBuyerReference (), ret.getBuyerAssignedReference (), aErrorList))
      ret.setBuyerReference ((BuyerReferenceType) null);

    // R4
    _r4 (ret, aErrorList);

    // R6
    if (ret.getAccountingSupplierParty () != null)
      updateSellerTaxScheme (ret.getAccountingSupplierParty ().getParty (), aErrorList);

    // R5, R7
    _r5r7 (ret, aErrorList);

    // C1, C2
    checkPartyIdentifiers (aErrorList,
                           ret.getAccountingSupplierParty () == null ? null : ret.getAccountingSupplierParty ()
                                                                                 .getParty (),
                           ret.getAccountingCustomerParty () == null ? null : ret.getAccountingCustomerParty ()
                                                                                 .getParty (),
                           ret.getPayeeParty (),
                           ret.getDelivery ());
    checkAttachmentFilenames (ret.getAdditionalDocumentReference (), aErrorList);

    return ret;
  }
}
