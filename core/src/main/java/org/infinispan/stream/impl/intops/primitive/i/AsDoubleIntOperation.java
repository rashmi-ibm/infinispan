package org.infinispan.stream.impl.intops.primitive.i;

import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.infinispan.stream.impl.intops.IntermediateOperation;

import io.reactivex.Flowable;

/**
 * Performs as double operation on a {@link IntStream}
 */
public class AsDoubleIntOperation implements IntermediateOperation<Integer, IntStream, Double, DoubleStream> {
   private static final AsDoubleIntOperation OPERATION = new AsDoubleIntOperation();
   private AsDoubleIntOperation() { }

   public static AsDoubleIntOperation getInstance() {
      return OPERATION;
   }

   @Override
   public DoubleStream perform(IntStream stream) {
      return stream.asDoubleStream();
   }

   @Override
   public Flowable<Double> mapFlowable(Flowable<Integer> input) {
      return input.map(Integer::doubleValue);
   }
}
