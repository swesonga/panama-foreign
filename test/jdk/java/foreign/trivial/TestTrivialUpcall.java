/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @enablePreview
 * @library ../ /test/lib
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @requires vm.flavor != "zero"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestTrivialUpcall
 */

import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static org.testng.Assert.fail;

public class TestTrivialUpcall extends UpcallTestHelper {

    @Test
    public void testUpcallFailure() throws IOException, InterruptedException {
        // test to see if we catch a trivial downcall doing an upcall
        runInNewProcess(Runner.class, true).assertStdOutContains("wrong thread state for upcall");
    }

    public static class Runner extends NativeTestHelper {
        public static void main(String[] args) throws Throwable {
            System.loadLibrary("Trivial");

            MethodHandle mh = downcallHandle("do_upcall", FunctionDescriptor.ofVoid(C_POINTER), Linker.Option.isTrivial());
            MemorySegment stub = upcallStub(Runner.class, "target", FunctionDescriptor.ofVoid());
            mh.invokeExact(stub);
        }

        public static void target() {
            fail("Should not get here");
        }
    }
}
