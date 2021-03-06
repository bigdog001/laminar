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
package org.jikesrvm.jni;

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.Processor;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.ObjectReference;

/**
 * A JNIEnvironment is created for each Java thread.
 */
public final class JNIEnvironment implements SizeConstants {

  /**
   * initial size for JNI refs, later grow as needed
   */
  protected static final int JNIREFS_ARRAY_LENGTH = 100;

  /**
   * sometimes we put stuff onto the jnirefs array bypassing the code
   * that makes sure that it does not overflow (evil assembly code in the
   * jni stubs that would be painful to fix).  So, we keep some space
   * between the max value in JNIRefsMax and the actual size of the
   * array.  How much is governed by this field.
   */
  protected static final int JNIREFS_FUDGE_LENGTH = 50;

  /**
   * This is the shared JNI function table used by native code
   * to invoke methods in @link{JNIFunctions}.
   */
  public static FunctionTable JNIFunctions;

  /**
   * For the PowerOpenABI we need a linkage triple instead of just
   * a function pointer.
   * This is an array of such triples that matches JNIFunctions.
   */
  private static AddressArray[] LinkageTriplets;

  /**
   * Stash the JTOC somewhere we can find it later
   * when we are making a C => Java transition.
   * We mainly need this for OSX/Linux but it is also nice to have on AIX.
   */
  @SuppressWarnings({"unused", "UnusedDeclaration"})
  // used by native code
  private final Address savedJTOC = VM.BuildForPowerPC ? Magic.getTocPointer() : Address.zero();

  /**
   * This is the pointer to the shared JNIFunction table.
   * When we invoke a native method, we adjust the pointer we
   * pass to the native code such that this field is at offset 0.
   * In other words, we turn a JNIEnvironment into a JNIEnv*
   * by handing the native code an interior pointer to
   * this object that points directly to this field.
   */
  @SuppressWarnings({"unused", "UnusedDeclaration"})
  // used by native code
  @Entrypoint
  private final Address externalJNIFunctions =
      VM.BuildForPowerOpenABI ? Magic.objectAsAddress(LinkageTriplets) : Magic.objectAsAddress(JNIFunctions);

  /**
   * For saving processor register on entry to native,
   * to be restored on JNI call from native
   */
  @Entrypoint
  @Untraced
  protected Processor savedPRreg;

  /**
   * true if the bottom stack frame is native,
   * such as thread for CreateJVM or AttachCurrentThread
   */
  protected boolean alwaysHasNativeFrame;

  /**
   * references passed to native code
   */
  @Entrypoint
  @Untraced
  public AddressArray JNIRefs;

  /**
   * address of current top ref in JNIRefs array
   */
  @Entrypoint
  public int JNIRefsTop;

  /**
   * offset of end (last entry) of JNIRefs array
   */
  @Entrypoint
  protected int JNIRefsMax;

  /**
   * previous frame boundary in JNIRefs array
   */
  @Entrypoint
  public int JNIRefsSavedFP;

  /**
   * Top java frame when in C frames on top of the stack
   */
  @Entrypoint
  protected Address JNITopJavaFP;

  /**
   * Currently pending exception (null if none)
   */
  @Entrypoint
  @Untraced
  protected Throwable pendingException;

  /**
   * We allocate JNIEnvironments in the immortal heap (so we
   * can hand them directly to C code).  Therefore, we must do some
   * kind of pooling of JNIEnvironment instances.
   * This is the free list of unused instances.
   */
  protected JNIEnvironment next;

  /**
   * Pool of available JNIEnvironments.
   */
  protected static JNIEnvironment pool;

  /**
   * Initialize a thread specific JNI environment.
   */
  protected void initializeState() {
    JNIRefs = AddressArray.create(JNIREFS_ARRAY_LENGTH + JNIREFS_FUDGE_LENGTH);
    JNIRefsTop = 0;
    JNIRefsSavedFP = 0;
    JNIRefsMax = (JNIREFS_ARRAY_LENGTH - 1) << LOG_BYTES_IN_ADDRESS;
    alwaysHasNativeFrame = false;
  }

  /*
   * Allocation and pooling
   */

  /**
   * Create a thread specific JNI environment.
   */
  public static synchronized JNIEnvironment allocateEnvironment() {
    JNIEnvironment env;
    if (pool != null) {
      env = pool;
      pool = pool.next;
    } else {
      env = new JNIEnvironment();
    }
    env.initializeState();
    return env;
  }

  /**
   * Return a thread-specific JNI environment; must be called as part of
   * terminating a thread that has a JNI environment allocated to it.
   * @param env the JNIEnvironment to deallocate
   */
  @Unpreemptible("Deallocate environment but may contend with environment being allocated")
  public static synchronized void deallocateEnvironment(JNIEnvironment env) {
    env.next = pool;
    pool = env;
  }

  /*
   * accessor methods
   */
  @Uninterruptible
  public boolean hasNativeStackFrame() {
    return alwaysHasNativeFrame || JNIRefsTop != 0;
  }

  @Uninterruptible
  public Address topJavaFP() {
    return JNITopJavaFP;
  }

  @Uninterruptible
  public AddressArray refsArray() {
    return JNIRefs;
  }

  @Uninterruptible
  public int refsTop() {
    return JNIRefsTop;
  }

