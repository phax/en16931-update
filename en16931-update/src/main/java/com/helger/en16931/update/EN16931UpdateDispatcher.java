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

import java.io.File;
import java.io.OutputStream;
import java.io.Serializable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.annotation.WillClose;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.state.ESuccess;
import com.helger.cii.d25a.CIID25ACrossIndustryInvoiceTypeMarshaller;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931Edition;
import com.helger.en16931.basics.EEN16931SyntaxKind;
import com.helger.en16931.update.cii.CIID16BToCIID25AUpdater;
import com.helger.en16931.update.ubl.UBL21CreditNoteToUBL25CreditNoteUpdater;
import com.helger.en16931.update.ubl.UBL21InvoiceToUBL25InvoiceUpdater;
import com.helger.ubl25.UBL25Marshaller;
import com.helger.xml.XMLHelper;
import com.helger.xml.serialize.read.DOMReader;

import oasis.names.specification.ubl.schema.xsd.creditnote_25.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_25.InvoiceType;

/**
 * Updates a document to EN 16931:2026, choosing the syntax kind and the source edition
 * automatically.
 * <ul>
 * <li>The <b>syntax kind</b> - CII, UBL Invoice or UBL Credit Note - comes from the document
 * element.</li>
 * <li>The <b>source edition</b> comes from BT-24 (Specification identifier), see
 * {@link EEN16931Edition}. It cannot be taken from the XML namespaces, because all CII releases
 * and all UBL 2.x versions declare identical ones. An explicit source edition can be set, which
 * skips the detection.</li>
 * <li>A document that already is EN 16931:2026 is handled according to
 * {@link #getAlready2026Handling()}.</li>
 * </ul>
 * The settings of this class are handed over to whichever updater is used.
 *
 * @author Philip Helger
 * @since 1.0.0
 */
public class EN16931UpdateDispatcher extends AbstractEN16931UpdaterBase <EN16931UpdateDispatcher>
{
  private static final Logger LOGGER = LoggerFactory.getLogger (EN16931UpdateDispatcher.class);

  private EEN16931Edition m_eSourceEdition;
  private EAlready2026Handling m_eAlready2026Handling = EAlready2026Handling.DEFAULT;

  public EN16931UpdateDispatcher ()
  {}

  /**
   * @return The edition the source documents are treated as, or <code>null</code> if it is
   *         detected per document. The default is <code>null</code>.
   */
  @Nullable
  public final EEN16931Edition getSourceEdition ()
  {
    return m_eSourceEdition;
  }

  /**
   * Force the source edition instead of detecting it from BT-24. This is needed for the few
   * documents that carry no usable BT-24, like legacy ZUGFeRD ones.
   *
   * @param eSourceEdition
   *        The edition to use. May be <code>null</code> to detect it per document.
   * @return this for chaining
   */
  @NonNull
  public final EN16931UpdateDispatcher setSourceEdition (@Nullable final EEN16931Edition eSourceEdition)
  {
    m_eSourceEdition = eSourceEdition;
    return this;
  }

  @NonNull
  public final EAlready2026Handling getAlready2026Handling ()
  {
    return m_eAlready2026Handling;
  }

  @NonNull
  public final EN16931UpdateDispatcher setAlready2026Handling (@NonNull final EAlready2026Handling eAlready2026Handling)
  {
    ValueEnforcer.notNull (eAlready2026Handling, "Already2026Handling");
    m_eAlready2026Handling = eAlready2026Handling;
    return this;
  }

  /**
   * Create the updater for the provided syntax kind, initialised with the settings of this
   * dispatcher.
   *
   * @param eSyntaxKind
   *        The syntax kind. May not be <code>null</code>.
   * @return Never <code>null</code>.
   */
  @NonNull
  public AbstractEN16931Updater <?> createUpdater (@NonNull final EEN16931SyntaxKind eSyntaxKind)
  {
    ValueEnforcer.notNull (eSyntaxKind, "SyntaxKind");

    final AbstractEN16931Updater <?> ret = switch (eSyntaxKind)
    {
      case CII -> new CIID16BToCIID25AUpdater ();
      case UBL_INVOICE -> new UBL21InvoiceToUBL25InvoiceUpdater ();
      case UBL_CREDIT_NOTE -> new UBL21CreditNoteToUBL25CreditNoteUpdater ();
    };
    return copySettingsTo (ret);
  }

  @Nullable
  private static Element _getDocumentElement (@NonNull final Node aNode)
  {
    return aNode instanceof final Document aDoc ? aDoc.getDocumentElement ()
                                                : aNode instanceof final Element aElem ? aElem : null;
  }

  @Nullable
  private Serializable _readAs2026 (@NonNull final Node aNode,
                                    @NonNull final EEN16931SyntaxKind eSyntaxKind,
                                    @NonNull final ErrorList aErrorList)
  {
    return switch (eSyntaxKind)
    {
      case CII -> new CIID25ACrossIndustryInvoiceTypeMarshaller ().setCollectErrors (aErrorList).read (aNode);
      case UBL_INVOICE -> UBL25Marshaller.invoice ().setCollectErrors (aErrorList).read (aNode);
      case UBL_CREDIT_NOTE -> UBL25Marshaller.creditNote ().setCollectErrors (aErrorList).read (aNode);
    };
  }

