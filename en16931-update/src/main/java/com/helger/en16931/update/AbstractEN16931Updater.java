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
import java.io.Serializable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.en16931.basics.EEN16931SyntaxKind;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Base class of the updaters, one per syntax kind. An updater reads an EN 16931:2017 document with
 * the <b>2026</b> JAXB model - the namespaces are identical and the 2017 documents are valid
 * against the 2026 schema - and rewrites it in place. Reading validates against the schema.
 * <p>
 * The update is successful only if a non-<code>null</code> result is returned <b>and</b> the error
 * list contains no errors. Warnings and infos (every applied rule is an info) do not count.
 *
 * @author Philip Helger
 * @param <IMPLTYPE>
 *        The implementation type
 * @since 1.0.0
 */
public abstract class AbstractEN16931Updater <IMPLTYPE extends AbstractEN16931Updater <IMPLTYPE>> extends
                                             AbstractEN16931UpdaterBase <IMPLTYPE>
{
  protected AbstractEN16931Updater ()
  {}

  /**
   * @return The syntax kind this updater handles. Never <code>null</code>.
   */
  @NonNull
  public abstract EEN16931SyntaxKind getSyntaxKind ();

  /**
   * Update the provided document.
   *
   * @param aNode
   *        The EN 16931:2017 document or its document element. May not be <code>null</code>.
   * @param aErrorList
   *        The error list to be filled. May not be <code>null</code>.
   * @return <code>null</code> if the document cannot be read. Otherwise the EN 16931:2026
   *         document - a CII D25A <code>CrossIndustryInvoiceType</code>, a UBL 2.5
   *         <code>InvoiceType</code> or a UBL 2.5 <code>CreditNoteType</code>.
   */
  @Nullable
  public abstract Serializable update (@NonNull Node aNode, @NonNull ErrorList aErrorList);

  /**
   * Update the provided file.
   *
   * @param aFile
   *        The EN 16931:2017 file. May not be <code>null</code>.
   * @param aErrorList
   *        The error list to be filled. May not be <code>null</code>.
   * @return <code>null</code> if the file cannot be read.
   * @see #update(Node, ErrorList)
   */
  @Nullable
  public Serializable update (@NonNull final File aFile, @NonNull final ErrorList aErrorList)
  {
    ValueEnforcer.notNull (aFile, "File");
    ValueEnforcer.notNull (aErrorList, "ErrorList");

    final Document aDoc = aFile.isFile () ? DOMReader.readXMLDOM (aFile) : null;
    if (aDoc == null)
    {
      reportError (aErrorList, "Failed to read '" + aFile.getAbsolutePath () + "' as an XML document");
      return null;
    }
    return update (aDoc, aErrorList);
  }
}
