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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.string.StringHelper;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931Edition;
import com.helger.en16931.update.AbstractEN16931Updater;
import com.helger.en16931.update.EUpdateRule;

import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.AnnotationType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.BuyerAssignedReferenceType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.DeliveryType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.DocumentReferenceType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.PartyIdentificationType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.PartyLegalEntityType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.PartyTaxSchemeType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_25.PartyType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_25.AnnotationContentType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_25.BuyerReferenceType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_25.DocumentTypeCodeType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_25.NoteType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_25.SubjectCodeType;

/**
 * Base class of the UBL 2.1 to UBL 2.5 updaters. It holds the rules that work the same on an
 * Invoice and on a Credit Note. The two document types are unrelated Java classes, so the rules
 * work on the parts they have in common.
 *
 * @author Philip Helger
 * @param <IMPLTYPE>
 *        The implementation type
 * @since 1.0.0
 */
public abstract class AbstractUBL21ToUBL25Updater <IMPLTYPE extends AbstractUBL21ToUBL25Updater <IMPLTYPE>> extends
                                                  AbstractEN16931Updater <IMPLTYPE>
{
  /** The EN 16931:2026 UBL version - R9 */
  protected static final String UBL_VERSION_2026 = EEN16931Edition.EN2026.getUBLSyntaxVersion ();
  /** The EN 16931:2017 UBL version - R9 */
  protected static final String UBL_VERSION_2017 = EEN16931Edition.EN2017.getUBLSyntaxVersion ();

  /** The only value of the BT-122-1-1 list identifier in the EN core */
  protected static final String DOCUMENT_TYPE_CODE_LIST_ID = "1001";
  /** BT-122-1 - "Related document" */
  protected static final String DOCUMENT_TYPE_CODE_SUPPORTING_DOCUMENT = "916";
  /** BT-32-2 in EN 16931:2026 */
  protected static final String TAX_SCHEME_ID_NATIONAL = "LOC";

  // BT-21 is part of the text in the 2017 binding: "#code#text"
  private static final Pattern NOTE_WITH_CODE = Pattern.compile ("^#([^#\\r\\n]+)#(.*)$", Pattern.DOTALL);

  protected AbstractUBL21ToUBL25Updater ()
  {}

  /**
   * Rule R9: the UBL version identifier.
   *
   * @param sOld
   *        The value of <code>cbc:UBLVersionID</code>. May be <code>null</code>.
   * @param aErrorList
   *        The error list. May not be <code>null</code>.
   * @return The value to write, or <code>null</code> if the element is to be left alone.
   */
  @Nullable
  protected static String getUpdatedUBLVersionID (@Nullable final String sOld, @NonNull final ErrorList aErrorList)
  {
    if (sOld == null)
      return null;
    if (sOld.trim ().equals (UBL_VERSION_2017))
    {
      reportApplied (aErrorList, EUpdateRule.R9, "cbc:UBLVersionID", "UBL version '" + sOld + "' changed to '" + UBL_VERSION_2026 + "'");
      return UBL_VERSION_2026;
    }
    if (!sOld.trim ().equals (UBL_VERSION_2026))
      reportWarning (aErrorList,
                     EUpdateRule.R9,
                     "cbc:UBLVersionID",
                     "UBL version '" + sOld + "' is neither " + UBL_VERSION_2017 + " nor " + UBL_VERSION_2026 + " and was left unchanged");
    return null;
  }

  /**
   * Rule R2: BT-10.
   *
   * @param aOld
   *        The <code>cbc:BuyerReference</code> of the source document. May be <code>null</code>.
   * @param aTarget
   *        The <code>cac:BuyerAssignedReference</code> list of the document. May not be
   *        <code>null</code>.
   * @param aErrorList
   *        The error list. May not be <code>null</code>.
   * @return <code>true</code> if the old element was moved and has to be removed from the document.
   */
  protected static boolean moveBuyerReference (@Nullable final BuyerReferenceType aOld,
                                               @NonNull final List <BuyerAssignedReferenceType> aTarget,
                                               @NonNull final ErrorList aErrorList)
  {
    if (aOld == null)
      return false;

    final String sValue = aOld.getValue ();
    boolean bAlreadyThere = false;
    for (final BuyerAssignedReferenceType aRef : aTarget)
      for (final BuyerReferenceType aExisting : aRef.getBuyerReference ())
        if (sValue != null && sValue.equals (aExisting.getValue ()))
          bAlreadyThere = true;
    if (!bAlreadyThere && StringHelper.isNotEmpty (sValue))
    {
      final BuyerAssignedReferenceType aRef = new BuyerAssignedReferenceType ();
      aRef.addBuyerReference (new BuyerReferenceType (sValue));
      aTarget.add (aRef);
    }
    reportApplied (aErrorList, EUpdateRule.R2, "cbc:BuyerReference", "BT-10 '" + sValue + "' moved to cac:BuyerAssignedReference/cbc:BuyerReference");
    return true;
  }

  /**
   * Rule R3: all header notes of the 2017 binding are BG-1 and become annotations. A leading
   * <code>#code#</code> is BT-21, the rest is BT-22. The notes are removed from the list.
   *
   * @param aNotes
   *        The <code>cbc:Note</code> list of the document. May not be <code>null</code>.
   * @param aAnnotations
   *        The <code>cac:Annotation</code> list of the document. May not be <code>null</code>.
   * @param aErrorList
   *        The error list. May not be <code>null</code>.
   */
  protected static void moveNotes (@NonNull final List <NoteType> aNotes,
                                   @NonNull final List <AnnotationType> aAnnotations,
                                   @NonNull final ErrorList aErrorList)
  {
    for (final NoteType aNote : aNotes)
    {
      final String sValue = aNote.getValue ();
      String sCode = null;
      String sText = sValue;
      if (sValue != null)
      {
        final Matcher aMatcher = NOTE_WITH_CODE.matcher (sValue);
        if (aMatcher.matches ())
        {
          sCode = aMatcher.group (1);
          sText = aMatcher.group (2);
        }
      }

      final AnnotationType aAnnotation = new AnnotationType ();
      if (sCode != null)
        aAnnotation.setSubjectCode (new SubjectCodeType (sCode));
      if (StringHelper.isNotEmpty (sText))
        aAnnotation.addAnnotationContent (new AnnotationContentType (sText));
      aAnnotations.add (aAnnotation);
      reportApplied (aErrorList,
                     EUpdateRule.R3,
                     "cbc:Note",
                     sCode == null ? "BT-22 moved to cac:Annotation/cbc:AnnotationContent"
                                   : "BT-21 '" + sCode + "' and BT-22 moved to cac:Annotation");
    }
    aNotes.clear ();
  }

  /**
   * Rule R6: the national tax scheme of the Seller is <code>LOC</code> in EN 16931:2026, where any
   * code other than <code>VAT</code> was allowed.
   *
   * @param aSeller
   *        The Seller party. May be <code>null</code>.
   * @param aErrorList
   *        The error list. May not be <code>null</code>.
   */
  protected static void updateSellerTaxScheme (@Nullable final PartyType aSeller, @NonNull final ErrorList aErrorList)
  {
    if (aSeller == null)
      return;
    for (final PartyTaxSchemeType aPTS : aSeller.getPartyTaxScheme ())
    {
      if (aPTS.getTaxScheme () == null || aPTS.getTaxScheme ().getID () == null)
        continue;
      final String sOld = aPTS.getTaxScheme ().getID ().getValue ();
      if (StringHelper.isNotEmpty (sOld) && !"VAT".equals (sOld) && !TAX_SCHEME_ID_NATIONAL.equals (sOld))
      {
        aPTS.getTaxScheme ().getID ().setValue (TAX_SCHEME_ID_NATIONAL);
        reportApplied (aErrorList,
                       EUpdateRule.R6,
                       "cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme/cac:TaxScheme/cbc:ID",
                       "BT-32-2 '" + sOld + "' changed to '" + TAX_SCHEME_ID_NATIONAL + "'");
      }
    }
  }

  /**
   * @param aRef
   *        The document reference. May not be <code>null</code>.
   * @return <code>true</code> if the reference has neither a <code>cbc:DocumentTypeCode</code> nor
   *         a <code>cbc:DocumentType</code>.
   */
  protected static boolean hasNoDocumentType (@NonNull final DocumentReferenceType aRef)
  {
    return aRef.getDocumentTypeCode () == null && aRef.getDocumentType ().isEmpty ();
  }

  /**
   * Rule R7: BG-24 gets the fixed document type code <code>916</code>.
   *
   * @param aRef
   *        The <code>cac:AdditionalDocumentReference</code>. May not be <code>null</code>.
   * @param aErrorList
   *        The error list. May not be <code>null</code>.
   */
  protected static void setSupportingDocumentType (@NonNull final DocumentReferenceType aRef,
                                                   @NonNull final ErrorList aErrorList)
  {
    final DocumentTypeCodeType aCode = new DocumentTypeCodeType (DOCUMENT_TYPE_CODE_SUPPORTING_DOCUMENT);
    aCode.setListID (DOCUMENT_TYPE_CODE_LIST_ID);
    aRef.setDocumentTypeCode (aCode);
    reportApplied (aErrorList,
                   EUpdateRule.R7,
                   "cac:AdditionalDocumentReference/cbc:DocumentTypeCode",
                   "BT-122-1 '" + DOCUMENT_TYPE_CODE_SUPPORTING_DOCUMENT + "' (listID " + DOCUMENT_TYPE_CODE_LIST_ID + ") added to the supporting document '" + (aRef.getID () == null ? "" : aRef.getID ().getValue ()) + "'");
  }

  private void _checkIdentifiers (@NonNull final ErrorList aErrorList,
                                  @NonNull final String sPath,
                                  @Nullable final PartyType aParty,
                                  @NonNull final String sBTID,
                                  @Nullable final String sBTLegal)
  {
    if (aParty == null)
      return;
    for (final PartyIdentificationType aPI : aParty.getPartyIdentification ())
      if (aPI.getID () != null && StringHelper.isEmpty (aPI.getID ().getSchemeID ()))
        reportCheckFailure (aErrorList,
                            EUpdateRule.C1,
                            sPath + "/cac:PartyIdentification/cbc:ID",
                            sBTID + "-1: the identifier '" + aPI.getID ().getValue () + "' has no scheme identifier, which is mandatory in EN 16931:2026. It was left unchanged");
    if (sBTLegal != null)
      for (final PartyLegalEntityType aPLE : aParty.getPartyLegalEntity ())
        if (aPLE.getCompanyID () != null && StringHelper.isEmpty (aPLE.getCompanyID ().getSchemeID ()))
          reportCheckFailure (aErrorList,
                              EUpdateRule.C1,
                              sPath + "/cac:PartyLegalEntity/cbc:CompanyID",
                              sBTLegal + "-1: the identifier '" + aPLE.getCompanyID ().getValue () + "' has no scheme identifier, which is mandatory in EN 16931:2026. It was left unchanged");
  }

  /**
   * Check C1 on the Seller, Buyer, Payee and the delivery location.
   *
   * @param aErrorList
   *        The error list. May not be <code>null</code>.
   * @param aSeller
   *        BT-29, BT-30. May be <code>null</code>.
   * @param aBuyer
   *        BT-46, BT-47. May be <code>null</code>.
   * @param aPayee
   *        BT-60, BT-61. May be <code>null</code>.
   * @param aDeliveries
   *        BT-71. May not be <code>null</code>.
   */
  protected final void checkPartyIdentifiers (@NonNull final ErrorList aErrorList,
                                              @Nullable final PartyType aSeller,
                                              @Nullable final PartyType aBuyer,
                                              @Nullable final PartyType aPayee,
                                              @NonNull final List <DeliveryType> aDeliveries)
  {
    _checkIdentifiers (aErrorList, "cac:AccountingSupplierParty/cac:Party", aSeller, "BT-29", "BT-30");
    _checkIdentifiers (aErrorList, "cac:AccountingCustomerParty/cac:Party", aBuyer, "BT-46", "BT-47");
    _checkIdentifiers (aErrorList, "cac:PayeeParty", aPayee, "BT-60", "BT-61");
    for (final DeliveryType aDelivery : aDeliveries)
      if (aDelivery.getDeliveryLocation () != null &&
          aDelivery.getDeliveryLocation ().getID () != null &&
          StringHelper.isEmpty (aDelivery.getDeliveryLocation ().getID ().getSchemeID ()))
        reportCheckFailure (aErrorList,
                            EUpdateRule.C1,
                            "cac:Delivery/cac:DeliveryLocation/cbc:ID",
                            "BT-71-1: the identifier '" + aDelivery.getDeliveryLocation ().getID ().getValue () + "' has no scheme identifier, which is mandatory in EN 16931:2026. It was left unchanged");
  }

  /**
   * Check C2 on all supporting documents.
   *
   * @param aRefs
   *        The <code>cac:AdditionalDocumentReference</code> list. May not be <code>null</code>.
   * @param aErrorList
   *        The error list. May not be <code>null</code>.
   */
  protected final void checkAttachmentFilenames (@NonNull final List <DocumentReferenceType> aRefs,
                                                 @NonNull final ErrorList aErrorList)
  {
    for (final DocumentReferenceType aRef : aRefs)
      if (aRef.getAttachment () != null &&
          aRef.getAttachment ().getEmbeddedDocumentBinaryObject () != null &&
          StringHelper.isEmpty (aRef.getAttachment ().getEmbeddedDocumentBinaryObject ().getFilename ()))
        reportCheckFailure (aErrorList,
                            EUpdateRule.C2,
                            "cac:AdditionalDocumentReference/cac:Attachment/cbc:EmbeddedDocumentBinaryObject",
                            "BT-125-2: the attached document has no filename, which is mandatory in EN 16931:2026. It was left unchanged");
  }
}
