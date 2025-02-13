/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.ref.CleanerFactory;

import java.lang.foreign.MemorySegment.Scope;

/**
 * An arena controls the lifecycle of native memory segments, providing both flexible allocation and timely deallocation.
 * <p>
 * An arena has a {@linkplain MemorySegment.Scope scope} - the <em>arena scope</em>. All the segments allocated
 * by the arena are associated with the arena scope. As such, the arena determines the temporal bounds
 * of all the memory segments allocated by it.
 * <p>
 * Moreover, an arena also determines whether access to memory segments allocated by it should be
 * {@linkplain MemorySegment#isAccessibleBy(Thread) restricted} to specific threads.
 * An arena is a {@link SegmentAllocator} and features several allocation methods that can be used by clients
 * to obtain native segments.
 * <p>
 * The simplest arena is the {@linkplain Arena#global() global arena}. The global arena
 * features an <em>unbounded lifetime</em>. As such, native segments allocated with the global arena are always
 * accessible and their backing regions of memory are never deallocated. Moreover, memory segments allocated with the
 * global arena can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} from any thread.
 * {@snippet lang = java:
 * MemorySegment segment = Arena.global().allocate(100, 1);
 * ...
 * // segment is never deallocated!
 *}
 * <p>
 * Alternatively, clients can obtain an {@linkplain Arena#ofAuto() automatic arena}, that is an arena
 * which features a <em>bounded lifetime</em> that is managed, automatically, by the garbage collector. As such, the regions
 * of memory backing memory segments allocated with the automatic arena are deallocated at some unspecified time
 * <em>after</em> the automatic allocator (and all the segments allocated by it) become
 * <a href="../../../java/lang/ref/package.html#reachability">unreachable</a>, as shown below:
 *
 * {@snippet lang = java:
 * MemorySegment segment = Arena.ofAuto().allocate(100, 1);
 * ...
 * segment = null; // the segment region becomes available for deallocation after this point
 *}
 * Memory segments allocated with an automatic arena can also be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} from any thread.
 * <p>
 * Rather than leaving deallocation in the hands of the Java runtime, clients will often wish to exercise control over
 * the timing of deallocation for regions of memory that back memory segments. Two kinds of arenas support this,
 * namely {@linkplain #ofConfined() confined} and {@linkplain #ofShared() shared} arenas. They both feature
 * bounded lifetimes that are managed manually. For instance, the lifetime of a confined arena starts when the confined
 * arena is created, and ends when the confined arena is {@linkplain #close() closed}. As a result, the regions of memory
 * backing memory segments allocated with a confined arena are deallocated when the confined arena is closed.
 * When this happens, all the segments allocated with the confined arena are invalidated, and subsequent access
 * operations on these segments will fail {@link IllegalStateException}:
 *
 * {@snippet lang = java:
 * MemorySegment segment = null;
 * try (Arena arena = Arena.ofConfined()) {
 *     segment = arena.allocate(100);
 *     ...
 * } // segment region deallocated here
 * segment.get(ValueLayout.JAVA_BYTE, 0); // throws IllegalStateException
 *}
 *
 * Memory segments allocated with a {@linkplain #ofConfined() confined arena} can only be accessed (and closed) by the
 * thread that created the arena. If access to a memory segment from multiple threads is required, clients can allocate
 * segment in a {@linkplain #ofShared() shared arena} instead.
 * <p>
 * The characteristics of the various arenas are summarized in the following table:
 *
 * <blockquote><table class="plain">
 * <caption style="display:none">Arenas characteristics</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Kind</th>
 *     <th scope="col">Bounded lifetime</th>
 *     <th scope="col">Explicitly closeable</th>
 *     <th scope="col">Accessible from multiple threads</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="font-weight:normal">Global</th>
 *     <td style="text-align:center;">No</td>
 *     <td style="text-align:center;">No</td>
 *     <td style="text-align:center;">Yes</td></tr>
 * <tr><th scope="row" style="font-weight:normal">Automatic</th>
 *     <td style="text-align:center;">Yes</td>
 *     <td style="text-align:center;">No</td>
 *     <td style="text-align:center;">Yes</td></tr>
 * <tr><th scope="row" style="font-weight:normal">Confined</th>
 *     <td style="text-align:center;">Yes</td>
 *     <td style="text-align:center;">Yes</td>
 *     <td style="text-align:center;">No</td></tr>
 * <tr><th scope="row" style="font-weight:normal">Shared</th>
 *     <td style="text-align:center;">Yes</td>
 *     <td style="text-align:center;">Yes</td>
 *     <td style="text-align:center;">Yes</td></tr>
 * </tbody>
 * </table></blockquote>
 *
 * <h2 id = "thread-confinement">Safety and thread-confinement</h2>
 *
 * Arenas provide strong temporal safety guarantees: a memory segment allocated by an arena cannot be accessed
 * <em>after</em> the arena has been closed. The cost of providing this guarantee varies based on the
 * number of threads that have access to the memory segments allocated by the arena. For instance, if an arena
 * is always created and closed by one thread, and the memory segments allocated by the arena are always
 * accessed by that same thread, then ensuring correctness is trivial.
 * <p>
 * Conversely, if an arena allocates segments that can be accessed by multiple threads, or if the arena can be closed
 * by a thread other than the accessing thread, then ensuring correctness is much more complex. For example, a segment
 * allocated with the arena might be accessed <em>while</em> another thread attempts, concurrently, to close the arena.
 * To provide the strong temporal safety guarantee without forcing every client, even simple ones, to incur a performance
 * impact, arenas are divided into <em>thread-confined</em> arenas, and <em>shared</em> arenas.
 * <p>
 * Confined arenas, support strong thread-confinement guarantees. Upon creation, they are assigned an
 * <em>owner thread</em>, typically the thread which initiated the creation operation.
 * The segments created by a confined arena can only be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed}
 * by the owner thread. Moreover, any attempt to close the confined arena from a thread other than the owner thread will
 * fail with {@link WrongThreadException}.
 * <p>
 * Shared arenas, on the other hand, have no owner thread. The segments created by a shared arena
 * can be {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread. This might be useful when
 * multiple threads need to access the same memory segment concurrently (e.g. in the case of parallel processing).
 * Moreover, a shared arena can be closed by any thread.
 *
 *
 * @implSpec
 * Implementations of this interface are thread-safe.
 *
 * @see MemorySegment
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public interface Arena extends SegmentAllocator, AutoCloseable {

    /**
     * Creates a new arena that is managed, automatically, by the garbage collector.
     * Segments obtained with the returned arena can be
     * {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread.
     * Calling {@link #close()} on the returned arena will result in an {@link UnsupportedOperationException}.
     *
     * @return a new arena that is managed, automatically, by the garbage collector.
     */
    static Arena ofAuto() {
        return MemorySessionImpl.createImplicit(CleanerFactory.cleaner()).asArena();
    }

    /**
     * Obtains the global arena. Segments obtained with the global arena can be
     * {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by any thread.
     * Calling {@link #close()} on the returned arena will result in an {@link UnsupportedOperationException}.
     *
     * @return the global arena.
     */
    static Arena global() {
        class Holder {
            final static Arena GLOBAL = MemorySessionImpl.GLOBAL.asArena();
        }
        return Holder.GLOBAL;
    }

    /**
     * {@return a new confined arena, owned by the current thread}
     */
    static Arena ofConfined() {
        return MemorySessionImpl.createConfined(Thread.currentThread()).asArena();
    }

    /**
     * {@return a new shared arena}
     */
    static Arena ofShared() {
        return MemorySessionImpl.createShared().asArena();
    }

    /**
     * Returns a native memory segment with the given size (in bytes) and alignment constraint (in bytes).
     * The returned segment is associated with this {@linkplain #scope() arena scope}.
     * The segment's {@link MemorySegment#address() address} is the starting address of the
     * allocated off-heap memory region backing the segment, and the address is
     * aligned according the provided alignment constraint.
     *
     * @implSpec
     * Implementations of this method must return a native segment featuring the requested size,
     * and that is compatible with the provided alignment constraint. Furthermore, for any two segments
     * {@code S1, S2} returned by this method, the following invariant must hold:
     *
     * {@snippet lang = java:
     * S1.overlappingSlice(S2).isEmpty() == true
     *}
     *
     * @param byteSize the size (in bytes) of the off-heap memory block backing the native memory segment.
     * @param byteAlignment the alignment constraint (in bytes) of the off-heap region of memory backing the native memory segment.
     * @return a new native memory segment.
     * @throws IllegalArgumentException if {@code bytesSize < 0}, {@code alignmentBytes <= 0}, or if {@code alignmentBytes}
     * is not a power of 2.
     * @throws IllegalStateException if this arena has already been {@linkplain #close() closed}.
     * @throws WrongThreadException if this arena is confined, and this method is called from a thread {@code T}
     * other than the arena owner thread.
     */
    @Override
    default MemorySegment allocate(long byteSize, long byteAlignment) {
        return ((MemorySessionImpl)scope()).allocate(byteSize, byteAlignment);
    }

    /**
     * {@return the arena scope}
     */
    Scope scope();

    /**
     * Closes this arena. If this method completes normally, the arena scope is no longer {@linkplain Scope#isAlive() alive},
     * and all the memory segments associated with it can no longer be accessed. Furthermore, any off-heap region of memory backing the
     * segments obtained from this arena are also released.
     *
     * @apiNote This operation is not idempotent; that is, closing an already closed arena <em>always</em> results in an
     * exception being thrown. This reflects a deliberate design choice: failure to close an arena might reveal a bug
     * in the underlying application logic.
     *
     * @implSpec If this method completes normally, then {@code this.scope().isAlive() == false}.
     * Implementations are allowed to throw {@link UnsupportedOperationException} if an explicit close operation is
     * not supported.
     *
     * @see Scope#isAlive()
     *
     * @throws IllegalStateException if the arena has already been closed.
     * @throws IllegalStateException if a segment associated with this arena is being accessed concurrently, e.g.
     * by a {@linkplain Linker#downcallHandle(FunctionDescriptor, Linker.Option...) downcall method handle}.
     * @throws WrongThreadException if this arena is confined, and this method is called from a thread {@code T}
     * other than the arena owner thread.
     * @throws UnsupportedOperationException if this arena does not support explicit closure.
     */
    @Override
    void close();

}
