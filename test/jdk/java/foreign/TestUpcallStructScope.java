/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64" | os.arch == "riscv64"
 *
 * @run testng/othervm/native
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   TestUpcallStructScope
 * @run testng/othervm/native
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   TestUpcallStructScope
 */

import java.lang.foreign.*;

import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.testng.Assert.assertFalse;

public class TestUpcallStructScope extends NativeTestHelper {
    static final MethodHandle MH_do_upcall;
    static final Linker LINKER = Linker.nativeLinker();
    static final MethodHandle MH_Consumer_accept;

    // struct S_PDI { void* p0; double p1; int p2; };
    static final MemoryLayout S_PDI_LAYOUT = MemoryLayout.structLayout(
        C_POINTER.withName("p0"),
        C_DOUBLE.withName("p1"),
        C_INT.withName("p2")
    );

    static {
        System.loadLibrary("TestUpcallStructScope");
        MH_do_upcall = LINKER.downcallHandle(
                findNativeOrThrow("do_upcall"),
                FunctionDescriptor.ofVoid(C_POINTER, S_PDI_LAYOUT)
        );

        try {
            MH_Consumer_accept = MethodHandles.publicLookup().findVirtual(Consumer.class, "accept",
                    MethodType.methodType(void.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static MethodHandle methodHandle (Consumer<MemorySegment> callback) {
        return MH_Consumer_accept.bindTo(callback).asType(MethodType.methodType(void.class, MemorySegment.class));
    }

    @Test
    public void testUpcall() throws Throwable {
        AtomicReference<MemorySegment> capturedSegment = new AtomicReference<>();
        MethodHandle target = methodHandle(capturedSegment::set);
        FunctionDescriptor upcallDesc = FunctionDescriptor.ofVoid(S_PDI_LAYOUT);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment upcallStub = LINKER.upcallStub(target, upcallDesc, arena);
            MemorySegment argSegment = arena.allocate(S_PDI_LAYOUT);
            MH_do_upcall.invoke(upcallStub, argSegment);
        }

        MemorySegment captured = capturedSegment.get();
        assertFalse(captured.scope().isAlive());
    }

}
