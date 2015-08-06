package hex.glrm;

import hex.DataInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Random;
import java.util.concurrent.ExecutionException;

public class GLRMCategoricalTest extends TestUtil {
  public final double TOLERANCE = 1e-6;
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private static String colFormat(String[] cols, String format) {
    int[] idx = new int[cols.length];
    for(int i = 0; i < idx.length; i++) idx[i] = i;
    return colFormat(cols, format, idx);
  }
  private static String colFormat(String[] cols, String format, int[] idx) {
    StringBuilder sb = new StringBuilder();
    for(int i = 0; i < cols.length; i++) sb.append(String.format(format, cols[idx[i]]));
    sb.append("\n");
    return sb.toString();
  }

  private static String colExpFormat(String[] cols, String[][] domains, String format) {
    int[] idx = new int[cols.length];
    for(int i = 0; i < idx.length; i++) idx[i] = i;
    return colExpFormat(cols, domains, format, idx);
  }

  private static String colExpFormat(String[] cols, String[][] domains, String format, int[] idx) {
    StringBuilder sb = new StringBuilder();
    for(int i = 0; i < domains.length; i++) {
      int c = idx[i];
      if(domains[c] == null)
        sb.append(String.format(format, cols[c]));
      else {
        for(int j = 0; j < domains[c].length; j++)
          sb.append(String.format(format, domains[c][j]));
      }
    }
    sb.append("\n");
    return sb.toString();
  }

  @Test public void testCategoricalIris() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null, score = null;

    try {
      train = parse_test_file(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
      GLRMModel.GLRMParameters parms = new GLRMModel.GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._loss = GLRMModel.GLRMParameters.Loss.L1;
      parms._init = GLRM.Initialization.SVD;
      parms._transform = DataInfo.TransformType.NONE;
      parms._recover_svd = true;
      parms._max_iterations = 1000;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testCategoricalProstate() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null, score = null;
    final int[] cats = new int[]{1,3,4,5};    // Categoricals: CAPSULE, RACE, DPROS, DCAPS

    try {
      Scope.enter();
      train = parse_test_file(Key.make("prostate.hex"), "smalldata/logreg/prostate.csv");
      for(int i = 0; i < cats.length; i++)
        Scope.track(train.replace(cats[i], train.vec(cats[i]).toEnum())._key);
      train.remove("ID").remove();
      DKV.put(train._key, train);

      GLRMModel.GLRMParameters parms = new GLRMModel.GLRMParameters();
      parms._train = train._key;
      parms._k = 8;
      parms._gamma_x = parms._gamma_y = 0.1;
      parms._regularization_x = GLRMModel.GLRMParameters.Regularizer.L2;
      parms._regularization_y = GLRMModel.GLRMParameters.Regularizer.L2;
      parms._init = GLRM.Initialization.PlusPlus;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._recover_svd = false;
      parms._max_iterations = 200;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
      Scope.exit();
    }
  }

  @Test public void testLossRegularizer() throws InterruptedException, ExecutionException {
    long seed = 0xDECAF;
    Random rng = new Random(seed);
    Frame train = null, score = null;
    final int[] cats = new int[]{1,3,4,5};    // Categoricals: CAPSULE, RACE, DPROS, DCAPS
    final GLRMModel.GLRMParameters.Regularizer[] regs = new GLRMModel.GLRMParameters.Regularizer[] {
            GLRMModel.GLRMParameters.Regularizer.L2,
            GLRMModel.GLRMParameters.Regularizer.L1,
            GLRMModel.GLRMParameters.Regularizer.NonNegative,
            GLRMModel.GLRMParameters.Regularizer.OneSparse,
            GLRMModel.GLRMParameters.Regularizer.UnitOneSparse,
            GLRMModel.GLRMParameters.Regularizer.Simplex
    };

    Scope.enter();
    try {
      train = parse_test_file(Key.make("prostate.hex"), "smalldata/logreg/prostate.csv");
      for(int i = 0; i < cats.length; i++)
        Scope.track(train.replace(cats[i], train.vec(cats[i]).toEnum())._key);
      train.remove("ID").remove();
      DKV.put(train._key, train);

      for(GLRMModel.GLRMParameters.Loss loss : new GLRMModel.GLRMParameters.Loss[] {
              GLRMModel.GLRMParameters.Loss.L2,
              GLRMModel.GLRMParameters.Loss.L1,
              GLRMModel.GLRMParameters.Loss.Huber,
              GLRMModel.GLRMParameters.Loss.Poisson,
              GLRMModel.GLRMParameters.Loss.Hinge,
              GLRMModel.GLRMParameters.Loss.Logistic
      }) {
        for(GLRMModel.GLRMParameters.MultiLoss multiloss : new GLRMModel.GLRMParameters.MultiLoss[] {
                GLRMModel.GLRMParameters.MultiLoss.Categorical,
                GLRMModel.GLRMParameters.MultiLoss.Ordinal
        }) {
          GLRMModel model = null;
          try {
            Scope.enter();
            long myseed = rng.nextLong();
            Log.info("GLRM using seed = " + myseed);

            GLRMModel.GLRMParameters parms = new GLRMModel.GLRMParameters();
            parms._train = train._key;
            parms._transform = DataInfo.TransformType.NONE;
            parms._k = 5;
            parms._loss = loss;
            parms._multi_loss = multiloss;
            parms._init = GLRM.Initialization.SVD;
            parms._regularization_x = regs[rng.nextInt(regs.length)];
            parms._regularization_y = regs[rng.nextInt(regs.length)];
            parms._gamma_x = Math.abs(rng.nextDouble());
            parms._gamma_y = Math.abs(rng.nextDouble());
            parms._recover_svd = false;
            parms._seed = myseed;
            parms._verbose = false;

            GLRM job = new GLRM(parms);
            try {
              model = job.trainModel().get();
              Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
              score = model.score(train);
              ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
              Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
            } catch (Throwable t) {
              throw t;
            } finally {
              job.remove();
            }
          } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
          } finally {
            if (model != null) {
              model._output._loading_key.get().delete();
              model.delete();
            }
            if (score != null) score.delete();
            Scope.exit();
          }
        }
      }
    } finally {
      if(train != null) train.delete();
      Scope.exit();
    }
  }

