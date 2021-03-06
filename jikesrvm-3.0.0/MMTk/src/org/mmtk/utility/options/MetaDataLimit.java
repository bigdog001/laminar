/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.options;

/**
 * Provide a bound on how much metadata is allowed before a GC is triggered.
 */
public final class MetaDataLimit extends org.vmutil.options.PagesOption {
  /**
   * Create the option.
   */
  public MetaDataLimit() {
    super(Options.set, "Meta Data Limit",
          "Trigger a GC if the meta data volume grows to this limit",
          4096);
  }
}
