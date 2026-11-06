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
package com.helger.en16931.update.cli;

import java.util.concurrent.Callable;

import com.helger.en16931.update.UpdateVersion;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main command line client. The options are added with the update logic (plan item A11).
 *
 * @author Philip Helger
 */
@Command (description = "Updater of EN 16931:2017 invoices to EN 16931:2026",
          name = "EN16931Update",
          mixinStandardHelpOptions = true,
          separator = " ")
public class EN16931UpdateCLI implements Callable <Integer>
{
  public Integer call ()
  {
    System.out.println ("en16931-update " + UpdateVersion.BUILD_VERSION);
    return Integer.valueOf (0);
  }

  public static void main (final String [] args)
  {
    System.exit (new CommandLine (new EN16931UpdateCLI ()).execute (args));
  }
}
