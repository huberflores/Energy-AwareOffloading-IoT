package ee.ut.mobile.contextaware.neural.light;

import ee.ut.mobile.contextaware.h2.DAOManager;
import ee.ut.mobile.contextaware.h2.DataSourceFactory;

import static ee.ut.mobile.contextaware.neural.util.Constant.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.encog.Encog;
import org.encog.mathutil.error.ErrorCalculation;
import org.encog.mathutil.error.ErrorCalculationMode;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.neural.neat.NEATNetwork;
import org.encog.neural.neat.NEATPopulation;
import org.encog.neural.neat.training.NEATTraining;
import org.encog.neural.networks.training.CalculateScore;
import org.encog.neural.networks.training.TrainingSetScore;
import org.encog.persist.EncogDirectoryPersistence;
import org.encog.util.arrayutil.NormalizeArray;
import org.encog.util.arrayutil.TemporalWindowArray;
import org.encog.util.simple.EncogUtility;

import java.io.File;
import java.sql.Timestamp;
import java.util.*;


public class NeatLightTrainer {
    public long startingTime = 0;
    public Timestamp startingTimeStamp;
    public long lastTime = 0;
    public long size = 0;
    public final static int WINDOW_SIZE = 30;

    Map<Timestamp, Double> lights;
    private double[] normalizedLight;
    private List<Timestamp> timing;
    private ErrorCalculation errorCalculation = new ErrorCalculation();
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

        NormalizeArray norm = new NormalizeArray();
        norm.setNormalizedHigh(hi);
        norm.setNormalizedLow(lo);

        // create arrays to hold the normalized temperature
        normalizedLight = norm.process(tempPrimitiveArray);
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


    public void run() {

        Calendar start = Calendar.getInstance();
        start.set(2014, Calendar.MAY, 17, 20, 30, 17);
        Calendar later = Calendar.getInstance();
        later.set(2014, Calendar.MAY, 17, 20, 50, 2);

        normalizeLightWithTimeBetween(0.1, 0.9, new Timestamp(start.getTime().getTime()),
                new Timestamp(later.getTime().getTime()));


        MLDataSet trainingSet = generateTraining();
        NEATPopulation pop = new NEATPopulation(2,1,1000);
        CalculateScore score = new TrainingSetScore(trainingSet);
        final NEATTraining train = new NEATTraining(score,pop);
        NEATNetwork network = (NEATNetwork)train.getMethod();

        EncogUtility.trainToError(train, 0.01);
        ErrorCalculation.setMode(ErrorCalculationMode.RMS);
        double rre = network.calculateError(trainingSet);



        //save network file
        long time = System.currentTimeMillis();
        File networkFile = new File("NNVALIDATE" + getClass().getSimpleName().replace("er","ed") + time);
        System.out.println("***********************************************");
        System.out.println("NNVALIDATE" + getClass().getSimpleName().replace("er","ed") + time);
        System.out.println("***********************************************");

        System.out.println("Neural Network Results:");
        EncogUtility.evaluate(network, trainingSet);
        System.out.println("Error : "+ network.calculateError(trainingSet));

        for (final MLDataPair pair : trainingSet) {
            final MLData output = network.compute(pair.getInput());

            errorCalculation.updateError(output.getData(), pair.getIdeal().getData(),1.0);

            System.out.println("Input="
                    + EncogUtility.formatNeuralData(pair.getInput())
                    + ", Actual=" + EncogUtility.formatNeuralData(output)
                    + ", Ideal="
                    + EncogUtility.formatNeuralData(pair.getIdeal()));

        }

        System.out.println("Test RMSE: " + errorCalculation.calculateRMS());
        System.out.println("Training RMSE : " + rre);

        EncogDirectoryPersistence.saveObject(networkFile, network);

    }

    public static void main(String[] args) {
        NeatLightTrainer neatLightTrainer = new NeatLightTrainer();
        neatLightTrainer.run();
        Encog.getInstance().shutdown();
    }
}
