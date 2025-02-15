/*
 *  Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @enablePreview
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestMemoryAccessInstance
 */

import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Function;

import org.testng.annotations.*;
import org.testng.SkipException;
import static org.testng.Assert.*;

public class TestMemoryAccessInstance {

    static class Accessor<T, X, L extends ValueLayout> {

        interface SegmentGetter<T, X, L> {
            X get(T buffer, L layout, long offset);
        }

        interface SegmentSetter<T, X, L> {
            void set(T buffer, L layout, long offset, X o);
        }

        interface BufferGetter<X> {
            X get(ByteBuffer segment, int offset);
        }

        interface BufferSetter<X> {
            void set(ByteBuffer buffer, int offset, X o);
        }

        final X value;
        final L layout;
        final Function<MemorySegment, T> transform;
        final SegmentGetter<T, X, L> segmentGetter;
        final SegmentSetter<T, X, L> segmentSetter;
        final BufferGetter<X> bufferGetter;
        final BufferSetter<X> bufferSetter;

        Accessor(Function<MemorySegment, T> transform, L layout, X value,
                 SegmentGetter<T, X, L> segmentGetter, SegmentSetter<T, X, L> segmentSetter,
                 BufferGetter<X> bufferGetter, BufferSetter<X> bufferSetter) {
            this.transform = transform;
            this.layout = layout;
            this.value = value;
            this.segmentGetter = segmentGetter;
            this.segmentSetter = segmentSetter;
            this.bufferGetter = bufferGetter;
            this.bufferSetter = bufferSetter;
        }

