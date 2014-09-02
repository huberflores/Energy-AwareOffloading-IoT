package ee.ut.mobile.contextaware.h2;

import ee.ut.mobile.contextaware.h2.data.Data;
import ee.ut.mobile.contextaware.h2.data.Sensor;
import ee.ut.mobile.contextaware.h2.data.SensorData;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class DAOManager {
    private static Logger log = Logger.getLogger(DAOManager.class);
    private static final String CREATE_LOCATIONS =
            "create table IF NOT EXISTS locations (id integer primary key  auto_increment,name VARCHAR)";

    private static final String CREATE_SENSOR_TYPES =
            "create table IF NOT EXISTS sensortypes (id integer primary key auto_increment ,type VARCHAR, regression_error double, measure_error double, rmse double )";

    private static final String CREATE_DATA =
            "create table IF NOT EXISTS data (id integer primary key auto_increment,sensortype_id integer , location_id integer , value double, measure_time timestamp, t_time time, measured boolean)";

    private static final String SELECT_TEMPS_DURATION_SPECIFIED = "select value, FORMATDATETIME(measure_time,'yyyy-MM-dd HH:mm:ss') as measure_time from data where measure_time < ? and measure_time > ? sensortype_id = 1 order by measure_time";
    private static final String SELECT_SENSOR_TYPES = "select * from sensortypes";
    private static final String SELECT_LOCATIONS = "select * from locations";
    private static final String SELECT_TEMPS = "select value from data where sensortype_id = 1 order by measure_time";
    private static final String SELECT_TEMPS_WITH_TIME = "select value, FORMATDATETIME(measure_time,'yyyy-MM-dd HH:mm:ss') as measure_time from data where sensortype_id = 1 order by measure_time";
    private static final String SELECT_TEMPS_WITH_TIME_BETWEEN = "select value, FORMATDATETIME(measure_time,'yyyy-MM-dd HH:mm:ss') as measure_time from data where sensortype_id = 1 and measure_time < ? and measure_time > ? order by measure_time";
    private static final String SELECT_DATA_WITH_TIME_BETWEEN = "select value, FORMATDATETIME(measure_time,'yyyy-MM-dd HH:mm:ss') as measure_time from data where sensortype_id = ? and measure_time < ? and measure_time > ? order by measure_time";
    private static final String SELECT_DATA_UNTIL = "select value, FORMATDATETIME(measure_time,'yyyy-MM-dd HH:mm:ss') as measure_time from data where sensortype_id = ? and measure_time < ? order by measure_time limit 30";
    private static final String SELECT_LAST_DATA_BY_TYPEID = "select * from data where sensortype_id = ? order by measure_time desc limit 1";


    /**
     * Adapted from the Sketch sending the data.
     * protocol.addValue(1, tm);
     * protocol.addValue(2, hs);
     * protocol.addValue(3, ldr);
     * protocol.addValue(4, readVoltage());
     * protocol.addValue(5, 0);
     * protocol.addValue(6, xAxisValue);
     * protocol.addValue(7, yAxisValue);
     * protocol.addValue(8, gyro.readX());
     * protocol.addValue(9, gyro.readXAxisRate());
     * protocol.addValue(10, gyro.readY());
     * protocol.addValue(11, gyro.readYAxisRate());
     */

    private static final String[] INSERT_STATEMENTS = {
            "insert into locations  (id, name) values (1,'ut')",
            "insert into sensortypes values (1, 'temp', 0.01, 0.05, 0.02)",
            "insert into sensortypes values (2,'hall', 0.01, 0.05, 0.02);",
            "insert into sensortypes values (3, 'ldr',0.01, 0.05, 0.02);",
            "insert into sensortypes values (4, 'battery status', 0.01, 0.05, 0.02);",
            "insert into sensortypes values (5, 'cpu', 0.01, 0.05, 0.02);",
            "insert into sensortypes values (6, 'accelX', 0.01, 0.05, 0.02);",
            "insert into sensortypes values (7, 'accelY', 0.01, 0.05, 0.02);",
            "insert into sensortypes values (8, 'bandW', 0.01, 0.05, 0.02);",
            "insert into sensortypes values (9, 'x gyro rate', 0.01, 0.05, 0.02);",
            "insert into sensortypes values (10, 'y gyro',0.01, 0.05, 0.02);",
            "insert into sensortypes values (11, 'y gyro rate', 0.01, 0.05, 0.02);",

    };

    public static void insertSensorData(DataSource dataSource, int location, Data data) {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement("insert into data (sensortype_id, location_id, value, measure_time, t_time, measured) values(?, ?, ?, ?, ?, ?)");
            preparedStatement.setInt(1, data.getType());
            preparedStatement.setInt(2, location);
            preparedStatement.setDouble(3, data.getValue());
            preparedStatement.setTimestamp(4, data.getMeasureTime());
            preparedStatement.setTime(5, data.getTime());
            preparedStatement.setBoolean(6, data.isMeasured());
            preparedStatement.execute();

            log.debug(String.format("insert into data (sensortype_id, location_id, value, measure_time, t_time, measured) values(%d, %d, %f, %s, %s, %s)",
                    data.getType(), location, data.getValue(), data.getMeasureTime(), data.getTime(), data.isMeasured()));
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, preparedStatement);
        }
    }

    public static List<Data> getDifferenceComparisonDataList(DataSource dataSource, int sensorType, int location) {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        List<Data> result = new ArrayList<Data>();
        try {
            connection = dataSource.getConnection();
            preparedStatement = connection
                    .prepareStatement("select * from data where sensortype_id = ? and location_id = ?" +
                            " and measure_time < current_timestamp and measure_time >= dateadd('minute', -4, " +
                            "current_timestamp) and measured = true order by measure_time asc");
            preparedStatement.setInt(1, sensorType);
            preparedStatement.setInt(2, location);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                Data data = new Data(resultSet.getInt("sensortype_id"), resultSet.getDouble("value"));
                data.setMeasured(resultSet.getBoolean("measured"));
                data.setMeasureTime(resultSet.getTimestamp("measure_time"));
                result.add(data);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, preparedStatement);
        }
        return result;
    }


    public static Map<Integer, Sensor> getSensorMap(DataSource dataSource) {
        log.debug("Requesting sensor map");
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        Map<Integer, Sensor> resultMap = new HashMap<Integer, Sensor>();
        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement("select * from sensortypes");
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String desc = resultSet.getString("type");
                double regError = resultSet.getDouble("regression_error");
                double measureError = resultSet.getDouble("measure_error");
                double rmse = resultSet.getDouble("rmse");
                resultMap.put(id, new Sensor(id, desc, regError, measureError,rmse));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, preparedStatement);
        }
        return resultMap;
    }

    public static SensorData processMessage(String message) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        SensorData sensorData = mapper.readValue(message, SensorData.class);
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        for (Data data : sensorData.getData()) {
            data.setMeasured(true);
            data.setMeasureTime(ts);
            data.setTime(new Time(System.currentTimeMillis()));
        }
        return sensorData;
    }

    public static void insertLocation(DataSource dataSource, String location) {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = dataSource.getConnection();
            preparedStatement = connection
                    .prepareStatement("insert into locations (name) values(?)");
            preparedStatement.setString(1, location);
            preparedStatement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, preparedStatement);
        }
    }

    public static void createTables(DataSource dataSource) {

        Connection connection = null;
        Statement statement = null;
        try {
            connection = dataSource.getConnection();
            log.debug("URL connection  " + connection.getMetaData().getURL());
            log.debug("Username connection  " + connection.getMetaData().getUserName());
            log.debug(" Driver name connection  " + connection.getMetaData().getDriverName());
            statement = connection.createStatement();
            statement.execute(CREATE_LOCATIONS);
            log.debug("Created location table");
            statement.execute(CREATE_SENSOR_TYPES);
            log.debug("Created Sensor table");
            statement.execute(CREATE_DATA);
            log.debug("Created Data table");
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, statement);
        }
    }

    public static void insertTableData(DataSource dataSource) {

        Connection connection = null;
        Statement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            if (!statement.executeQuery(SELECT_SENSOR_TYPES).next()) {
                for (int i = 0; i < INSERT_STATEMENTS.length; i++) {
                    statement.execute(INSERT_STATEMENTS[i]);
                }
                log.debug("insert statements executed");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, statement);
        }
    }


    public static List<Double> getAllTemperature(DataSource dataSource) {
        Connection connection = null;
        Statement stmt = null;
        List<Double> result = new ArrayList<Double>();
        try {
            connection = dataSource.getConnection();
            stmt = connection.createStatement();
            stmt.execute(SELECT_TEMPS);
            ResultSet resultSet = stmt.getResultSet();
            while (resultSet.next()) {
                result.add(resultSet.getDouble("value"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, stmt);
        }
        return result;
    }

    public static Map<Timestamp,Double> getTemperatureWithTime(DataSource dataSource) {
        Connection connection = null;
        Statement stmt = null;
        Map<Timestamp,Double> result = new TreeMap<Timestamp,Double>();
        try {
            connection = dataSource.getConnection();
            stmt = connection.createStatement();
            stmt.execute(SELECT_TEMPS_WITH_TIME);
            ResultSet resultSet = stmt.getResultSet();
            while (resultSet.next()) {
                result.put(resultSet.getTimestamp("measure_time"),resultSet.getDouble("value"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, stmt);
        }
        return result;
    }


    public static Map<Timestamp,Double> getTemperatureWithTimeBetween(DataSource dataSource, Timestamp start, Timestamp stop) {
        Connection connection = null;
        PreparedStatement stmt = null;
        Map<Timestamp,Double> result = new TreeMap<Timestamp,Double>();
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(SELECT_TEMPS_WITH_TIME_BETWEEN);
            stmt.setTimestamp(2,start);
            stmt.setTimestamp(1,stop);

            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                result.put(resultSet.getTimestamp("measure_time"),resultSet.getDouble("value"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, stmt);
        }
        return result;
    }

    public static Map<Timestamp,Double> getDataWithTimeBetween(DataSource dataSource, int sensorId,Timestamp start, Timestamp stop) {
        Connection connection = null;
        PreparedStatement stmt = null;
        Map<Timestamp,Double> result = new TreeMap<Timestamp,Double>();
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(SELECT_DATA_WITH_TIME_BETWEEN);
            stmt.setTimestamp(3,start);
            stmt.setTimestamp(2,stop);
            stmt.setInt(1,sensorId);

            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                result.put(resultSet.getTimestamp("measure_time"),resultSet.getDouble("value"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, stmt);
        }
        return result;
    }


    public static Map<Timestamp,Double> getDataUntil(DataSource dataSource, int sensorId,Timestamp end) {
        Connection connection = null;
        PreparedStatement stmt = null;
        Map<Timestamp,Double> result = new TreeMap<Timestamp,Double>();
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(SELECT_DATA_UNTIL);
            stmt.setTimestamp(2,end);
            stmt.setInt(1,sensorId);

            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                result.put(resultSet.getTimestamp("measure_time"),resultSet.getDouble("value"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, stmt);
        }
        return result;
    }


    private static void close(Connection connection, Statement stmt) {
        try {
            if (stmt != null)
                stmt.close();
            if (connection != null)
                connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Data getLastDataByTypeId(DataSource dataSource, int sensortypeId) {
        Connection connection = null;
        PreparedStatement stmt = null;
        Data data = null;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(SELECT_LAST_DATA_BY_TYPEID);
            stmt.setInt(1,sensortypeId);

            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                data = new Data(resultSet.getInt("sensortype_id"), resultSet.getDouble("value"));
                data.setMeasured(resultSet.getBoolean("measured"));
                data.setMeasureTime(resultSet.getTimestamp("measure_time"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(connection, stmt);
        }
        return data;
    }
}
