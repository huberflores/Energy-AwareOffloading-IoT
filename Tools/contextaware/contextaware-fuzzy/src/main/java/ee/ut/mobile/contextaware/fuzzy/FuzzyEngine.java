package ee.ut.mobile.contextaware.fuzzy;

import ee.ut.mobile.contextaware.fuzzy.comparator.FuzzyAND;
import ee.ut.mobile.contextaware.fuzzy.comparator.FuzzyOR;
import ee.ut.mobile.contextaware.fuzzy.controller.BasicFuzzyController;
import ee.ut.mobile.contextaware.fuzzy.deffuzifyer.PredictionDefuzzifierMethod;
import ee.ut.mobile.contextaware.fuzzy.functions.FunctionException;
import ee.ut.mobile.contextaware.fuzzy.functions.LinearMembershipFunction;
import ee.ut.mobile.contextaware.fuzzy.functions.SingleValueMembershipFunction;
import ee.ut.mobile.contextaware.fuzzy.functions.TrapezoidalMembershipFunction;
import ee.ut.mobile.contextaware.fuzzy.variables.Constants;
import ee.ut.mobile.contextaware.fuzzy.variables.FuzzySet;
import ee.ut.mobile.contextaware.fuzzy.variables.LinguisticVariable;
import ee.ut.mobile.contextaware.h2.data.Sensor;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.Scanner;

public class FuzzyEngine implements Constants {
    private BasicFuzzyController controller;
    private Map<Integer, Sensor> sensorMap;

    public FuzzyEngine(Map<Integer, Sensor> sensorMap) {
        this.sensorMap = sensorMap;
        controller = new BasicFuzzyController();
        controller.setDefuzzifyerMethod(new PredictionDefuzzifierMethod());
    }



    public int calculatePredictTime(DataHolder temp, DataHolder light,
                                    DataHolder hall,DataHolder bw,
                                    DataHolder volt, DataHolder accelx,
                                    DataHolder accely) {

        controller.fuzzify(LBL_TEMPERATURE, temp.getMeasuredValue());
        controller.fuzzify(LBL_TEMPERATURE_PREDICTABLE, temp.getPredictability());
        controller.fuzzify(LBL_LIGHT, light.getMeasuredValue());
        controller.fuzzify(LBL_LIGHT_PREDICTABLE, light.getPredictability());
        controller.fuzzify(LBL_HALL, hall.getMeasuredValue());
        controller.fuzzify(LBL_HALL_PREDICTABLE, hall.getPredictability());
        controller.fuzzify(LBL_BW, bw.getMeasuredValue());
        controller.fuzzify(LBL_BW_PREDICTABLE, bw.getPredictability());
        controller.fuzzify(LBL_VOLT, volt.getMeasuredValue());
        controller.fuzzify(LBL_VOLT_PREDICTABLE, volt.getPredictability());
        controller.fuzzify(LBL_ACCELX, accelx.getMeasuredValue());
        controller.fuzzify(LBL_ACCELX_PREDICTABLE, accelx.getPredictability());
        controller.fuzzify(LBL_ACCELY, accely.getMeasuredValue());
        controller.fuzzify(LBL_ACCELY_PREDICTABLE, accely.getPredictability());
        return (int) controller.defuzzify(LBL_RESULT);
    }

