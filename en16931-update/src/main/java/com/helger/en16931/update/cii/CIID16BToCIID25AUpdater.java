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
import com.helger.base.string.StringHelper;
import com.helger.cii.d25a.CIID25ACrossIndustryInvoiceTypeMarshaller;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931Edition;
import com.helger.en16931.basics.EEN16931SyntaxKind;
import com.helger.en16931.update.AbstractEN16931Updater;
import com.helger.en16931.update.EUpdateRule;

import un.unece.uncefact.data.standard.cii.d25a.CrossIndustryInvoiceType;
import un.unece.uncefact.data.standard.cii.d25a.rabie.DocumentContextParameterType;
import un.unece.uncefact.data.standard.cii.d25a.rabie.ExchangedDocumentContextType;
import un.unece.uncefact.data.standard.cii.d25a.rabie.HeaderTradeAgreementType;
import un.unece.uncefact.data.standard.cii.d25a.rabie.HeaderTradeDeliveryType;
import un.unece.uncefact.data.standard.cii.d25a.rabie.HeaderTradeSettlementType;
import un.unece.uncefact.data.standard.cii.d25a.rabie.ReferencedDocumentType;
import un.unece.uncefact.data.standard.cii.d25a.rabie.SupplyChainTradeTransactionType;
import un.unece.uncefact.data.standard.cii.d25a.rabie.TradePartyType;
import un.unece.uncefact.data.standard.cii.d25a.udt.BinaryObjectType;
import un.unece.uncefact.data.standard.cii.d25a.udt.IDType;
import un.unece.uncefact.data.standard.cii.d25a.udt.TextType;

