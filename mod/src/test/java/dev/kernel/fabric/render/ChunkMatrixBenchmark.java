package dev.kernel.fabric.render;

import org.joml.Matrix4f;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

public final class ChunkMatrixBenchmark {
    private static volatile Matrix4f[] escaped;
    private static final com.sun.management.ThreadMXBean ALLOCATION = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static double[] run(int sections, boolean reuse, int frames) {
        var source = new Matrix4f().rotateXYZ(.1f,.2f,.3f).translate(2,3,4);
        Matrix4f[] output = new Matrix4f[sections];
        long tid=Thread.currentThread().threadId(), beforeBytes=ALLOCATION.getThreadAllocatedBytes(tid), start=System.nanoTime();
        for (int frame=0;frame<frames;frame++) {
            source.m30(frame);
            Matrix4f cached = null;
            for (int i=0;i<sections;i++) {
                Matrix4f next = reuse && ChunkMatrixSnapshot.matches(cached,source) ? cached : new Matrix4f(source);
                output[i]=next; cached=next;
            }
            escaped=output;
        }
        return new double[]{(System.nanoTime()-start)/(double)frames,(ALLOCATION.getThreadAllocatedBytes(tid)-beforeBytes)/(double)frames};
    }
    public static void main(String[] args) {
        for(int sections:new int[]{1,32,512,2048}) {
            for(int warm=0;warm<4;warm++){run(sections,false,4096);run(sections,true,4096);}
            double[][] times=new double[2][7]; double[] bytes=new double[2];
            for(int iteration=0;iteration<7;iteration++) for(int order=0;order<2;order++) {
                int mode=(iteration+order)%2;double[] result=run(sections,mode==1,4096);times[mode][iteration]=result[0];bytes[mode]=result[1];
            }
            Arrays.sort(times[0]);Arrays.sort(times[1]);
            System.out.printf("%d sections: %.1f -> %.1f ns/batch, %.0f -> %.0f bytes/batch%n",sections,times[0][3],times[1][3],bytes[0],bytes[1]);
        }
    }
}
