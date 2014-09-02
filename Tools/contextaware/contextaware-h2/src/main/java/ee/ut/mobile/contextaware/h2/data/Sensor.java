package ee.ut.mobile.contextaware.h2.data;

public class Sensor {

	public static final int TYPE_TEMPERATURE = 1;
	public static final int TYPE_HALL = 2;
	public static final int TYPE_LIGHT = 3;
	public static final int TYPE_VOLTAGE = 4;
	public static final int TYPE_CPU_USAGE = 5;
	public static final int TYPE_ACCEL_X = 6;
	public static final int TYPE_ACCEL_Y = 7;
	public static final int TYPE_BW = 8;

	private long id;
	private String description;
	private double regressionError;
	private double measureError;
	private double rmse;

	public Sensor(long id, String description, double regressionError, double measureError, double rmse) {
		this.id = id;
		this.description = description;
		this.regressionError = regressionError;
		this.measureError = measureError;
        this.rmse = rmse;
	}

	public long getId() {
		return id;
	}

	public String getDescription() {
		return description;
	}

	public double getRegressionError() {
		return regressionError;
	}

	public double getMeasureError() {
		return measureError;
	}

    public double getRmse() {
        return rmse;
    }
}