        void test() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(128, 1);
                ByteBuffer buffer = segment.asByteBuffer();
                T t = transform.apply(segment);
                segmentSetter.set(t, layout, 8, value);
                assertEquals(bufferGetter.get(buffer, 8), value);
                bufferSetter.set(buffer, 8, value);
                assertEquals(value, segmentGetter.get(t, layout, 8));
            }
        }

        @SuppressWarnings("unchecked")
        void testHyperAligned() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(64, 1);
                T t = transform.apply(segment);
                L alignedLayout = (L)layout.withBitAlignment(layout.byteSize() * 8 * 2);
                try {
                    segmentSetter.set(t, alignedLayout, 0, value);
                    fail();
                } catch (IllegalArgumentException exception) {
                    assertTrue(exception.getMessage().contains("greater"));
                }
                try {
                    segmentGetter.get(t, alignedLayout, 0);
                    fail();
                } catch (IllegalArgumentException exception) {
                    assertTrue(exception.getMessage().contains("greater"));
                }
            }
        }

        static <L extends ValueLayout, X> Accessor<MemorySegment, X, L> ofSegment(L layout, X value,
                         SegmentGetter<MemorySegment, X, L> segmentGetter, SegmentSetter<MemorySegment, X, L> segmentSetter,
                         BufferGetter<X> bufferGetter, BufferSetter<X> bufferSetter) {
            return new Accessor<>(Function.identity(), layout, value, segmentGetter, segmentSetter, bufferGetter, bufferSetter);
        }
    }

    @Test(dataProvider = "segmentAccessors")
    public void testSegmentAccess(String testName, Accessor<?, ?, ?> accessor) {
        accessor.test();
    }

    @Test(dataProvider = "segmentAccessors")
    public void testSegmentAccessHyper(String testName, Accessor<?, ?, ?> accessor) {
        if (testName.contains("index")) {
            accessor.testHyperAligned();
        } else {
            throw new SkipException("Skipping");
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*Heap segment not allowed.*")
    public void badHeapSegmentSet() {
        long byteSize = ValueLayout.ADDRESS.byteSize();
        Arena scope = Arena.ofAuto();
        MemorySegment targetSegment = scope.allocate(byteSize, 1);
        MemorySegment segment = MemorySegment.ofArray(new byte[]{ 0, 1, 2 });
        targetSegment.set(ValueLayout.ADDRESS, 0, segment); // should throw
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*Heap segment not allowed.*")
    public void badHeapSegmentSetAtIndex() {
        long byteSize = ValueLayout.ADDRESS.byteSize();
        Arena scope = Arena.ofAuto();
        MemorySegment targetSegment = scope.allocate(byteSize, 1);
        MemorySegment segment = MemorySegment.ofArray(new byte[]{ 0, 1, 2 });
        targetSegment.setAtIndex(ValueLayout.ADDRESS, 0, segment); // should throw
    }

    static final ByteOrder NE = ByteOrder.nativeOrder();

    @DataProvider(name = "segmentAccessors")
    static Object[][] segmentAccessors() {
        return new Object[][]{

                {"byte", Accessor.ofSegment(ValueLayout.JAVA_BYTE, (byte) 42,
                        MemorySegment::get, MemorySegment::set,
                        ByteBuffer::get, ByteBuffer::put)
                },
                {"bool", Accessor.ofSegment(ValueLayout.JAVA_BOOLEAN, false,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.get(pos) != 0, (bb, pos, v) -> bb.put(pos, v ? (byte)1 : (byte)0))
                },
                {"char", Accessor.ofSegment(ValueLayout.JAVA_CHAR, (char) 42,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getChar(pos), (bb, pos, v) -> bb.order(NE).putChar(pos, v))
                },
                {"int", Accessor.ofSegment(ValueLayout.JAVA_INT, 42,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getInt(pos), (bb, pos, v) -> bb.order(NE).putInt(pos, v))
                },
                {"float", Accessor.ofSegment(ValueLayout.JAVA_FLOAT, 42f,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getFloat(pos), (bb, pos, v) -> bb.order(NE).putFloat(pos, v))
                },
                {"long", Accessor.ofSegment(ValueLayout.JAVA_LONG, 42L,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getLong(pos), (bb, pos, v) -> bb.order(NE).putLong(pos, v))
                },
                {"double", Accessor.ofSegment(ValueLayout.JAVA_DOUBLE, 42d,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getDouble(pos), (bb, pos, v) -> bb.order(NE).putDouble(pos, v))
                },
                { "address", Accessor.ofSegment(ValueLayout.ADDRESS, MemorySegment.ofAddress(42),
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> {
                            ByteBuffer nb = bb.order(NE);
                            long addr = ValueLayout.ADDRESS.byteSize() == 8 ?
                                    nb.getLong(pos) : nb.getInt(pos);
                            return MemorySegment.ofAddress(addr);
                        },
                        (bb, pos, v) -> {
                            ByteBuffer nb = bb.order(NE);
                            if (ValueLayout.ADDRESS.byteSize() == 8) {
                                nb.putLong(pos, v.address());
                            } else {
                                nb.putInt(pos, (int)v.address());
                            }
                        })
                },

                {"char/index", Accessor.ofSegment(ValueLayout.JAVA_CHAR, (char) 42,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getChar(pos * 2), (bb, pos, v) -> bb.order(NE).putChar(pos * 2, v))
                },
                {"int/index", Accessor.ofSegment(ValueLayout.JAVA_INT, 42,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getInt(pos * 4), (bb, pos, v) -> bb.order(NE).putInt(pos * 4, v))
                },
                {"float/index", Accessor.ofSegment(ValueLayout.JAVA_FLOAT, 42f,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getFloat(pos * 4), (bb, pos, v) -> bb.order(NE).putFloat(pos * 4, v))
                },
                {"long/index", Accessor.ofSegment(ValueLayout.JAVA_LONG, 42L,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getLong(pos * 8), (bb, pos, v) -> bb.order(NE).putLong(pos * 8, v))
                },
                {"double/index", Accessor.ofSegment(ValueLayout.JAVA_DOUBLE, 42d,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getDouble(pos * 8), (bb, pos, v) -> bb.order(NE).putDouble(pos * 8, v))
                },
                { "address/index", Accessor.ofSegment(ValueLayout.ADDRESS, MemorySegment.ofAddress(42),
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> {
                            ByteBuffer nb = bb.order(NE);
                            long addr = ValueLayout.ADDRESS.byteSize() == 8 ?
                                    nb.getLong(pos * 8) : nb.getInt(pos * 4);
                            return MemorySegment.ofAddress(addr);
                        },
                        (bb, pos, v) -> {
                            ByteBuffer nb = bb.order(NE);
                            if (ValueLayout.ADDRESS.byteSize() == 8) {
                                nb.putLong(pos * 8, v.address());
                            } else {
                                nb.putInt(pos * 4, (int)v.address());
                            }
                        })
                },
        };
    }
}
