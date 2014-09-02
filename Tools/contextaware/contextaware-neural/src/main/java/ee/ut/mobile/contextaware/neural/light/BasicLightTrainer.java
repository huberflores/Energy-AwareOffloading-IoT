package ee.ut.mobile.contextaware.neural.light;

import ee.ut.mobile.contextaware.h2.DAOManager;
import ee.ut.mobile.contextaware.h2.DataSourceFactory;

import static ee.ut.mobile.contextaware.neural.util.Constant.*;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.encog.ConsoleStatusReportable;
import org.encog.Encog;
import org.encog.engine.network.activation.ActivationTANH;
import org.encog.mathutil.error.ErrorCalculation;
import org.encog.mathutil.error.ErrorCalculationMode;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.folded.FoldedDataSet;
import org.encog.ml.train.MLTrain;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.cross.CrossValidationKFold;
import org.encog.neural.networks.training.lma.LevenbergMarquardtTraining;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.neural.pattern.FeedForwardPattern;
import org.encog.neural.prune.PruneIncremental;
import org.encog.persist.EncogDirectoryPersistence;
import org.encog.util.EngineArray;
import org.encog.util.arrayutil.NormalizeArray;
import org.encog.util.arrayutil.TemporalWindowArray;

import java.io.File;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.*;


public class BasicLightTrainer {
    public long startingTime = 0;
    public Timestamp startingTimeStamp;
    public long lastTime = 0;
    public long size = 0;
    public final static int WINDOW_SIZE = 30;
    private NormalizeArray norm;
    public final static double MAX_ERROR = 0.01;
    Map<Timestamp, Double> lights;
    private double[] normalizedLight;
    private List<Timestamp> timing;
    private double[] closedLoopLight;
    private Timestamp lastTimeStamp;
    private double median;

    public void normalizeLightWithTimeBetween(double lo, double hi, Timestamp start, Timestamp stop) {
        lights = DAOManager.getDataWithTimeBetween(DataSourceFactory.getDataSource(), LIGHT_ID, start, stop);

        size = lights.size();

        Set<Timestamp> times = lights.keySet();

        startingTime = ((Timestamp) ((TreeMap) lights).firstKey()).getTime();
        startingTimeStamp = ((Timestamp) ((TreeMap) lights).firstKey());
        lastTime = ((Timestamp) ((TreeMap) lights).lastKey()).getTime();
        lastTimeStamp = (Timestamp) ((TreeMap) lights).lastKey();


        DescriptiveStatistics destats = new DescriptiveStatistics();

        for (Timestamp t : times) {
            destats.addValue(lights.get(t));
        }

        median = destats.getPercentile(50);

        List<Double> cleanData = addMissingData();
        Double[] tempArray = new Double[cleanData.size()];
        cleanData.toArray(tempArray);

        double[] tempPrimitiveArray = ArrayUtils.toPrimitive(tempArray);

        norm = new NormalizeArray();
        norm.setNormalizedHigh(hi);
        norm.setNormalizedLow(lo);

        // create arrays to hold the normalized temperature
        normalizedLight = norm.process(tempPrimitiveArray);
        closedLoopLight = EngineArray.arrayCopy(normalizedLight);

    }


    public List<Double> addMissingData() {
        List<Double> temperatures = new ArrayList<Double>();
        timing = new ArrayList<Timestamp>();
        Timestamp newTimeStamp = startingTimeStamp;

        while (newTimeStamp.before(lastTimeStamp)) {
            temperatures.add(lights.get(newTimeStamp) != null ? lights.get(newTimeStamp) : median);
            timing.add(newTimeStamp);

            //add one second
            int sec = 1000;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(newTimeStamp.getTime());
            cal.add(Calendar.MILLISECOND, sec);
            newTimeStamp = new Timestamp(cal.getTime().getTime());
        }
        return temperatures;
    }

    public MLDataSet generateTraining() {
        TemporalWindowArray temp = new TemporalWindowArray(WINDOW_SIZE, 1);
        temp.analyze(this.normalizedLight);
        return temp.process(this.normalizedLight);
    }


    public void trainLMA(BasicNetwork network, MLDataSet training) {

        final MLTrain train = new LevenbergMarquardtTraining(network, training);


        int epoch = 1;

        do {
            train.iteration();
            System.out.println("Epoch #" + epoch + " Error:" + train.getError());
            epoch++;
        } while (train.getError() > MAX_ERROR);

        //save network file
        File networkFile = new File("NNLMA" + getClass().getSimpleName().replace("er", "ed") + System.currentTimeMillis());
        EncogDirectoryPersistence.saveObject(networkFile, network);
    }


