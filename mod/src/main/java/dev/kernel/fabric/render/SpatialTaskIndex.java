package dev.kernel.fabric.render;

import java.util.ArrayList;
import java.util.Arrays;

/** Median-partitioned point index for moving cameras, with a lazily built heap for stationary queries. */
final class SpatialTaskIndex<T> {
    private static final int TREE_THRESHOLD = 512;
    private final ArrayList<Node<T>> active = new ArrayList<>();
    private final ArrayList<Node<T>> pending = new ArrayList<>();
    private final ArrayList<Node<T>> heap = new ArrayList<>();
    private Node<T> root;
    private int indexedCount;
    private long generation;
    private boolean heapReady;
    private int stationaryQueries;
    private double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;
    private double x, y, z, bestDistance;
    private Node<T> best;

    Node<T> add(T value, int x, int y, int z, long sequence) {
        Node<T> node = new Node<>(value, x, y, z, sequence);
        node.activeIndex = active.size(); active.add(node);
        node.pendingIndex = pending.size(); pending.add(node);
        if (heapReady) {
            node.distance = pointDistance(node, lastX, lastY, lastZ);
            node.heapIndex = heap.size(); heap.add(node); siftUp(node.heapIndex);
        }
        return node;
    }

    void remove(Node<T> node) {
        if (heapReady) {
            int position = node.heapIndex;
            Node<T> last = heap.removeLast();
            if (last != node) {
                setHeap(position, last);
                if (position > 0 && before(last, heap.get((position - 1) >>> 1))) siftUp(position);
                else siftDown(position);
            }
        }
        int position = node.activeIndex;
        Node<T> last = active.removeLast();
        if (last != node) { active.set(position, last); last.activeIndex = position; }
        if (node.pendingIndex >= 0) {
            position = node.pendingIndex;
            last = pending.removeLast();
            if (last != node) { pending.set(position, last); last.pendingIndex = position; }
        } else if (node.generation == generation) {
            for (Node<T> ancestor = node; ancestor != null; ancestor = ancestor.parent) ancestor.liveCount--;
        }
        node.value = null;
        node.activeIndex = node.pendingIndex = -1;
        if (active.isEmpty()) clear();
    }

    int size() { return active.size(); }
    void clear() { root = null; active.clear(); pending.clear(); heap.clear(); heapReady = false; stationaryQueries = 0; indexedCount = 0; }

