package ee.ut.mobile.contextaware.web;

import ee.ut.mobile.contextaware.fuzzy.FuzzyEngine;
import ee.ut.mobile.contextaware.h2.DAOManager;
import ee.ut.mobile.contextaware.h2.DataSourceFactory;
import ee.ut.mobile.contextaware.h2.data.Data;
import ee.ut.mobile.contextaware.h2.data.Sensor;
import ee.ut.mobile.contextaware.h2.data.SensorData;
import ee.ut.mobile.contextaware.predict.NeuralNetworkManager;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class HTTPHandler extends AbstractHandler {

    private Logger log;
    private NeuralNetworkManager neuralNetworkManager;
    private Map<Integer, Sensor> sensorMap;

    public HTTPHandler() {
        log = Logger.getLogger(this.getClass());

        log.debug("Creating/Updating tables");
        DAOManager.createTables(DataSourceFactory.getDataSource());
        log.debug("Tables created or updated");

        DAOManager.insertTableData(DataSourceFactory.getDataSource());
        log.debug("Inserted necessary data");

        sensorMap = DAOManager.getSensorMap(DataSourceFactory.getDataSource());
        neuralNetworkManager = new NeuralNetworkManager(DataSourceFactory.getDataSource(), sensorMap);
        log.debug("Handler initialised");
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        log.debug("handle called");
        long startTime = System.currentTimeMillis();
        if ("post".equalsIgnoreCase(request.getMethod())) {
            log.debug("New post request received");

            if ("application/json".equals(request.getContentType())) {
                try {
                    String body = readRequestBody(request.getInputStream());
                    SensorData receivedSensorData = DAOManager.processMessage(body);
                    if (receivedSensorData == null) {
                        throw new IOException("Could not parse data");
                    }
                    log.info("Data successfully parsed");

                    int idleTime = 10; // Default value

                    Map<Integer, NeuralNetworkManager.PredictionData> predictionMap = neuralNetworkManager.createPredictionData(receivedSensorData);
                    NeuralNetworkManager.PredictionData tempPred = predictionMap.get(Sensor.TYPE_TEMPERATURE);
                    NeuralNetworkManager.PredictionData hallPred = predictionMap.get(Sensor.TYPE_HALL);
                    NeuralNetworkManager.PredictionData lightPred = predictionMap.get(Sensor.TYPE_LIGHT);
                    NeuralNetworkManager.PredictionData voltagePred = predictionMap.get(Sensor.TYPE_VOLTAGE);
                    //NeuralNetworkManager.PredictionData cpu = predictionMap.get(Sensor.TYPE_CPU_USAGE);
                    NeuralNetworkManager.PredictionData accelxPred = predictionMap.get(Sensor.TYPE_ACCEL_X);
                    NeuralNetworkManager.PredictionData accelyPred = predictionMap.get(Sensor.TYPE_ACCEL_Y);
                    NeuralNetworkManager.PredictionData bwPred = predictionMap.get(Sensor.TYPE_BW);

                    if (predictionsAreValid(tempPred, lightPred, hallPred, bwPred, voltagePred, accelxPred,accelyPred)) {
                        double prevTemp = tempPred.getLastData().getValue();
                        double prevHall = hallPred.getLastData().getValue();
                        double prevLight = lightPred.getLastData().getValue();
                        double prevVoltage = voltagePred.getLastData().getValue();
                        double prevAccelx = accelxPred.getLastData().getValue();
                        double prevAccely = accelyPred.getLastData().getValue();
                        double prevBw = bwPred.getLastData().getValue();

                        double measuredTemp = tempPred.getMeasuredData().getValue();
                        double measuredHall = hallPred.getMeasuredData().getValue();
                        double measuredLight = lightPred.getMeasuredData().getValue();
                        double measuredBW = bwPred.getMeasuredData().getValue();
                        double measuredAccely = accelyPred.getMeasuredData().getValue();
                        double measuredAccelx = accelxPred.getMeasuredData().getValue();
                        double measuredVolt = voltagePred.getMeasuredData().getValue();

                        log.debug("Fuzzy logic initialize data: temp = " + prevTemp + ", light = " + prevLight + ", hall = " + prevHall);
                        log.debug("Measured data: temp = " + measuredTemp + ", light = " + measuredLight + ", hall = " + measuredHall);

                        FuzzyEngine engine = new FuzzyEngine(sensorMap);
                        engine.initialize(prevTemp, prevLight, prevHall, prevBw, prevVoltage, prevAccelx, prevAccely);

                        idleTime = engine.calculatePredictTime(new FuzzyEngine.DataHolder(measuredTemp, tempPred.getPredictability()),
                                new FuzzyEngine.DataHolder(measuredLight, lightPred.getPredictability()),
                                new FuzzyEngine.DataHolder(measuredHall, hallPred.getPredictability()),
                                new FuzzyEngine.DataHolder(measuredBW, bwPred.getPredictability()),
                                new FuzzyEngine.DataHolder(measuredVolt, voltagePred.getPredictability()),
                                new FuzzyEngine.DataHolder(measuredAccelx, accelxPred.getPredictability()),
                                new FuzzyEngine.DataHolder(measuredAccely, accelyPred.getPredictability())
                        );

                        log.debug("Fuzzy engine idle time = " + idleTime);

                        if (idleTime > 10 && !neuralNetworkManager.predictData(idleTime, tempPred, lightPred, hallPred, bwPred, voltagePred, accelxPred,accelyPred)) {
                            idleTime = 10;
                        }
                    }


                    for (Data receivedData : receivedSensorData.getData()) {
                        DAOManager.insertSensorData(DataSourceFactory.getDataSource(), receivedSensorData.getLocation(), receivedData);
                    }

                    idleTime = Math.max(idleTime, 10); // Minimum value is 10seconds

                    log.debug("Request handled in " + (System.currentTimeMillis() - startTime) + " ms");
                    response.setContentType("application/json;charset=utf-8");
                    response.setStatus(HttpServletResponse.SC_OK);
                    String respString = String.format("{\"idle\": %d}", idleTime);
                    log.debug("Response: " + respString);
                    response.setContentLength(respString.getBytes("utf-8").length);
                    response.getWriter().println(respString);
                } catch (IOException e) {
                    log.error(e);
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } else {
                response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            }
        } else {
            log.debug("Unsupported request type received");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
        baseRequest.setHandled(true);
    }


    private String readRequestBody(InputStream in) throws IOException {
        if (in == null) {
            log.debug("InputStream is null");
            return "";
        }
        InputStreamReader reader = new InputStreamReader(in);
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            body.append(buffer, 0, read);
        }
        log.debug("Received message body= " + body.toString());
        reader.close();
        return body.toString();
    }

    private static boolean predictionsAreValid(NeuralNetworkManager.PredictionData... predictions) {
        for (NeuralNetworkManager.PredictionData pred : predictions) {
            if (pred.getMeasuredData() == null
                    || pred.getLastData() == null
                    || pred.getType() == NeuralNetworkManager.PredictionData.UNDEFINED
                    || pred.getPredictability() == NeuralNetworkManager.PredictionData.UNDEFINED
                    || pred.getPredictability() < NeuralNetworkManager.PREDICTABILITY_THRESHOLD
                    || pred.getLocation() == NeuralNetworkManager.PredictionData.UNDEFINED) {

                return false;
            }
        }
        return true;
    }
}