  @Test public void testExpandCatsIris() throws InterruptedException, ExecutionException {
    double[][] iris = ard(ard(6.3, 2.5, 4.9, 1.5, 1),
            ard(5.7, 2.8, 4.5, 1.3, 1),
            ard(5.6, 2.8, 4.9, 2.0, 2),
            ard(5.0, 3.4, 1.6, 0.4, 0),
            ard(6.0, 2.2, 5.0, 1.5, 2));
    double[][] iris_expandR = ard(ard(0, 1, 0, 6.3, 2.5, 4.9, 1.5),
            ard(0, 1, 0, 5.7, 2.8, 4.5, 1.3),
            ard(0, 0, 1, 5.6, 2.8, 4.9, 2.0),
            ard(1, 0, 0, 5.0, 3.4, 1.6, 0.4),
            ard(0, 0, 1, 6.0, 2.2, 5.0, 1.5));
    String[] iris_cols = new String[] {"sepal_len", "sepal_wid", "petal_len", "petal_wid", "class"};
    String[][] iris_domains = new String[][] { null, null, null, null, new String[] {"setosa", "versicolor", "virginica"} };

    Frame fr = null;
    try {
      fr = parse_test_file(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
      DataInfo dinfo = new DataInfo(Key.make(), fr, null, 0, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, false, false, false, /* weights */ false, /* offset */ false, /* fold */ false);

      Log.info("Original matrix:\n" + colFormat(iris_cols, "%8.7s") + ArrayUtils.pprint(iris));
      double[][] iris_perm = ArrayUtils.permuteCols(iris, dinfo._permutation);
      Log.info("Permuted matrix:\n" + colFormat(iris_cols, "%8.7s", dinfo._permutation) + ArrayUtils.pprint(iris_perm));

      double[][] iris_exp = GLRM.expandCats(iris_perm, dinfo);
      Log.info("Expanded matrix:\n" + colExpFormat(iris_cols, iris_domains, "%8.7s", dinfo._permutation) + ArrayUtils.pprint(iris_exp));
      Assert.assertArrayEquals(iris_expandR, iris_exp);
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (fr != null) fr.delete();
    }
  }

  @Test public void testExpandCatsProstate() throws InterruptedException, ExecutionException {
    double[][] prostate = ard(ard(0, 71, 1, 0, 0,  4.8, 14.0, 7),
            ard(1, 70, 1, 1, 0,  8.4, 21.8, 5),
            ard(0, 73, 1, 3, 0, 10.0, 27.4, 6),
            ard(1, 68, 1, 0, 0,  6.7, 16.7, 6));
    double[][] pros_expandR = ard(ard(1, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 71,  4.8, 14.0, 7),
            ard(0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 70,  8.4, 21.8, 5),
            ard(0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 73, 10.0, 27.4, 6),
            ard(1, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 68,  6.7, 16.7, 6));
    String[] pros_cols = new String[]{"Capsule", "Age", "Race", "Dpros", "Dcaps", "PSA", "Vol", "Gleason"};
    String[][] pros_domains = new String[][]{new String[]{"No", "Yes"}, null, new String[]{"Other", "White", "Black"},
            new String[]{"None", "UniLeft", "UniRight", "Bilobar"}, new String[]{"No", "Yes"}, null, null, null};
    final int[] cats = new int[]{1,3,4,5};    // Categoricals: CAPSULE, RACE, DPROS, DCAPS

    Frame fr = null;
    try {
      Scope.enter();
      fr = parse_test_file(Key.make("prostate.hex"), "smalldata/logreg/prostate.csv");
      for(int i = 0; i < cats.length; i++)
        Scope.track(fr.replace(cats[i], fr.vec(cats[i]).toEnum())._key);
      fr.remove("ID").remove();
      DKV.put(fr._key, fr);
      DataInfo dinfo = new DataInfo(Key.make(), fr, null, 0, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, false, false, false, /* weights */ false, /* offset */ false, /* fold */ false);

      Log.info("Original matrix:\n" + colFormat(pros_cols, "%8.7s") + ArrayUtils.pprint(prostate));
      double[][] pros_perm = ArrayUtils.permuteCols(prostate, dinfo._permutation);
      Log.info("Permuted matrix:\n" + colFormat(pros_cols, "%8.7s", dinfo._permutation) + ArrayUtils.pprint(pros_perm));

      double[][] pros_exp = GLRM.expandCats(pros_perm, dinfo);
      Log.info("Expanded matrix:\n" + colExpFormat(pros_cols, pros_domains, "%8.7s", dinfo._permutation) + ArrayUtils.pprint(pros_exp));
      Assert.assertArrayEquals(pros_expandR, pros_exp);
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (fr != null) fr.delete();
      Scope.exit();
    }
  }
}
