/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.internal.foreign.abi.fallback;

import jdk.internal.foreign.abi.SharedUtils;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

class LibFallback {
    static final boolean SUPPORTED = tryLoadLibrary();

    private static boolean tryLoadLibrary() {
        try {
            System.loadLibrary("fallbackLinker");
        } catch (UnsatisfiedLinkError ule) {
            return false;
        }
        init();
        return true;
    }

    static final int DEFAULT_ABI = ffi_default_abi();

    static final MemorySegment UINT8_TYPE = MemorySegment.ofAddress(ffi_type_uint8());
    static final MemorySegment SINT8_TYPE = MemorySegment.ofAddress(ffi_type_sint8());
    static final MemorySegment UINT16_TYPE = MemorySegment.ofAddress(ffi_type_uint16());
    static final MemorySegment SINT16_TYPE = MemorySegment.ofAddress(ffi_type_sint16());
    static final MemorySegment SINT32_TYPE = MemorySegment.ofAddress(ffi_type_sint32());
    static final MemorySegment SINT64_TYPE = MemorySegment.ofAddress(ffi_type_sint64());
    static final MemorySegment FLOAT_TYPE = MemorySegment.ofAddress(ffi_type_float());
    static final MemorySegment DOUBLE_TYPE = MemorySegment.ofAddress(ffi_type_double());
    static final MemorySegment POINTER_TYPE = MemorySegment.ofAddress(ffi_type_pointer());

    static final MemorySegment VOID_TYPE = MemorySegment.ofAddress(ffi_type_void());
    static final short STRUCT_TAG = ffi_type_struct();
    private static final long SIZEOF_CIF = sizeofCif();

