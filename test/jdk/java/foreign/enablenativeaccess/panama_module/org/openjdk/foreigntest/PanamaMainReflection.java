/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.foreigntest;

import java.lang.foreign.*;
import java.lang.foreign.Arena;
import java.lang.reflect.Method;

public class PanamaMainReflection {
   public static void main(String[] args) throws Throwable {
       testReflectionnativeLinker();
       testReflectionMemorySegment();
   }

    public static void testReflectionnativeLinker() throws Throwable {
        System.out.println("Trying to get Linker");
        Method method = Linker.class.getDeclaredMethod("nativeLinker");
        method.invoke(null);
        System.out.println("Got Linker");
    }

    public static void testReflectionMemorySegment() throws Throwable {
        System.out.println("Trying to get MemorySegment");
        Method method = MemorySegment.class.getDeclaredMethod("reinterpret", long.class);
        method.invoke(MemorySegment.NULL, 10L);
        System.out.println("Got MemorySegment");
    }
}
