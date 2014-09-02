package ee.ut.mobile.contextaware.neural.hall;

import ee.ut.mobile.contextaware.h2.DAOManager;
import ee.ut.mobile.contextaware.h2.DataSourceFactory;

import static ee.ut.mobile.contextaware.neural.util.Constant.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.encog.Encog;
import org.encog.mathutil.error.ErrorCalculation;
import org.encog.mathutil.error.ErrorCalculationMode;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.svm.SVM;
import org.encog.ml.svm.training.SVMTrain;
import org.encog.persist.EncogDirectoryPersistence;
import org.encog.util.EngineArray;
import org.encog.util.arrayutil.NormalizeArray;
import org.encog.util.arrayutil.TemporalWindowArray;

import java.io.File;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.*;


public class SVMHallTrainer {
    public long startingTime = 0;
    public Timestamp startingTimeStamp;
    public long lastTime = 0;
    public long size = 0;
    public final static int WINDOW_SIZE = 30;
    NormalizeArray norm;
    private Map<Timestamp, Double> halls;
    private double[] normalizedHall;
    private List<Timestamp> timing;
    private double[] closedLoopHall;
    private Timestamp lastTimeStamp;
    private double median;
    private double stdDeviation;
    private double mean;


    public void normalizeHallWithTimeBetween(double lo, double hi, Timestamp start, Timestamp stop) {
        halls = DAOManager.getDataWithTimeBetween(DataSourceFactory.getDataSource(), HALL_ID, start, stop);

        size = halls.size();

        Set<Timestamp> times = halls.keySet();

        startingTime = ((Timestamp) ((TreeMap) halls).firstKey()).getTime();
        startingTimeStamp = ((Timestamp) ((TreeMap) halls).firstKey());
        lastTime = ((Timestamp) ((TreeMap) halls).lastKey()).getTime();
        lastTimeStamp = (Timestamp) ((TreeMap) halls).lastKey();


        DescriptiveStatistics destats = new DescriptiveStatistics();
        SummaryStatistics ss = new SummaryStatistics();

        for (Timestamp t : times) {
            destats.addValue(halls.get(t));
            ss.addValue(halls.get(t));
        }

        median = destats.getPercentile(50);
        stdDeviation = destats.getStandardDeviation();
        mean = ss.getMean();

        List<Double> cleanData = addMissingData();
        Double[] tempArray = new Double[cleanData.size()];
        cleanData.toArray(tempArray);

        double[] tempPrimitiveArray = ArrayUtils.toPrimitive(tempArray);

        norm = new NormalizeArray();
        norm.setNormalizedHigh(hi);
        norm.setNormalizedLow(lo);

        // create arrays to hold the normalized temperature
        normalizedHall = norm.process(tempPrimitiveArray);
        double[] test = norm.process(tempPrimitiveArray);
        closedLoopHall = EngineArray.arrayCopy(normalizedHall);
    }


    public List<Double> addMissingData() {
        List<Double> temperatures = new ArrayList<Double>();
        timing = new ArrayList<Timestamp>();
        Timestamp newTimeStamp = startingTimeStamp;

        while (newTimeStamp.before(lastTimeStamp)) {
            temperatures.add(halls.get(newTimeStamp) != null ? halls.get(newTimeStamp) : median);
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
        temp.analyze(this.normalizedHall);
        return temp.process(this.normalizedHall);
    }

    public SVM createNetwork() {
        SVM network = new SVM(WINDOW_SIZE, true);
        network.getParams().eps = 3 * stdDeviation  * Math.sqrt(Math.log(normalizedHall.length)/normalizedHall.length);
        network.getParams().C = Math.max((Math.abs((3 * stdDeviation) + mean)) , (Math.abs(-(3 * stdDeviation) + mean)));
        return network;
    }

    public void train(SVM network, MLDataSet training) {
        final SVMTrain train = new SVMTrain(network, training);
        train.iteration();
        long time = System.currentTimeMillis();
        File networkFile = new File("data/NN" + getClass().getSimpleName().replace("er","ed") + time);
        System.out.println("***********************************************");
        System.out.println("NN" + getClass().getSimpleName().replace("er","ed") + time);
        System.out.println("***********************************************");
        EncogDirectoryPersistence.saveObject(networkFile, train.getMethod());
    }

    public void predict(SVM network) {
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
                input.setData(i, this.normalizedHall[(evalutingpoint - WINDOW_SIZE) + i]);
            }

            MLData output = network.compute(input);
            double prediction = output.getData(0);
            this.closedLoopHall[evalutingpoint] = prediction;

            // calculate "closed loop", based on predicted data
            for (int i = 0; i < input.size(); i++) {
                input.setData(i, this.closedLoopHall[(evalutingpoint - WINDOW_SIZE) + i]);
            }

            output = network.compute(input);
            double closedLoopPrediction = output.getData(0);

            //ErrorCalculation
            ec.updateError(prediction,normalizedHall[evalutingpoint]);



            // display
            System.out.println(timing.get(evalutingpoint) + "\t"
                    + f.format(norm.getStats().deNormalize(this.normalizedHall[evalutingpoint])) + "\t"
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

        normalizeHallWithTimeBetween(0.1, 0.9, new Timestamp(start.getTime().getTime()),
                new Timestamp(later.getTime().getTime()));


        SVM network = createNetwork();
        MLDataSet training = generateTraining();

        train(network, training);
        ErrorCalculation.setMode(ErrorCalculationMode.RMS);
        System.out.println("Mode : " + ErrorCalculation.getMode());
        System.out.println("Training RMSE: " + network.calculateError(training));

        predict(network);



    }

    public static void main(String[] args) {
        SVMHallTrainer svmHallTrainer = new SVMHallTrainer();
        svmHallTrainer.run();
        Encog.getInstance().shutdown();
    }
}
