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

import static com.helger.en16931.update.UpdateTestHelper.listXML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.io.resource.FileSystemResource;
import com.helger.phive.api.execute.ValidationExecutionManager;
import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.api.result.ValidationResult;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.phive.api.validity.IValidityDeterminator;
import com.helger.phive.en16931.EN16931Validation;
import com.helger.phive.xml.source.IValidationSourceXML;
import com.helger.phive.xml.source.ValidationSourceXML;

/**
 * Validates the <b>input</b> corpus against the EN 16931:2017 Schematron (artefacts 1.3.17 as
 * shipped with phive-rules-en16931). Only the inputs are checked: the XSD step of those rules is the
 * 2017 one, so an updated document fails it by design (it has elements the 2017 schema does not
 * know), and there is no 2026 validation artefact yet.
 * <p>
 * The numbers are pinned. If they change, the Schematron version was probably updated - look at
 * which files changed before adapting them. The invalid CII files are old test inputs of the sibling
 * projects (e.g. BR-53 in XRechnung 1.2.2, CII-SR-461, BR-CL-06); the updater preserves them as
 * they are, it does not repair them.
 *
 * @author Philip Helger
 */
public final class CorpusConformanceTest
{
  @Test
  public void testInputCorpusAgainst2017Schematron () throws Exception
  {
    final ValidationExecutorSetRegistry <IValidationSourceXML> aRegistry = new ValidationExecutorSetRegistry <> ();
    EN16931Validation.initEN16931 (aRegistry);
    final DVRCoordinate aCII = EN16931Validation.VID_CII_1317.getWithVersionLatestRelease ();
    final DVRCoordinate aInvoice = EN16931Validation.VID_UBL_INVOICE_1317.getWithVersionLatestRelease ();
    final DVRCoordinate aCreditNote = EN16931Validation.VID_UBL_CREDIT_NOTE_1317.getWithVersionLatestRelease ();

    final List <File> aFiles = new ArrayList <> ();
    aFiles.addAll (listXML ("external/cii"));
    aFiles.addAll (listXML ("external/ubl21"));
    aFiles.addAll (listXML ("edge/ubl21"));

    int nCIIValid = 0;
    int nCIITotal = 0;
    int nUBLValid = 0;
    int nUBLTotal = 0;
    for (final File aFile : aFiles)
    {
      final String sContent = Files.readString (aFile.toPath (), StandardCharsets.UTF_8);
      final boolean bCII = sContent.contains ("CrossIndustryInvoice");
      final DVRCoordinate aVID = sContent.contains ("<CreditNote") ? aCreditNote : sContent.contains ("<Invoice") ? aInvoice : aCII;
      final ValidationResultList aList = ValidationExecutionManager.executeValidation (IValidityDeterminator.createDefault (),
                                                                                      aRegistry.getOfID (aVID),
                                                                                      ValidationSourceXML.create (new FileSystemResource (aFile)));
      assertNotNull (aList);
      boolean bValid = true;
      for (final ValidationResult aResult : aList)
        if (!aResult.getErrorList ().isEmpty ())
          bValid = false;
      if (bCII)
      {
        nCIITotal++;
        if (bValid)
          nCIIValid++;
      }
      else
      {
        nUBLTotal++;
        if (bValid)
          nUBLValid++;
      }
    }
    assertEquals (103, nCIITotal);
    assertEquals (18, nUBLTotal);
    assertEquals ("All UBL inputs are valid", nUBLTotal, nUBLValid);
    assertEquals ("Valid CII inputs", 70, nCIIValid);
    assertTrue (nCIIValid + nUBLValid == 88);
  }
}
