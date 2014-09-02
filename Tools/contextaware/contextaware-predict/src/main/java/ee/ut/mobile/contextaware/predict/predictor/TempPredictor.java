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

import static ee.ut.mobile.contextaware.neural.util.Constant.LIGHT_ID;


public class TempPredictor extends BasePredictor {
    private BasicML network;
    public long startingTime = 0;
    public Timestamp startingTimeStamp;
    public long lastTime = 0;
    public long size = 0;
    private Map<Timestamp, Double> temps;
    private double[] normalizedTemp;
    private NormalizeArray norm;

    private Timestamp lastTimeStamp;
    private double median;
    public final static int WINDOW_SIZE = 30;
    private static final float HI = 1.5f;
    private static final float LOW = 1.0f;
    private double stdDeviation;
    private double mean;

    public TempPredictor(BasicML network) {
        this.network = network;
    }

    public TempPredictor() {
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
            input.setData(i, this.normalizedTemp[(normalizedTemp.length - WINDOW_SIZE) + i]);
        }
        MLData output = network.compute(input);
        return  norm.getStats().deNormalize(output.getData(0));
    }

    private double predictbyNEAT(){
        NEATNetwork network = (NEATNetwork) getNetwork();
        // calculate based on actual data
        MLData input = new BasicMLData(WINDOW_SIZE);
        for (int i = 0; i < input.size(); i++) {
            input.setData(i, this.normalizedTemp[(normalizedTemp.length - WINDOW_SIZE) + i]);
        }
        MLData output = network.compute(input);
        return  norm.getStats().deNormalize(output.getData(0));
    }

    private double predictbyBasic(){
        BasicNetwork network = (BasicNetwork) getNetwork();
        // calculate based on actual data
        MLData input = new BasicMLData(WINDOW_SIZE);
        for (int i = 0; i < input.size(); i++) {
            input.setData(i, this.normalizedTemp[(normalizedTemp.length - WINDOW_SIZE) + i]);
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
        temps = DAOManager.getDataUntil(DataSourceFactory.getDataSource(), LIGHT_ID, new Timestamp(newTime));

        size = temps.size();

        Set<Timestamp> times = temps.keySet();

        startingTime = ((Timestamp) ((TreeMap) temps).firstKey()).getTime();
        startingTimeStamp = ((Timestamp) ((TreeMap) temps).firstKey());
        lastTime = ((Timestamp) ((TreeMap) temps).lastKey()).getTime();
        lastTimeStamp = (Timestamp) ((TreeMap) temps).lastKey();


        DescriptiveStatistics destats = new DescriptiveStatistics();
        SummaryStatistics ss = new SummaryStatistics();

        for (Timestamp t : times) {
            destats.addValue(temps.get(t));
            ss.addValue(temps.get(t));
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
        normalizedTemp = norm.process(tempPrimitiveArray);

    }



    public List<Double> addMissingData() {
        List<Double> tempz = new ArrayList<Double>();
        List<Timestamp> timing = new ArrayList<Timestamp>();
        Timestamp newTimeStamp = startingTimeStamp;

        while (newTimeStamp.before(lastTimeStamp)) {
            tempz.add(temps.get(newTimeStamp) != null ? temps.get(newTimeStamp) : median);
            timing.add(newTimeStamp);

            //add one second
            int sec = 1000;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(newTimeStamp.getTime());
            cal.add(Calendar.MILLISECOND, sec);
            newTimeStamp = new Timestamp(cal.getTime().getTime());
        }
        return tempz;
    }
}
