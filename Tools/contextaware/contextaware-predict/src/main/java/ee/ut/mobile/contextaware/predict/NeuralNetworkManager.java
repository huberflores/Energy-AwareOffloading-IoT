package ee.ut.mobile.contextaware.predict;

import ee.ut.mobile.contextaware.h2.DAOManager;
import ee.ut.mobile.contextaware.h2.DataSourceFactory;
import ee.ut.mobile.contextaware.h2.data.Data;
import ee.ut.mobile.contextaware.h2.data.Sensor;
import ee.ut.mobile.contextaware.h2.data.SensorData;
import ee.ut.mobile.contextaware.neural.util.Constant;
import ee.ut.mobile.contextaware.predict.predictor.*;
import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.TDistribution;
import org.apache.commons.math.distribution.TDistributionImpl;
import org.apache.commons.math.stat.regression.SimpleRegression;
import org.apache.log4j.Logger;
import org.encog.ml.svm.SVM;
import org.encog.persist.EncogDirectoryPersistence;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Timestamp;
import java.util.*;

public class NeuralNetworkManager {

    public static final double PREDICTABILITY_THRESHOLD = 0.9;

    private Logger log = Logger.getLogger(this.getClass());
    private DataSource dataSource;
    private Map<Integer, Sensor> sensorMap;

    public NeuralNetworkManager(DataSource source, Map<Integer, Sensor> sensorMap) {
        this.dataSource = source;
        this.sensorMap = sensorMap;
    }

    public Map<Integer, PredictionData> createPredictionData(SensorData receivedSensorData) {
        HashMap<Integer, PredictionData> result = new HashMap<Integer, PredictionData>();
        for (Data data : receivedSensorData.getData()) {
            log.debug("Predicting data, new Data: " + data);
            PredictionData prediction = new PredictionData();
            Sensor sensor = sensorMap.get(data.getType());
            if (sensor == null) {
                log.error("Sensor of type " + data.getType() + " not found in sensorMap");
                continue;
            }
            prediction.predictability = calculatePredictability(sensor.getRmse(), sensor.getRegressionError());
            prediction.lastData = DAOManager.getLastDataByTypeId(dataSource, data.getType());    // Last value
            prediction.location = receivedSensorData.getLocation();
            prediction.measuredData = data;
            prediction.type = data.getType();
            result.put(data.getType(), prediction);
        }
        return result;
    }

    /**
     * @param predictions
     * @param time
     * @return
     */
    public boolean predictData(int time, PredictionData... predictions) {
        if (time == 0) {
            log.debug("predictData called with time= " + time + " which is too small");
            return false;
        }
        List<Data> predictedData = new ArrayList<Data>();
        int location = -1;

        for (PredictionData pred : predictions) {
            if (location != -1 && location != pred.getLocation()) {
                log.warn("predictData called but PredictionData locations do not match");
                return false;
            }
            location = pred.getLocation();

            if (pred.predictability < PREDICTABILITY_THRESHOLD) {
                // We will not predict anything
                return false;
            }
            predictedData.addAll(createPredictions(pred, time));
        }
        log.debug(predictedData.size() + " predictions total");
        for (Data prediction : predictedData) {
            DAOManager.insertSensorData(dataSource, location, prediction);
        }
        return true;
    }

