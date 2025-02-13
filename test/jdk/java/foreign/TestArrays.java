/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @enablePreview
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestArrays
 */

import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.PathElement;

import java.lang.invoke.VarHandle;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.testng.annotations.*;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.testng.Assert.*;

public class TestArrays {

    static SequenceLayout bytes = MemoryLayout.sequenceLayout(100,
            JAVA_BYTE
    );

    static SequenceLayout chars = MemoryLayout.sequenceLayout(100,
            JAVA_CHAR
    );

    static SequenceLayout shorts = MemoryLayout.sequenceLayout(100,
            JAVA_SHORT
    );

    static SequenceLayout ints = MemoryLayout.sequenceLayout(100,
            JAVA_INT
    );

    static SequenceLayout floats = MemoryLayout.sequenceLayout(100,
            JAVA_FLOAT
    );

    static SequenceLayout longs = MemoryLayout.sequenceLayout(100,
            JAVA_LONG
    );

    static SequenceLayout doubles = MemoryLayout.sequenceLayout(100,
            JAVA_DOUBLE
    );

    static VarHandle byteHandle = bytes.varHandle(PathElement.sequenceElement());
    static VarHandle charHandle = chars.varHandle(PathElement.sequenceElement());
    static VarHandle shortHandle = shorts.varHandle(PathElement.sequenceElement());
    static VarHandle intHandle = ints.varHandle(PathElement.sequenceElement());
    static VarHandle floatHandle = floats.varHandle(PathElement.sequenceElement());
    static VarHandle longHandle = longs.varHandle(PathElement.sequenceElement());
    static VarHandle doubleHandle = doubles.varHandle(PathElement.sequenceElement());

    static void initBytes(MemorySegment base, SequenceLayout seq, BiConsumer<MemorySegment, Long> handleSetter) {
        for (long i = 0; i < seq.elementCount() ; i++) {
            handleSetter.accept(base, i);
        }
    }

    static void checkBytes(MemorySegment base, SequenceLayout layout, Function<MemorySegment, Object> arrayFactory, BiFunction<MemorySegment, Long, Object> handleGetter) {
        int nelems = (int)layout.elementCount();
        Object arr = arrayFactory.apply(base);
        for (int i = 0; i < nelems; i++) {
            Object found = handleGetter.apply(base, (long) i);
            Object expected = java.lang.reflect.Array.get(arr, i);
            assertEquals(expected, found);
        }
    }

    @Test(dataProvider = "arrays")
    public void testArrays(Consumer<MemorySegment> init, Consumer<MemorySegment> checker, MemoryLayout layout) {
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        init.accept(segment);
        assertFalse(segment.isReadOnly());
        checker.accept(segment);
    }

    @Test(dataProvider = "elemLayouts",
            expectedExceptions = IllegalStateException.class)
    public void testTooBigForArray(MemoryLayout layout, Function<MemorySegment, Object> arrayFactory) {
        MemoryLayout seq = MemoryLayout.sequenceLayout((Integer.MAX_VALUE * layout.byteSize()) + 1, layout);
        //do not really allocate here, as it's way too much memory
        MemorySegment segment = MemorySegment.NULL.reinterpret(seq.byteSize());
        arrayFactory.apply(segment);
    }

    @Test(dataProvider = "elemLayouts",
            expectedExceptions = IllegalStateException.class)
    public void testBadSize(MemoryLayout layout, Function<MemorySegment, Object> arrayFactory) {
        if (layout.byteSize() == 1) throw new IllegalStateException(); //make it fail
        try (Arena arena = Arena.ofConfined()) {
            long byteSize = layout.byteSize() + 1;
            long byteAlignment = layout.byteSize();
            MemorySegment segment = arena.allocate(byteSize, byteAlignment);
            arrayFactory.apply(segment);
        }
    }

    @Test(dataProvider = "elemLayouts",
            expectedExceptions = IllegalStateException.class)
    public void testArrayFromClosedSegment(MemoryLayout layout, Function<MemorySegment, Object> arrayFactory) {
        Arena arena = Arena.ofConfined();
        MemorySegment segment = arena.allocate(layout);
        arena.close();
        arrayFactory.apply(segment);
    }