    public void initialize(double prevTemp,
                           double prevLight,
                           double prevHall,
                           double prevBw,
                           double prevVolt,
                           double prevaccelx,
                           double prevaccely) {


        Logger log = Logger.getLogger("FuzzyEngine");
        log.debug("Initializing temp = " + prevTemp + ", light = " + prevLight + ", hall = " + prevHall);

        Sensor tempSensor = sensorMap.get(Sensor.TYPE_TEMPERATURE);
        Sensor lightSensor = sensorMap.get(Sensor.TYPE_LIGHT);
        Sensor hallSensor = sensorMap.get(Sensor.TYPE_HALL);
        Sensor bwSensor = sensorMap.get(Sensor.TYPE_BW);
        Sensor voltSensor = sensorMap.get(Sensor.TYPE_VOLTAGE);
        Sensor accelxSensor = sensorMap.get(Sensor.TYPE_ACCEL_X);
        Sensor accelySensor = sensorMap.get(Sensor.TYPE_ACCEL_Y);

        double tempSensorError = tempSensor.getMeasureError();
        double tempChangeSlopeWidth = tempSensorError * 5;
        double tempSameSlopeWidth = tempSensorError * 5;

        LinguisticVariable temp = new LinguisticVariable(LBL_TEMPERATURE);
        FuzzySet tempChangedLeft = temp.addSet(createChangedLeftSet(LBL_SET_TEMP_CHANGED_LEFT, prevTemp, tempSensorError, tempChangeSlopeWidth));
        FuzzySet tempChangedRight = temp.addSet(createChangedRightSet(LBL_SET_TEMP_CHANGED_RIGHT, prevTemp, tempSensorError, tempChangeSlopeWidth));
        FuzzySet tempSame = temp.addSet(createSameSet(LBL_SET_TEMP_SAME, prevTemp, tempSameSlopeWidth, tempSensorError));
        controller.addVariable(temp);


        //bwSensor
        double bwSensorError = bwSensor.getMeasureError();
        double bwChangeSlopeWidth = bwSensorError * 5;
        double bwSameSlopeWidth = bwSensorError * 5;

        LinguisticVariable bw = new LinguisticVariable(LBL_BW);
        FuzzySet bwChangedLeft = bw.addSet(createChangedLeftSet(LBL_SET_BW_CHANGED_LEFT, prevBw, bwSensorError, bwChangeSlopeWidth));
        FuzzySet bwChangedRight = bw.addSet(createChangedRightSet(LBL_SET_BW_CHANGED_RIGHT, prevBw, bwSensorError, bwChangeSlopeWidth));
        FuzzySet bwSame = bw.addSet(createSameSet(LBL_SET_BW_SAME, prevBw, bwSameSlopeWidth, bwSensorError));
        controller.addVariable(bw);

        //voltSensor
        double voltSensorError = voltSensor.getMeasureError();
        double voltChangeSlopeWidth = voltSensorError * 5;
        double voltSameSlopeWidth = voltSensorError * 5;

        LinguisticVariable volt = new LinguisticVariable(LBL_VOLT);
        FuzzySet voltChangedLeft = volt.addSet(createChangedLeftSet(LBL_SET_VOLT_CHANGED_LEFT, prevVolt, voltSensorError, voltChangeSlopeWidth));
        FuzzySet voltChangedRight = volt.addSet(createChangedRightSet(LBL_SET_VOLT_CHANGED_RIGHT, prevVolt, voltSensorError, voltChangeSlopeWidth));
        FuzzySet voltSame = volt.addSet(createSameSet(LBL_SET_VOLT_SAME, prevVolt, voltSameSlopeWidth, voltSensorError));
        controller.addVariable(volt);


        //accelySensor
        double accelySensorError = accelySensor.getMeasureError();
        double accelyChangeSlopeWidth = accelySensorError * 5;
        double accelySameSlopeWidth = accelySensorError * 5;

        LinguisticVariable accely = new LinguisticVariable(LBL_ACCELY);
        FuzzySet accelyChangedLeft = accely.addSet(createChangedLeftSet(LBL_SET_ACCELY_CHANGED_LEFT, prevaccely, accelySensorError, accelyChangeSlopeWidth));
        FuzzySet accelyChangedRight = accely.addSet(createChangedRightSet(LBL_SET_ACCELY_CHANGED_RIGHT, prevaccely, accelySensorError, accelyChangeSlopeWidth));
        FuzzySet accelySame = accely.addSet(createSameSet(LBL_SET_ACCELY_SAME, prevaccely, accelySameSlopeWidth, accelySensorError));
        controller.addVariable(accely);

        //accelxSensor
        double accelxSensorError = accelxSensor.getMeasureError();
        double accelxChangeSlopeWidth = accelxSensorError * 5;
        double accelxSameSlopeWidth = accelxSensorError * 5;

        LinguisticVariable accelx = new LinguisticVariable(LBL_ACCELX);
        FuzzySet accelxChangedLeft = accelx.addSet(createChangedLeftSet(LBL_SET_ACCELX_CHANGED_LEFT, prevaccelx, accelxSensorError, accelxChangeSlopeWidth));
        FuzzySet accelxChangedRight = accelx.addSet(createChangedRightSet(LBL_SET_ACCELX_CHANGED_RIGHT, prevaccelx, accelxSensorError, accelxChangeSlopeWidth));
        FuzzySet accelxSame = accelx.addSet(createSameSet(LBL_SET_ACCELX_SAME, prevaccelx, accelxSameSlopeWidth, accelxSensorError));
        controller.addVariable(accelx);


        double lightSensorError = lightSensor.getMeasureError(); // To both sides
        double lightChangeSlopeWidth = lightSensorError * 5;
        double lightSameSlopeWidth = lightSensorError * 5;

        LinguisticVariable light = new LinguisticVariable(LBL_LIGHT);
        FuzzySet lightChangedLeft = light.addSet(createChangedLeftSet(LBL_SET_LIGHT_CHANGED_LEFT, prevLight, lightSensorError, lightChangeSlopeWidth));
        FuzzySet lightChangedRight = light.addSet(createChangedRightSet(LBL_SET_LIGHT_CHANGED_RIGHT, prevLight, lightSensorError, lightChangeSlopeWidth));
        FuzzySet lightSame = light.addSet(createSameSet(LBL_SET_LIGHT_SAME, prevLight, lightSameSlopeWidth, lightSensorError));
        controller.addVariable(light);

        double hallSensorError = hallSensor.getMeasureError(); // To both sides
        double hallChangeSlopeWidth = hallSensorError * 5;
        double hallSameSlopeWidth = hallSensorError * 5;

        LinguisticVariable hall = new LinguisticVariable(LBL_HALL);
        FuzzySet hallChangedLeft = hall.addSet(createChangedLeftSet(LBL_SET_HALL_CHANGED_LEFT, prevHall, hallSensorError, hallChangeSlopeWidth));
        FuzzySet hallChangedRight = hall.addSet(createChangedRightSet(LBL_SET_HALL_CHANGED_RIGHT, prevHall, hallSensorError, hallChangeSlopeWidth));
        FuzzySet hallSame = hall.addSet(createSameSet(LBL_SET_HALL_SAME, prevHall, hallSameSlopeWidth, hallSensorError));
        controller.addVariable(hall);

        // Add predictability sets here
        //temp
        LinguisticVariable tempPredictableVar = new LinguisticVariable(LBL_TEMPERATURE_PREDICTABLE);
        FuzzySet tempNotPredictable = tempPredictableVar.addSet(createNotPredictableSet(LBL_SET_TEMP_NOT_PREDICTABLE));
        FuzzySet tempPredictable = tempPredictableVar.addSet(createPredictableSet(LBL_SET_TEMP_PREDICTABLE));
        controller.addVariable(tempPredictableVar);

        //volt
        LinguisticVariable voltPredictableVar = new LinguisticVariable(LBL_VOLT_PREDICTABLE);
        FuzzySet voltNotPredictable = voltPredictableVar.addSet(createNotPredictableSet(LBL_SET_VOLT_NOT_PREDICTABLE));
        FuzzySet voltPredictable = voltPredictableVar.addSet(createPredictableSet(LBL_SET_VOLT_PREDICTABLE));
        controller.addVariable(voltPredictableVar);

        //accely
        LinguisticVariable accelyPredictableVar = new LinguisticVariable(LBL_ACCELY_PREDICTABLE);
        FuzzySet accelyNotPredictable = accelyPredictableVar.addSet(createNotPredictableSet(LBL_SET_ACCELY_NOT_PREDICTABLE));
        FuzzySet accelyPredictable = accelyPredictableVar.addSet(createPredictableSet(LBL_SET_ACCELY_PREDICTABLE));
        controller.addVariable(accelyPredictableVar);

        //accelx
        LinguisticVariable accelxPredictableVar = new LinguisticVariable(LBL_ACCELX_PREDICTABLE);
        FuzzySet accelxNotPredictable = accelyPredictableVar.addSet(createNotPredictableSet(LBL_SET_ACCELX_NOT_PREDICTABLE));
        FuzzySet accelxPredictable = accelyPredictableVar.addSet(createPredictableSet(LBL_SET_ACCELX_PREDICTABLE));
        controller.addVariable(accelxPredictableVar);

        //bw
        LinguisticVariable bwPredictableVar = new LinguisticVariable(LBL_BW_PREDICTABLE);
        FuzzySet bwNotPredictable = bwPredictableVar.addSet(createNotPredictableSet(LBL_SET_BW_NOT_PREDICTABLE));
        FuzzySet bwPredictable = bwPredictableVar.addSet(createPredictableSet(LBL_SET_BW_PREDICTABLE));
        controller.addVariable(bwPredictableVar);

        //light
        LinguisticVariable lightPredictableVar = new LinguisticVariable(LBL_LIGHT_PREDICTABLE);
        FuzzySet lightNotPredictable = lightPredictableVar.addSet(createNotPredictableSet(LBL_SET_LIGHT_NOT_PREDICTABLE));
        FuzzySet lightPredictable = lightPredictableVar.addSet(createPredictableSet(LBL_SET_LIGHT_PREDICTABLE));
        controller.addVariable(lightPredictableVar);

        LinguisticVariable hallPredictableVar = new LinguisticVariable(LBL_HALL_PREDICTABLE);
        FuzzySet hallNotPredictable = hallPredictableVar.addSet(createNotPredictableSet(LBL_SET_HALL_NOT_PREDICTABLE));
        FuzzySet hallPredictable = hallPredictableVar.addSet(createPredictableSet(LBL_SET_HALL_PREDICTABLE));
        controller.addVariable(hallPredictableVar);

        LinguisticVariable decision = new LinguisticVariable(LBL_RESULT);
        FuzzySet requestData = decision.addSet(LBL_SET_REQUEST, new SingleValueMembershipFunction(10));
        FuzzySet predictData = decision.addSet(LBL_SET_PREDICT, new LinearMembershipFunction(0, 65));
        controller.addVariable(decision);




        // Temperature rules
        controller.addRule(new FuzzyAND(new FuzzyOR(tempChangedLeft, tempChangedRight), tempNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(new FuzzyOR(tempChangedLeft, tempChangedRight), tempPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(tempSame, tempNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(tempSame, tempPredictable), new FuzzySet(predictData));

        // Light rules
        controller.addRule(new FuzzyAND(new FuzzyOR(lightChangedLeft, lightChangedRight), lightNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(new FuzzyOR(lightChangedLeft, lightChangedRight), lightPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(lightSame, lightNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(lightSame, lightPredictable), new FuzzySet(predictData));

        // Hall rules
        controller.addRule(new FuzzyAND(new FuzzyOR(hallChangedLeft, hallChangedRight), hallNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(new FuzzyOR(hallChangedLeft, hallChangedRight), hallPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(hallSame, hallNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(hallSame, hallPredictable), new FuzzySet(predictData));

        // Volt rules
        controller.addRule(new FuzzyAND(new FuzzyOR(voltChangedLeft, voltChangedRight), voltNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(new FuzzyOR(voltChangedLeft, voltChangedRight), voltPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(voltSame, voltNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(voltSame, voltPredictable), new FuzzySet(predictData));

        // bw rules
        controller.addRule(new FuzzyAND(new FuzzyOR(bwChangedLeft, bwChangedRight), bwNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(new FuzzyOR(bwChangedLeft, bwChangedRight), bwPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(bwSame, bwNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(bwSame, bwPredictable), new FuzzySet(predictData));

        // accely rules
        controller.addRule(new FuzzyAND(new FuzzyOR(accelyChangedLeft, accelyChangedRight), accelyNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(new FuzzyOR(accelyChangedLeft, accelyChangedRight), accelyPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(accelySame, accelyNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(accelySame, accelyPredictable), new FuzzySet(predictData));

        // accelx rules
        controller.addRule(new FuzzyAND(new FuzzyOR(accelxChangedLeft, accelxChangedRight), accelxNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(new FuzzyOR(accelxChangedLeft, accelxChangedRight), accelxPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(accelxSame, accelxNotPredictable), new FuzzySet(requestData));
        controller.addRule(new FuzzyAND(accelxSame, accelxPredictable), new FuzzySet(predictData));
    }

    private static FuzzySet createChangedLeftSet(String label, double value, double error, double slopeWidth) {
        return new FuzzySet(label, new TrapezoidalMembershipFunction(Double.NEGATIVE_INFINITY, value - error - slopeWidth, value - error, false));
    }

    private static FuzzySet createChangedRightSet(String label, double value, double error, double slopeWidth) {
        return new FuzzySet(label, new TrapezoidalMembershipFunction(value + error, value + error + slopeWidth, Double.POSITIVE_INFINITY, true));
    }

    private static FuzzySet createSameSet(String label, double value, double slopeWidth, double error) {
        return new FuzzySet(label, new TrapezoidalMembershipFunction(value - slopeWidth, value - error, value + error, value + slopeWidth));
    }

    private static FuzzySet createPredictableSet(String label) {
        return new FuzzySet(label, new TrapezoidalMembershipFunction(0.9 - 0.01, 0.95, Double.POSITIVE_INFINITY, true));
    }

    private static FuzzySet createNotPredictableSet(String label) {
        return new FuzzySet(label, new TrapezoidalMembershipFunction(Double.NEGATIVE_INFINITY, 0.9 - 0.001, 0.9, false));
    }

    public static class DataHolder {
        private double measuredValue;
        private double predictability;

        public DataHolder(double measuredValue, double predictability) {
            this.measuredValue = measuredValue;
            this.predictability = predictability;
        }

        public double getMeasuredValue() {
            return measuredValue;
        }

        public double getPredictability() {
            return predictability;
        }
    }
}