/**
 * Updates an EN 16931:2017 CII D16B document to an EN 16931:2026 CII D25A document. The document
 * is read with the D25A model, so everything it contains is preserved. The rules are documented
 * in <code>docs/update-rules.md</code>:
 * <ul>
 * <li>R1 - BT-24 (and an optional BT-23 override)</li>
 * <li>R2 - BT-10 <code>ram:BuyerReference</code> becomes <code>ram:BuyerReferenceID</code></li>
 * <li>C1 - party identifiers without a scheme identifier - reported, never changed</li>
 * <li>C2 - attached documents without a filename - reported, never changed</li>
 * </ul>
 * R3, R4, R5, R6, R7 and R9 do not apply to CII.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
public class CIID16BToCIID25AUpdater extends AbstractEN16931Updater <CIID16BToCIID25AUpdater>
{
  private static final String PATH_CONTEXT = "rsm:ExchangedDocumentContext";
  private static final String PATH_AGREEMENT = "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement";
  private static final String PATH_DELIVERY = "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeDelivery";
  private static final String PATH_SETTLEMENT = "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement";

  public CIID16BToCIID25AUpdater ()
  {}

  @NonNull
  public final EEN16931SyntaxKind getSyntaxKind ()
  {
    return EEN16931SyntaxKind.CII;
  }

  private void _r1 (@NonNull final CrossIndustryInvoiceType aCII, @NonNull final ErrorList aErrorList)
  {
    final String sID2017 = EEN16931Edition.EN2017.getSpecificationIdentifier ();
    final String sPath = PATH_CONTEXT + "/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID";

    ExchangedDocumentContextType aContext = aCII.getExchangedDocumentContext ();
    if (aContext == null)
    {
      aContext = new ExchangedDocumentContextType ();
      aCII.setExchangedDocumentContext (aContext);
    }

    // BT-24
    boolean bAny2017 = false;
    for (final DocumentContextParameterType aParam : aContext.getGuidelineSpecifiedDocumentContextParameter ())
      if (aParam.getIDValue () != null && aParam.getIDValue ().startsWith (sID2017))
        bAny2017 = true;
    if (getCustomizationID () != null || !bAny2017)
    {
      // Only the first parameter - this is a configured value, or the source edition was set explicitly
      final String sOld = aContext.hasGuidelineSpecifiedDocumentContextParameterEntries () ? aContext.getGuidelineSpecifiedDocumentContextParameterAtIndex (0)
                                                                                                  .getIDValue ()
                                                                                           : null;
      _setFirstParameter (aContext, true, getUpdatedSpecificationIdentifier (sOld, sPath, aErrorList));
    }
    else
    {
      for (final DocumentContextParameterType aParam : aContext.getGuidelineSpecifiedDocumentContextParameter ())
        if (aParam.getIDValue () != null && aParam.getIDValue ().startsWith (sID2017))
          aParam.setID (getUpdatedSpecificationIdentifier (aParam.getIDValue (), sPath, aErrorList));
    }

    // BT-23 - only if configured, it is the same in both editions
    if (getProfileID () != null)
    {
      _setFirstParameter (aContext, false, getProfileID ());
      reportApplied (aErrorList,
                     EUpdateRule.R1,
                     PATH_CONTEXT + "/ram:BusinessProcessSpecifiedDocumentContextParameter/ram:ID",
                     "BT-23 set to the configured value '" + getProfileID () + "'");
    }
  }

  private static void _setFirstParameter (@NonNull final ExchangedDocumentContextType aContext,
                                          final boolean bGuideline,
                                          @NonNull final String sValue)
  {
    final var aList = bGuideline ? aContext.getGuidelineSpecifiedDocumentContextParameter ()
                                 : aContext.getBusinessProcessSpecifiedDocumentContextParameter ();
    if (aList.isEmpty ())
      aList.add (new DocumentContextParameterType ());
    aList.get (0).setID (sValue);
  }

  private static void _r2 (@NonNull final HeaderTradeAgreementType aAgreement, @NonNull final ErrorList aErrorList)
  {
    final TextType aOld = aAgreement.getBuyerReference ();
    if (aOld == null)
      return;

    final String sValue = aOld.getValue ();
    boolean bAlreadyThere = false;
    for (final IDType aID : aAgreement.getBuyerReferenceID ())
      if (sValue != null && sValue.equals (aID.getValue ()))
        bAlreadyThere = true;
    if (!bAlreadyThere && StringHelper.isNotEmpty (sValue))
      aAgreement.addBuyerReferenceID (new IDType (sValue));
    aAgreement.setBuyerReference ((TextType) null);
    reportApplied (aErrorList,
                   EUpdateRule.R2,
                   PATH_AGREEMENT + "/ram:BuyerReference",
                   "BT-10 '" + sValue + "' moved to ram:BuyerReferenceID");
  }

  private void _checkParty (@NonNull final ErrorList aErrorList,
                            @NonNull final String sPath,
                            @Nullable final TradePartyType aParty,
                            @NonNull final String sBTID,
                            @Nullable final String sBTLegal)
  {
    if (aParty == null)
      return;

    // The 2017 binding allowed ram:ID or ram:GlobalID, the 2026 binding only maps ram:GlobalID
    for (final IDType aID : aParty.getID ())
      reportCheckFailure (aErrorList,
                          EUpdateRule.C1,
                          sPath + "/ram:ID",
                          sBTID +
                                "-1: the identifier '" +
                                aID.getValue () +
                                "' is in ram:ID, which is not part of EN 16931:2026 - only ram:GlobalID with a scheme identifier is. It was left unchanged");
    for (final IDType aID : aParty.getGlobalID ())
      if (StringHelper.isEmpty (aID.getSchemeID ()))
        reportCheckFailure (aErrorList,
                            EUpdateRule.C1,
                            sPath + "/ram:GlobalID",
                            sBTID + "-1: the identifier '" + aID.getValue () + "' has no scheme identifier, which is mandatory in EN 16931:2026. It was left unchanged");
    if (sBTLegal != null && aParty.getSpecifiedLegalOrganization () != null)
    {
      final IDType aID = aParty.getSpecifiedLegalOrganization ().getID ();
      if (aID != null && StringHelper.isEmpty (aID.getSchemeID ()))
        reportCheckFailure (aErrorList,
                            EUpdateRule.C1,
                            sPath + "/ram:SpecifiedLegalOrganization/ram:ID",
                            sBTLegal +
                                  "-1: the identifier '" +
                                  aID.getValue () +
                                  "' has no scheme identifier, which is mandatory in EN 16931:2026. It was left unchanged");
    }
  }

  private void _c2 (@NonNull final HeaderTradeAgreementType aAgreement, @NonNull final ErrorList aErrorList)
  {
    for (final ReferencedDocumentType aDoc : aAgreement.getAdditionalReferencedDocument ())
      for (final BinaryObjectType aBinary : aDoc.getAttachmentBinaryObject ())
        if (StringHelper.isEmpty (aBinary.getFilename ()))
          reportCheckFailure (aErrorList,
                              EUpdateRule.C2,
                              PATH_AGREEMENT + "/ram:AdditionalReferencedDocument/ram:AttachmentBinaryObject",
                              "BT-125-2: the attached document has no filename, which is mandatory in EN 16931:2026. It was left unchanged");
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

    _r1 (ret, aErrorList);

    final SupplyChainTradeTransactionType aTransaction = ret.getSupplyChainTradeTransaction ();
    if (aTransaction != null)
    {
      final HeaderTradeAgreementType aAgreement = aTransaction.getApplicableHeaderTradeAgreement ();
      if (aAgreement != null)
      {
        _r2 (aAgreement, aErrorList);
        _checkParty (aErrorList, PATH_AGREEMENT + "/ram:SellerTradeParty", aAgreement.getSellerTradeParty (), "BT-29", "BT-30");
        _checkParty (aErrorList, PATH_AGREEMENT + "/ram:BuyerTradeParty", aAgreement.getBuyerTradeParty (), "BT-46", "BT-47");
        _c2 (aAgreement, aErrorList);
      }
      final HeaderTradeSettlementType aSettlement = aTransaction.getApplicableHeaderTradeSettlement ();
      if (aSettlement != null)
        _checkParty (aErrorList, PATH_SETTLEMENT + "/ram:PayeeTradeParty", aSettlement.getPayeeTradeParty (), "BT-60", "BT-61");
      final HeaderTradeDeliveryType aDelivery = aTransaction.getApplicableHeaderTradeDelivery ();
      if (aDelivery != null)
        _checkParty (aErrorList, PATH_DELIVERY + "/ram:ShipToTradeParty", aDelivery.getShipToTradeParty (), "BT-71", null);
    }
    return ret;
  }
}
