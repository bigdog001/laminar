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

/*
 * Called as: bootThread(int ip, int pr, int rvm_sp)
 *
 * where ip - address in RVM to branch to
 *       pr - address of the processor object
 *       rvm_sp - address of the RVM stack
 */

#define NEED_ASSEMBLER_DECLARATIONS
#include "InterfaceDeclarations.h"

#if defined(__linux__) || (defined(__SVR4) && defined(__sun)) 
       .text
       .align 4
       .globl   bootThread   # external visibility
       bootThread:
#else
	.text
	.align 4
	.globl	_bootThread
       _bootThread:
#endif
	/*
	 * bp frame pointer on C stack of caller
	 * sp stack pointer on C stack
         */

	/*
         * save the C stack frame pointer and stack pointer on
	 * the C stack
	 */
        pushl %ebp		// stack[sp--] <- ebp
        movl %esp,%ebp		// ebp <- esp

	/*
	 * switch to the new RVM stack, using ebp to address
	 * the old stack to read parameters.
	 */
        movl 16(%ebp),%esp	// new_esp <- parameter sp value
	movl 12(%ebp),PR	// PR register <- paramater processos obj value
        movl  8(%ebp),%eax	// eax <- parameter RVM ip

	/*
	 * save the old stack address on the new stack
	 */
	pushl %ebp		// new_stack[new_esp--] <- ebp

        movl %esp,%ebp		// FP -> Caller's SP
	addl $12,%ebp		// FP -> FP slot

        call *%eax		// branch to RVM

        /*
	 * restore original C sp and bp and return
	 */
        popl %esp		// original_sp <- stack[sp++]
        popl %ebp		// original_bp <- stack[original_sp++]
        ret
