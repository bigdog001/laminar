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
package java.lang.ref;

import org.jikesrvm.mm.mminterface.MemoryManager;

/**
 * Implementation of java.lang.ref.SoftReference for JikesRVM.
 */
public class SoftReference<T> extends Reference<T> {

  public SoftReference(T referent) {
    super(referent);
    MemoryManager.addSoftReference(this,referent);
  }

  public SoftReference(T referent, ReferenceQueue<T> q) {
    super(referent, q);
    MemoryManager.addSoftReference(this, referent);
  }
}