    private double calculatePredictability(SimpleRegression regression, double error) {
        try {
            log.debug("Regression error = " + error);
            double rmse = Math.sqrt(regression.getMeanSquareError());
            regression.getRegressionSumSquares();
            regression.getSumSquaredErrors();
            log.debug("Regression root mean square error " + rmse);
            TDistribution dist = new TDistributionImpl(regression.getN() - 2);
            double alphaM = 2.0 * dist.cumulativeProbability(error / rmse) - 1;
            log.debug("Regression alphaM = " + alphaM);
            return alphaM;
        } catch (MathException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private double calculatePredictability(double rmse, double error) {
        try {
            log.debug("Regression error = " + error);
            TDistribution dist = new TDistributionImpl(Constant.TEST_AMOUNT - 2);
            double alphaM = 2.0 * dist.cumulativeProbability(error / rmse) - 1;
            log.debug("Regression alphaM = " + alphaM);
            return alphaM;
        } catch (MathException e) {
            log.debug(e);
            return 0;
        }
    }

    private List<Data> createPredictions(PredictionData data, int time) {
        List<Data> predictions = new ArrayList<Data>();
        int step = 10; // 10 seconds
        int count = Math.max((time / 10), 0);
        long baseTime = data.getMeasuredData().getMeasureTime().getTime();
        for (int index = 1; index < count; index++) {
            long newTime = baseTime + step * index * 1000;
            double newValue = predictValueFor(data.getType(), newTime);
            Data dataDao = new Data(data.getMeasuredData().getType(), newValue);
            dataDao.setMeasureTime(new Timestamp(newTime));
            dataDao.setMeasured(false);
            predictions.add(dataDao);
        }
        return predictions;
    }

    private double predictValueFor(int type, long time) {
        double result = 0.0;
        switch (type) {
            case Sensor.TYPE_TEMPERATURE: //1
                SVM tntw = (SVM) EncogDirectoryPersistence.loadObject(new File(getClass().getClassLoader().getResource(Constant.TEMP_SVM_FILENAME).getPath()));
                result = (new TempPredictor(tntw)).predict(time);
                break;
            case Sensor.TYPE_HALL: //2
                SVM ntwh = (SVM) EncogDirectoryPersistence.loadObject(new File(getClass().getClassLoader().getResource(Constant.HALL_SVM_FILENAME).getPath()));
                result = (new HallPredictor(ntwh)).predict(time);
                break;
            case Sensor.TYPE_LIGHT: //3
                SVM lntw =
                        (SVM) EncogDirectoryPersistence
                                .loadObject(new File(getClass().getClassLoader().getResource(Constant.LIGHT_SVM_FILENAME).getPath()));
                result = (new LightPredictor(lntw)).predict(time);
                break;
            case Sensor.TYPE_VOLTAGE: //4
                SVM vntw = (SVM) EncogDirectoryPersistence.loadObject(new File(getClass().getClassLoader().getResource(Constant.BATTERY_SVM_FILENAME).getPath()));
                result = (new BatteryPredictor(vntw)).predict(time);
                break;
            case Sensor.TYPE_CPU_USAGE: //5
                SVM cntw = (SVM) EncogDirectoryPersistence.loadObject(new File(getClass().getClassLoader().getResource(Constant.CPU_SVM_FILENAME).getPath()));
                result = (new LightPredictor(cntw)).predict(time);
                break;
            case Sensor.TYPE_ACCEL_X: //6
                SVM acelx = (SVM) EncogDirectoryPersistence.loadObject(new File(getClass().getClassLoader().getResource(Constant.ACCELX_SVM_FILENAME).getPath()));
                result = (new AccelxPredictor(acelx)).predict(time);
                break;
            case Sensor.TYPE_ACCEL_Y: // 7
                SVM acely = (SVM) EncogDirectoryPersistence.loadObject(new File(getClass().getClassLoader().getResource(Constant.ACCELY_SVM_FILENAME).getPath()));
                result = (new AccelyPredictor(acely)).predict(time);
                break;
            case Sensor.TYPE_BW: //8
                SVM bntw = (SVM) EncogDirectoryPersistence.loadObject(new File(getClass().getClassLoader().getResource(Constant.BW_SVM_FILENAME).getPath()));
                result = (new BWPredictor(bntw)).predict(time);
                break;
            default: //
                return result;
        }
        return result;
    }


    public static class PredictionData {

        public static int UNDEFINED = -1;

        private Data lastData;
        private Data measuredData;
        private double predictability; // in per cent
        private int location;
        private int type;

        public PredictionData() {
            //regression = new SimpleRegression();
            predictability = UNDEFINED;
            location = UNDEFINED;
            type = UNDEFINED;
        }


        public Data getLastData() {
            return lastData;
        }

        public Data getMeasuredData() {
            return measuredData;
        }

        public double getPredictability() {
            return predictability;
        }

        public int getLocation() {
            return location;
        }

        public int getType() {
            return type;
        }
    }

    public static void main1(String[] args) {
        new NeuralNetworkManager(DataSourceFactory.getDataSource(), new HashMap<Integer, Sensor>()).predictValueFor(3, new Date().getTime());
        System.out.println();
    }
}