    private static final MethodType UPCALL_TARGET_TYPE = MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class);

    /**
     * Do a libffi based downcall. This method wraps the {@code ffi_call} function
     *
     * @param cif a pointer to a {@code ffi_cif} struct
     * @param target the address of the target function
     * @param retPtr a pointer to a buffer into which the return value shall be written, or {@code null} if the target
     *               function does not return a value
     * @param argPtrs a pointer to an array of pointers, which each point to an argument value
     * @param capturedState a pointer to a buffer into which captured state is written, or {@code null} if no state is
     *                      to be captured
     * @param capturedStateMask the bit mask indicating which state to capture
     *
     * @see jdk.internal.foreign.abi.CapturableState
     */
    static void doDowncall(MemorySegment cif, MemorySegment target, MemorySegment retPtr, MemorySegment argPtrs,
                                  MemorySegment capturedState, int capturedStateMask) {
            doDowncall(cif.address(), target.address(),
                    retPtr == null ? 0 : retPtr.address(), argPtrs.address(),
                    capturedState == null ? 0 : capturedState.address(), capturedStateMask);
    }

    /**
     * Wrapper for {@code ffi_prep_cif}
     *
     * @param returnType a pointer to an @{code ffi_type} describing the return type
     * @param numArgs the number of arguments
     * @param paramTypes a pointer to an array of pointers, which each point to an {@code ffi_type} describing a
     *                parameter type
     * @param abi the abi to be used
     * @param scope the scope into which to allocate the returned {@code ffi_cif} struct
     * @return a pointer to a prepared {@code ffi_cif} struct
     *
     * @throws IllegalStateException if the call to {@code ffi_prep_cif} returns a non-zero status code
     */
    static MemorySegment prepCif(MemorySegment returnType, int numArgs, MemorySegment paramTypes, FFIABI abi,
                                         Arena scope) throws IllegalStateException {
        MemorySegment cif = scope.allocate(SIZEOF_CIF);
        checkStatus(ffi_prep_cif(cif.address(), abi.value(), numArgs, returnType.address(), paramTypes.address()));
        return cif;
    }

    /**
     * Create an upcallStub-style closure. This method wraps the {@code ffi_closure_alloc}
     * and {@code ffi_prep_closure_loc} functions.
     * <p>
     * The closure will end up calling into {@link #doUpcall(long, long, MethodHandle)}
     * <p>
     * The target method handle should have the type {@code (MemorySegment, MemorySegment) -> void}. The first
     * argument is a pointer to the buffer into which the native return value should be written. The second argument
     * is a pointer to an array of pointers, which each point to a native argument value.
     *
     * @param cif a pointer to a {@code ffi_cif} struct
     * @param target a method handle that points to the target function
     * @param arena the scope to which to attach the created upcall stub
     * @return the created upcall stub
     *
     * @throws IllegalStateException if the call to {@code ffi_prep_closure_loc} returns a non-zero status code
     * @throws IllegalArgumentException if {@code target} does not have the right type
     */
    static MemorySegment createClosure(MemorySegment cif, MethodHandle target,
                                       Thread.UncaughtExceptionHandler handler, Arena arena)
            throws IllegalStateException, IllegalArgumentException {
        if (target.type() != UPCALL_TARGET_TYPE) {
            throw new IllegalArgumentException("Target handle has wrong type: " + target.type() + " != " + UPCALL_TARGET_TYPE);
        }

        long[] ptrs = new long[3];
        UpcallData upcallData = new UpcallData(target, handler);
        checkStatus(createClosure(cif.address(), upcallData, ptrs));
        long closurePtr = ptrs[0];
        long execPtr = ptrs[1];
        long globalTarget = ptrs[2];

        return MemorySegment.ofAddress(execPtr).reinterpret(arena.scope(), unused -> freeClosure(closurePtr, globalTarget));
    }

    private record UpcallData(MethodHandle target, Thread.UncaughtExceptionHandler handler) {}

    // the target function for a closure call
    private static void doUpcall(long retPtr, long argPtrs, UpcallData data) {
        try {
            data.target().invokeExact(MemorySegment.ofAddress(retPtr), MemorySegment.ofAddress(argPtrs));
        } catch (Throwable t) {
            SharedUtils.handleUncaughtException(t, data.handler());
        }
    }

    /**
     * Wrapper for {@code ffi_get_struct_offsets}
     *
     * @param structType a pointer to an {@code ffi_type} representing a struct
     * @param offsetsOut a pointer to an array of {@code size_t}, with one element for each element of the struct.
     *                   This is an 'out' parameter that will be filled in by this call
     * @param abi the abi to be used
     *
     * @throws IllegalStateException if the call to {@code ffi_get_struct_offsets} returns a non-zero status code
     */
    static void getStructOffsets(MemorySegment structType, MemorySegment offsetsOut, FFIABI abi)
            throws IllegalStateException  {
        checkStatus(ffi_get_struct_offsets(abi.value(), structType.address(), offsetsOut.address()));
    }

    private static void checkStatus(int code) {
        FFIStatus status = FFIStatus.of(code);
        if (status != FFIStatus.FFI_OK) {
            throw new IllegalStateException("libffi call failed with status: " + status);
        }
    }

    private static native void init();

    private static native long sizeofCif();

    private static native int createClosure(long cif, Object userData, long[] ptrs);
    private static native void freeClosure(long closureAddress, long globalTarget);
    private static native void doDowncall(long cif, long fn, long rvalue, long avalues, long capturedState, int capturedStateMask);

    private static native int ffi_prep_cif(long cif, int abi, int nargs, long rtype, long atypes);
    private static native int ffi_get_struct_offsets(int abi, long type, long offsets);

    private static native int ffi_default_abi();
    private static native short ffi_type_struct();

    private static native long ffi_type_void();
    private static native long ffi_type_uint8();
    private static native long ffi_type_sint8();
    private static native long ffi_type_uint16();
    private static native long ffi_type_sint16();
    private static native long ffi_type_uint32();
    private static native long ffi_type_sint32();
    private static native long ffi_type_uint64();
    private static native long ffi_type_sint64();
    private static native long ffi_type_float();
    private static native long ffi_type_double();
    private static native long ffi_type_pointer();
}