    public void train(BasicNetwork network, MLDataSet training) {
        final FoldedDataSet folded = new FoldedDataSet(training);
        final MLTrain train = new ResilientPropagation(network, folded);
        final CrossValidationKFold trainFolded = new CrossValidationKFold(train, 4);

        int epoch = 1;

        do {
            trainFolded.iteration();
            System.out.println("Epoch #" + epoch + " Error:" + trainFolded.getError());
            epoch++;
        } while (trainFolded.getError() > MAX_ERROR);

        //save network file
        long time = System.currentTimeMillis();
        File networkFile = new File("NNVALIDATE" + getClass().getSimpleName().replace("er","ed") + time);
        System.out.println("***********************************************");
        System.out.println("NNVALIDATE" + getClass().getSimpleName().replace("er","ed") + time);
        System.out.println("***********************************************");
        EncogDirectoryPersistence.saveObject(networkFile, network);
    }


    public BasicNetwork pruneNetwork(MLDataSet training) {
        FeedForwardPattern pattern = new FeedForwardPattern();
        pattern.setInputNeurons(training.getInputSize());
        pattern.setOutputNeurons(training.getIdealSize());
        pattern.setActivationFunction(new ActivationTANH());

        PruneIncremental prune = new PruneIncremental(training, pattern, 100, 1, 10, new ConsoleStatusReportable());

        prune.addHiddenLayer(5, 47);
        prune.addHiddenLayer(0, 10);

        prune.process();
        return prune.getBestNetwork();
    }


    public void predict(BasicNetwork network) {
        NumberFormat f = NumberFormat.getNumberInstance();
        f.setMaximumFractionDigits(4);
        f.setMinimumFractionDigits(4);
        ErrorCalculation ec = new ErrorCalculation();
        ec.reset();

        System.out.println("Year\tActual\tPredict\tClosed Loop Predict");
        int start = new Float(timing.size() * (9.9f / 10.0f)).intValue();
        for (int evalutingpoint = start; evalutingpoint < timing.size(); evalutingpoint++) {

            // calculate based on actual data
            MLData input = new BasicMLData(WINDOW_SIZE);
            for (int i = 0; i < input.size(); i++) {
                input.setData(i, this.normalizedLight[(evalutingpoint - WINDOW_SIZE) + i]);
            }

            MLData output = network.compute(input);
            double prediction = output.getData(0);
            this.closedLoopLight[evalutingpoint] = prediction;

            // calculate "closed loop", based on predicted data
            for (int i = 0; i < input.size(); i++) {
                input.setData(i, this.closedLoopLight[(evalutingpoint - WINDOW_SIZE) + i]);
            }

            output = network.compute(input);
            double closedLoopPrediction = output.getData(0);

            //ErrorCalculation
            ec.updateError(prediction,normalizedLight[evalutingpoint]);

            // display
            System.out.println(timing.get(evalutingpoint) + "\t"
                    + f.format(norm.getStats().deNormalize(this.normalizedLight[evalutingpoint])) + "\t"
                    + f.format(norm.getStats().deNormalize(prediction)) + "\t"
                    + f.format(norm.getStats().deNormalize(closedLoopPrediction)));

        }
        System.out.println("Test RMSE :" + ec.calculateRMS());

    }

    public void run() {

        Calendar start = Calendar.getInstance();
        start.set(2014, Calendar.MAY, 17, 20, 30, 17);
        Calendar later = Calendar.getInstance();
        later.set(2014, Calendar.MAY, 17, 20, 50, 2);

        normalizeLightWithTimeBetween(0.1, 0.9, new Timestamp(start.getTime().getTime()),
                new Timestamp(later.getTime().getTime()));


        MLDataSet training = generateTraining();
        BasicNetwork network = pruneNetwork(training);
        BasicNetwork cloneForLMA = (BasicNetwork) network.clone();

        train(network, training);
        ErrorCalculation.setMode(ErrorCalculationMode.RMS);
        System.out.println("RMSE Normal: " + network.calculateError(training));
        predict(network);


       /* trainLMA(cloneForLMA, training);
        ErrorCalculation.setMode(ErrorCalculationMode.RMS);
        System.out.println("RMSE LMA: " + cloneForLMA.calculateError(training));
        predict(cloneForLMA);*/

    }

    public static void main(String[] args) {
        BasicLightTrainer basicLightTrainer = new BasicLightTrainer();
        basicLightTrainer.run();
        Encog.getInstance().shutdown();
    }
}