    Node<T> closest(double x, double y, double z) {
        if (active.isEmpty() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        if (x == lastX && y == lastY && z == lastZ) {
            if (!heapReady && ++stationaryQueries >= 3 && active.size() >= 32) {
                for (Node<T> node : active) {
                    node.distance = pointDistance(node, x, y, z);
                    node.heapIndex = heap.size(); heap.add(node);
                }
                for (int i = heap.size() / 2 - 1; i >= 0; i--) siftDown(i);
                heapReady = true;
            }
        } else {
            heap.clear(); heapReady = false; stationaryQueries = 0;
            lastX = x; lastY = y; lastZ = z;
        }
        if (heapReady) return heap.getFirst().distance < Double.MAX_VALUE ? heap.getFirst() : null;
        this.x = x; this.y = y; this.z = z;
        best = null; bestDistance = Double.MAX_VALUE;
        if (active.size() < TREE_THRESHOLD) {
            for (int i = 0; i < active.size(); i++) consider(active.get(i));
        } else {
            if (root == null || pending.size() >= Math.max(64, active.size() / 4) || root.liveCount < indexedCount / 2) rebuild();
            visit(root, distance(root));
            for (int i = 0; i < pending.size(); i++) consider(pending.get(i));
        }
        Node<T> result = best;
        if (result != null) result.distance = bestDistance;
        best = null;
        return result;
    }

    private void consider(Node<T> node) {
        double distance = pointDistance(node, x, y, z);
        if (distance < bestDistance || distance == bestDistance && best != null && node.sequence < best.sequence) {
            best = node; bestDistance = distance;
        }
    }

    private static double pointDistance(Node<?> node, double x, double y, double z) {
        double dx = node.x + 0.5 - x, dy = node.y + 0.5 - y, dz = node.z + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean before(Node<?> a, Node<?> b) {
        return a.distance < b.distance || a.distance == b.distance && a.sequence < b.sequence;
    }

    private void setHeap(int index, Node<T> node) { heap.set(index, node); node.heapIndex = index; }

    private void siftUp(int index) {
        Node<T> node = heap.get(index);
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (!before(node, heap.get(parent))) break;
            setHeap(index, heap.get(parent)); index = parent;
        }
        setHeap(index, node);
    }

    private void siftDown(int index) {
        Node<T> node = heap.get(index);
        while (index < heap.size() / 2) {
            int child = index * 2 + 1;
            if (child + 1 < heap.size() && before(heap.get(child + 1), heap.get(child))) child++;
            if (!before(heap.get(child), node)) break;
            setHeap(index, heap.get(child)); index = child;
        }
        setHeap(index, node);
    }

    private void visit(Node<T> node, double lowerBound) {
        if (node.liveCount == 0 || lowerBound > bestDistance || lowerBound == Double.POSITIVE_INFINITY) return;
        if (node.value != null) consider(node);
        Node<T> near = node.left, far = node.right;
        double nearDistance = near == null ? Double.POSITIVE_INFINITY : distance(near);
        double farDistance = far == null ? Double.POSITIVE_INFINITY : distance(far);
        if (farDistance < nearDistance) {
            Node<T> swap = near; near = far; far = swap;
            double swapDistance = nearDistance; nearDistance = farDistance; farDistance = swapDistance;
        }
        if (near != null) visit(near, nearDistance);
        if (far != null) visit(far, farDistance);
    }

    private double distance(Node<T> node) {
        double dx = axisDistance(x, node.minX, node.maxX);
        double dy = axisDistance(y, node.minY, node.maxY);
        double dz = axisDistance(z, node.minZ, node.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double camera, int min, int max) {
        if (camera < min + 0.5) return min + 0.5 - camera;
        if (camera > max + 0.5) return max + 0.5 - camera;
        return 0;
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        Node<T>[] nodes = active.toArray(new Node[active.size()]);
        generation++;
        pending.clear();
        indexedCount = active.size();
        root = build(nodes, 0, nodes.length, null);
    }

    private Node<T> build(Node<T>[] nodes, int from, int to, Node<T> parent) {
        if (from == to) return null;
        int minX = nodes[from].x, maxX = minX, minY = nodes[from].y, maxY = minY, minZ = nodes[from].z, maxZ = minZ;
        for (int i = from + 1; i < to; i++) {
            Node<T> node = nodes[i];
            minX = Math.min(minX, node.x); maxX = Math.max(maxX, node.x);
            minY = Math.min(minY, node.y); maxY = Math.max(maxY, node.y);
            minZ = Math.min(minZ, node.z); maxZ = Math.max(maxZ, node.z);
        }
        long spanX = (long) maxX - minX, spanY = (long) maxY - minY, spanZ = (long) maxZ - minZ;
        int axis = spanX >= spanY && spanX >= spanZ ? 0 : spanY >= spanZ ? 1 : 2;
        int middle = (from + to) >>> 1;
        select(nodes, from, to - 1, middle, axis);
        Node<T> node = nodes[middle];
        node.parent = parent; node.pendingIndex = -1; node.generation = generation;
        node.minX = minX; node.maxX = maxX; node.minY = minY; node.maxY = maxY; node.minZ = minZ; node.maxZ = maxZ;
        node.liveCount = to - from;
        node.left = build(nodes, from, middle, node);
        node.right = build(nodes, middle + 1, to, node);
        return node;
    }

    private static <T> void select(Node<T>[] nodes, int left, int right, int middle, int axis) {
        long remainingWork = 2L * (right - left + 1);
        while (left < right) {
            remainingWork -= right - left + 1;
            if (remainingWork < 0) {
                Arrays.sort(nodes, left, right + 1, (a, b) -> compare(a, b, axis));
                return;
            }
            Node<T> pivot = nodes[(left + right) >>> 1];
            int low = left, high = right;
            while (low <= high) {
                while (compare(nodes[low], pivot, axis) < 0) low++;
                while (compare(nodes[high], pivot, axis) > 0) high--;
                if (low <= high) { Node<T> swap = nodes[low]; nodes[low++] = nodes[high]; nodes[high--] = swap; }
            }
            if (middle <= high) right = high;
            else if (middle >= low) left = low;
            else return;
        }
    }

    private static int compare(Node<?> a, Node<?> b, int axis) {
        int comparison = Integer.compare(axis == 0 ? a.x : axis == 1 ? a.y : a.z, axis == 0 ? b.x : axis == 1 ? b.y : b.z);
        return comparison != 0 ? comparison : Long.compare(a.sequence, b.sequence);
    }

    static final class Node<T> {
        T value;
        final int x, y, z;
        final long sequence;
        Node<T> parent, left, right;
        int activeIndex, pendingIndex, liveCount, heapIndex;
        int minX, minY, minZ, maxX, maxY, maxZ;
        long generation;
        double distance;

        Node(T value, int x, int y, int z, long sequence) {
            this.value = value; this.x = x; this.y = y; this.z = z; this.sequence = sequence;
        }
    }
}
