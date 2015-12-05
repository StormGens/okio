/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

/**
 * A collection of unused segments, necessary to avoid GC churn and zero-fill.
 * This pool is a thread-safe static singleton.
 *
 *
 * A->B->C->D->E->F
 *
 *一个Segment池，由一个单向链表构成。
 * 该池负责Segment的回收和闲置Segment的管理，一般Segment都是从该池获取的。该池是线程安全的。
 */
final class SegmentPool {
  /** The maximum number of bytes to pool. */
  // TODO: Is 64 KiB a good maximum size? Do we ever have that many idle segments?
  static final long MAX_SIZE = 64 * 1024; // 64 KiB.

  /** Singly-linked list of segments. */
  static Segment next;//

  /** Total bytes in this pool. */
  static long byteCount;//池子目前所包含的字节数

  private SegmentPool() {
  }

  /**
   *
   * 如果为 A->B->C->D->E->F ,那么take出来的是A.再次take为B
   *
   * 检测单链表是否为空.不为空, 取下链表头给申请者, 否则生成一个新segment返回.
   * @return
     */
  static Segment take() {
    synchronized (SegmentPool.class) {
      if (next != null) {
        Segment result = next;
        next = result.next;
        result.next = null;
        byteCount -= Segment.SIZE;
        return result;
      }
    }
    return new Segment(); // Pool is empty. Don't zero-fill while holding a lock.
  }

  /**
   * 回收一个segment到该SegmentPool中.
   *
   * 如果当前链表为 A->B->C->D->E->F,recycle(G)过来:
   * 如果G是在一个链表中的,报错.如果G正在被共享,也不能被回收,如果Pool满了,也不能被回收.
   *
   * 能回收时,回收后链表变为:G->A->B->C->D->E->F.
   *
   * @param segment
     */
  static void recycle(Segment segment) {
    //如果该segment 有前后关联,不能被回收
    if (segment.next != null || segment.prev != null) throw new IllegalArgumentException();
    //如果该segment 正在被共享,也不能被回收
    if (segment.shared) return; // This segment cannot be recycled.


    synchronized (SegmentPool.class) {
      //池子是满的,也不能被回收
      if (byteCount + Segment.SIZE > MAX_SIZE) return; // Pool is full.
      byteCount += Segment.SIZE;
      segment.next = next;
      segment.pos = segment.limit = 0;
      next = segment;
    }
  }
}
