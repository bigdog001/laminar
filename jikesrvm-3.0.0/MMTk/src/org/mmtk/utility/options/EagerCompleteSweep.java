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
 * Should we eagerly finish sweeping at the start of a collection
 */
public final class EagerCompleteSweep extends org.vmutil.options.BooleanOption {
  /**
   * Create the option.
   */
  public EagerCompleteSweep() {
    super(Options.set, "Eager Complete Sweep",
          "Should we eagerly finish sweeping at the start of a collection",
          false);
  }
}