    @DataProvider(name = "arrays")
    public Object[][] nativeAccessOps() {
        Consumer<MemorySegment> byteInitializer =
                (base) -> initBytes(base, bytes, (addr, pos) -> byteHandle.set(addr, pos, (byte)(long)pos));
        Consumer<MemorySegment> charInitializer =
                (base) -> initBytes(base, chars, (addr, pos) -> charHandle.set(addr, pos, (char)(long)pos));
        Consumer<MemorySegment> shortInitializer =
                (base) -> initBytes(base, shorts, (addr, pos) -> shortHandle.set(addr, pos, (short)(long)pos));
        Consumer<MemorySegment> intInitializer =
                (base) -> initBytes(base, ints, (addr, pos) -> intHandle.set(addr, pos, (int)(long)pos));
        Consumer<MemorySegment> floatInitializer =
                (base) -> initBytes(base, floats, (addr, pos) -> floatHandle.set(addr, pos, (float)(long)pos));
        Consumer<MemorySegment> longInitializer =
                (base) -> initBytes(base, longs, (addr, pos) -> longHandle.set(addr, pos, (long)pos));
        Consumer<MemorySegment> doubleInitializer =
                (base) -> initBytes(base, doubles, (addr, pos) -> doubleHandle.set(addr, pos, (double)(long)pos));

        Consumer<MemorySegment> byteChecker =
                (base) -> checkBytes(base, bytes, s -> s.toArray(JAVA_BYTE), (addr, pos) -> (byte)byteHandle.get(addr, pos));
        Consumer<MemorySegment> shortChecker =
                (base) -> checkBytes(base, shorts, s -> s.toArray(JAVA_SHORT), (addr, pos) -> (short)shortHandle.get(addr, pos));
        Consumer<MemorySegment> charChecker =
                (base) -> checkBytes(base, chars, s -> s.toArray(JAVA_CHAR), (addr, pos) -> (char)charHandle.get(addr, pos));
        Consumer<MemorySegment> intChecker =
                (base) -> checkBytes(base, ints, s -> s.toArray(JAVA_INT), (addr, pos) -> (int)intHandle.get(addr, pos));
        Consumer<MemorySegment> floatChecker =
                (base) -> checkBytes(base, floats, s -> s.toArray(JAVA_FLOAT), (addr, pos) -> (float)floatHandle.get(addr, pos));
        Consumer<MemorySegment> longChecker =
                (base) -> checkBytes(base, longs, s -> s.toArray(JAVA_LONG), (addr, pos) -> (long)longHandle.get(addr, pos));
        Consumer<MemorySegment> doubleChecker =
                (base) -> checkBytes(base, doubles, s -> s.toArray(JAVA_DOUBLE), (addr, pos) -> (double)doubleHandle.get(addr, pos));

        return new Object[][]{
                {byteInitializer, byteChecker, bytes},
                {charInitializer, charChecker, chars},
                {shortInitializer, shortChecker, shorts},
                {intInitializer, intChecker, ints},
                {floatInitializer, floatChecker, floats},
                {longInitializer, longChecker, longs},
                {doubleInitializer, doubleChecker, doubles}
        };
    }

    @DataProvider(name = "elemLayouts")
    public Object[][] elemLayouts() {
        return new Object[][] {
                { JAVA_BYTE, (Function<MemorySegment, Object>)s -> s.toArray(JAVA_BYTE)},
                { JAVA_SHORT, (Function<MemorySegment, Object>) s -> s.toArray(JAVA_SHORT)},
                { JAVA_CHAR, (Function<MemorySegment, Object>) s -> s.toArray(JAVA_CHAR)},
                { JAVA_INT, (Function<MemorySegment, Object>)s -> s.toArray(JAVA_INT)},
                { JAVA_FLOAT, (Function<MemorySegment, Object>)s -> s.toArray(JAVA_FLOAT)},
                { JAVA_LONG, (Function<MemorySegment, Object>)s -> s.toArray(JAVA_LONG)},
                { JAVA_DOUBLE, (Function<MemorySegment, Object>)s -> s.toArray(JAVA_DOUBLE)}
        };
    }
}