  /**
   * Update a document to EN 16931:2026.
   *
   * @param aNode
   *        The document or its document element. May not be <code>null</code>.
   * @param aErrorList
   *        The error list to be filled. May not be <code>null</code>.
   * @return Never <code>null</code>. See {@link UpdateResult} on what success means.
   */
  @NonNull
  public UpdateResult update (@NonNull final Node aNode, @NonNull final ErrorList aErrorList)
  {
    ValueEnforcer.notNull (aNode, "Node");
    ValueEnforcer.notNull (aErrorList, "ErrorList");

    final EEN16931SyntaxKind eSyntaxKind = EEN16931SyntaxKind.getFromNodeOrNull (aNode);
    if (eSyntaxKind == null)
    {
      final Element aDocElement = _getDocumentElement (aNode);
      reportError (aErrorList,
                   "The XML document type " +
                            (aDocElement == null ? "(none)" : XMLHelper.getQName (aDocElement).toString ()) +
                            " is neither CII, nor a UBL Invoice, nor a UBL Credit Note");
      return UpdateResult.createFailed ();
    }

    EEN16931Edition eEdition = m_eSourceEdition;
    if (eEdition == null)
    {
      // Read BT-24 - the correct JAXB model is exactly what is not known yet
      final String sSpecID = EEN16931Edition.getSpecificationIdentifier (aNode);
      eEdition = EEN16931Edition.getFromSpecificationIdentifierOrNull (sSpecID);
      if (eEdition == null)
      {
        reportError (aErrorList,
                     sSpecID == null ? "Cannot determine the EN 16931 edition, because BT-24 (Specification identifier) is missing. Set the source edition explicitly."
                                     : "Cannot determine the EN 16931 edition from the BT-24 value '" +
                                       sSpecID +
                                       "'. Set the source edition explicitly.");
        return UpdateResult.createFailed ();
      }
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Detected EN 16931 edition " + eEdition.getID () + " and syntax " + eSyntaxKind.getID ());
    }

    if (eEdition == EEN16931Edition.EN2026)
    {
      switch (m_eAlready2026Handling)
      {
        case SKIP:
          reportWarning (aErrorList, "The document already is an EN 16931:2026 document - skipped");
          return UpdateResult.createSkipped ();
        case COPY:
        {
          final Serializable aDoc = _readAs2026 (aNode, eSyntaxKind, aErrorList);
          return aDoc == null ? UpdateResult.createFailed () : UpdateResult.createCopied (aDoc);
        }
        case FAIL:
          reportError (aErrorList, "The document already is an EN 16931:2026 document");
          return UpdateResult.createFailed ();
        default:
          throw new IllegalStateException ("Unsupported handling " + m_eAlready2026Handling);
      }
    }

    final Serializable aDoc = createUpdater (eSyntaxKind).update (aNode, aErrorList);
    return aDoc == null ? UpdateResult.createFailed () : UpdateResult.createUpdated (aDoc);
  }

  /**
   * Update a file to EN 16931:2026.
   *
   * @param aFile
   *        The file. May not be <code>null</code>.
   * @param aErrorList
   *        The error list to be filled. May not be <code>null</code>.
   * @return Never <code>null</code>.
   * @see #update(Node, ErrorList)
   */
  @NonNull
  public UpdateResult update (@NonNull final File aFile, @NonNull final ErrorList aErrorList)
  {
    ValueEnforcer.notNull (aFile, "File");
    ValueEnforcer.notNull (aErrorList, "ErrorList");

    final Document aDoc = aFile.isFile () ? DOMReader.readXMLDOM (aFile) : null;
    if (aDoc == null)
    {
      reportError (aErrorList, "Failed to read '" + aFile.getAbsolutePath () + "' as an XML document");
      return UpdateResult.createFailed ();
    }
    return update (aDoc, aErrorList);
  }

  /**
   * Serialize a document created by this class, picking the marshaller that matches its type.
   *
   * @param aDocument
   *        The CII D25A, UBL 2.5 Invoice or UBL 2.5 Credit Note to write. May not be
   *        <code>null</code>.
   * @param aOS
   *        The stream to write to. May not be <code>null</code>. Is closed by this method.
   * @param aErrorList
   *        The error list to be filled. May not be <code>null</code>.
   * @return {@link ESuccess#FAILURE} if the object is of none of the types, or if writing fails.
   *         Writing validates against the schema.
   */
  @NonNull
  public static ESuccess writeDocument (@NonNull final Serializable aDocument,
                                        @NonNull @WillClose final OutputStream aOS,
                                        @NonNull final ErrorList aErrorList)
  {
    ValueEnforcer.notNull (aDocument, "Document");
    ValueEnforcer.notNull (aOS, "OutputStream");
    ValueEnforcer.notNull (aErrorList, "ErrorList");

    if (aDocument instanceof final un.unece.uncefact.data.standard.cii.d25a.CrossIndustryInvoiceType aCII)
      return new CIID25ACrossIndustryInvoiceTypeMarshaller ().setFormattedOutput (true)
                                                             .setCollectErrors (aErrorList)
                                                             .write (aCII, aOS);
    if (aDocument instanceof final InvoiceType aInvoice)
      return UBL25Marshaller.invoice ().setFormattedOutput (true).setCollectErrors (aErrorList).write (aInvoice, aOS);
    if (aDocument instanceof final CreditNoteType aCreditNote)
      return UBL25Marshaller.creditNote ()
                            .setFormattedOutput (true)
                            .setCollectErrors (aErrorList)
                            .write (aCreditNote, aOS);

    reportError (aErrorList, "The object of type " + aDocument.getClass ().getName () + " is not an EN 16931:2026 document");
    return ESuccess.FAILURE;
  }
}