  @Uninterruptible
  public int savedRefsFP() {
    return JNIRefsSavedFP;
  }

  /**
   * Push a reference onto thread local JNIRefs stack.
   * To be used by JNI functions when returning a reference
   * back to JNI native C code.
   * @param ref the object to put on stack
   * @return offset of entry in JNIRefs stack
   */
  public int pushJNIRef(Object ref) {
    if (ref == null) {
      return 0;
    }

    if (VM.VerifyAssertions) {
      VM._assert(MemoryManager.validRef(ObjectReference.fromObject(ref)));
    }

    if ((JNIRefsTop >>> LOG_BYTES_IN_ADDRESS) >= JNIRefs.length()) {
      VM.sysFail("unchecked pushes exceeded fudge length!");
    }

    JNIRefsTop += BYTES_IN_ADDRESS;

    if (JNIRefsTop >= JNIRefsMax) {
      JNIRefsMax *= 2;
      AddressArray newrefs = AddressArray.create((JNIRefsMax >>> LOG_BYTES_IN_ADDRESS) + JNIREFS_FUDGE_LENGTH);
      for (int i = 0; i < JNIRefs.length(); i++) {
        newrefs.set(i, JNIRefs.get(i));
      }
      JNIRefs = newrefs;
    }

    JNIRefs.set(JNIRefsTop >>> LOG_BYTES_IN_ADDRESS, Magic.objectAsAddress(ref));
    return JNIRefsTop;
  }

  /**
   * Get a reference from the JNIRefs stack.
   * @param offset in JNIRefs stack
   * @return reference at that offset
   */
  public Object getJNIRef(int offset) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP, ");
      VM.sysWrite(offset);
      VM.sysWrite("(top is ");
      VM.sysWrite(JNIRefsTop);
      VM.sysWrite(")\n");
      return null;
    }
    if (offset < 0) {
      return JNIGlobalRefTable.ref(offset);
    } else {
      return Magic.addressAsObject(JNIRefs.get(offset >>> LOG_BYTES_IN_ADDRESS));
    }
  }

  /**
   * Remove a reference from the JNIRefs stack.
   * @param offset in JNIRefs stack
   */
  public void deleteJNIRef(int offset) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP, ");
      VM.sysWrite(offset);
      VM.sysWrite("(top is ");
      VM.sysWrite(JNIRefsTop);
      VM.sysWrite(")\n");
    }

    JNIRefs.set(offset >>> LOG_BYTES_IN_ADDRESS, Address.zero());

    if (offset == JNIRefsTop) JNIRefsTop -= BYTES_IN_ADDRESS;
  }

  /**
   * Dump the JNIRefs stack to the sysWrite stream
   */
  @Uninterruptible
  public void dumpJniRefsStack() {
    int jniRefOffset = JNIRefsTop;
    VM.sysWrite("\n* * dump of JNIEnvironment JniRefs Stack * *\n");
    VM.sysWrite("* JNIRefs = ");
    VM.sysWrite(Magic.objectAsAddress(JNIRefs));
    VM.sysWrite(" * JNIRefsTop = ");
    VM.sysWrite(JNIRefsTop);
    VM.sysWrite(" * JNIRefsSavedFP = ");
    VM.sysWrite(JNIRefsSavedFP);
    VM.sysWrite(".\n*\n");
    while (jniRefOffset >= 0) {
      VM.sysWrite(jniRefOffset);
      VM.sysWrite(" ");
      VM.sysWrite(Magic.objectAsAddress(JNIRefs).plus(jniRefOffset));
      VM.sysWrite(" ");
      MemoryManager.dumpRef(JNIRefs.get(jniRefOffset >>> LOG_BYTES_IN_ADDRESS).toObjectReference());
      jniRefOffset -= BYTES_IN_ADDRESS;
    }
    VM.sysWrite("\n* * end of dump * *\n");
  }

  /**
   * Record an exception as pending so that it will be delivered on the return
   * to the Java caller;  clear the exception by recording null
   * @param e  An exception or error
   */
  public void recordException(Throwable e) {
    // don't overwrite the first exception except to clear it
    if (pendingException == null || e == null) {
      pendingException = e;
    }
  }

  /**
   * @return the pending exception
   */
  public Throwable getException() {
    return pendingException;
  }

  /**
   * Initialize the array of JNI functions.
   * This function is called during bootimage writing.
   */
  public static void initFunctionTable(FunctionTable functions) {
    JNIFunctions = functions;
    if (VM.BuildForPowerOpenABI) {
      // Allocate the linkage triplets in the bootimage too (so they won't move)
      LinkageTriplets = new AddressArray[functions.length()];
      for (int i = 0; i < functions.length(); i++) {
        LinkageTriplets[i] = AddressArray.create(3);
      }
    }
  }

  /**
   * Initialization required during VM booting; only does something if
   * we are on a platform that needs linkage triplets.
   */
  public static void boot() {
    if (VM.BuildForPowerOpenABI) {
      // fill in the TOC and IP entries for each linkage triplet
      for (int i = 0; i < JNIFunctions.length(); i++) {
        AddressArray triplet = LinkageTriplets[i];
        triplet.set(1, Magic.getTocPointer());
        triplet.set(0, Magic.objectAsAddress(JNIFunctions.get(i)));
      }
    }
  }
}
