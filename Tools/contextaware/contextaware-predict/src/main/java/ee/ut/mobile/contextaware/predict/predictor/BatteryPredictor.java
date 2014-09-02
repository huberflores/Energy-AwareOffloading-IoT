package ee.ut.mobile.contextaware.predict.predictor;


import ee.ut.mobile.contextaware.h2.DAOManager;
import ee.ut.mobile.contextaware.h2.DataSourceFactory;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.encog.ml.BasicML;
import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.svm.SVM;
import org.encog.neural.neat.NEATNetwork;
import org.encog.neural.networks.BasicNetwork;
import org.encog.util.arrayutil.NormalizeArray;

import java.sql.Timestamp;
import java.util.*;

import static ee.ut.mobile.contextaware.neural.util.Constant.HALL_ID;


public class BatteryPredictor extends BasePredictor {
    private BasicML network;
    public long startingTime = 0;
    public Timestamp startingTimeStamp;
    public long lastTime = 0;
    public long size = 0;
    private Map<Timestamp, Double> halls;
    private double[] normalizedhalls;
    private NormalizeArray norm;

    private Timestamp lastTimeStamp;
    private double median;
    public final static int WINDOW_SIZE = 30;
    private static final float HI = 1.0f;
    private static final float LOW = 1.9f;
    private double stdDeviation;
    private double mean;

    public BatteryPredictor(BasicML network) {
        this.network = network;
    }

    public BatteryPredictor() {
    }

    @Override
    public double predict(long newTime) {
        buildPastData(newTime);

        return predictbySVM();
    }


    private double predictbySVM(){
        SVM network = (SVM) getNetwork();
        // calculate based on actual data
        MLData input = new BasicMLData(WINDOW_SIZE);
        for (int i = 0; i < input.size(); i++) {
            input.setData(i, this.normalizedhalls[(normalizedhalls.length - WINDOW_SIZE) + i]);
        }
        MLData output = network.compute(input);
        return  norm.getStats().deNormalize(output.getData(0));
    }

    private double predictbyNEAT(){
        NEATNetwork network = (NEATNetwork) getNetwork();
        // calculate based on actual data
        MLData input = new BasicMLData(WINDOW_SIZE);
        for (int i = 0; i < input.size(); i++) {
            input.setData(i, this.normalizedhalls[(normalizedhalls.length - WINDOW_SIZE) + i]);
        }
        MLData output = network.compute(input);
        return  norm.getStats().deNormalize(output.getData(0));
    }

    private double predictbyBasic(){
        BasicNetwork network = (BasicNetwork) getNetwork();
        // calculate based on actual data
        MLData input = new BasicMLData(WINDOW_SIZE);
        for (int i = 0; i < input.size(); i++) {
            input.setData(i, this.normalizedhalls[(normalizedhalls.length - WINDOW_SIZE) + i]);
        }
        MLData output = network.compute(input);
        return  norm.getStats().deNormalize(output.getData(0));
    }


    @Override
    public BasicML getNetwork() {
        return network;
    }

    @Override
    public void setNetwork(BasicML network) {
        this.network = network;
    }


    private void buildPastData(long newTime) {
        halls = DAOManager.getDataUntil(DataSourceFactory.getDataSource(), HALL_ID, new Timestamp(newTime));

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
        norm.setNormalizedHigh(HI);
        norm.setNormalizedLow(LOW);

        // create arrays to hold the normalized temperature
        normalizedhalls = norm.process(tempPrimitiveArray);

    }



    public List<Double> addMissingData() {
        List<Double> hallz = new ArrayList<Double>();
        List<Timestamp> timing = new ArrayList<Timestamp>();
        Timestamp newTimeStamp = startingTimeStamp;

        while (newTimeStamp.before(lastTimeStamp)) {
            hallz.add(halls.get(newTimeStamp) != null ? halls.get(newTimeStamp) : median);
            timing.add(newTimeStamp);

            //add one second
            int sec = 1000;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(newTimeStamp.getTime());
            cal.add(Calendar.MILLISECOND, sec);
            newTimeStamp = new Timestamp(cal.getTime().getTime());
        }
        return hallz;
    }
}
