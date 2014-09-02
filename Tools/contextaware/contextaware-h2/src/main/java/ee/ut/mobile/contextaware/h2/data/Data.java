package ee.ut.mobile.contextaware.h2.data;

import java.sql.Time;
import java.sql.Timestamp;

public class Data {

	private int type;
	private double value;
	private Timestamp measureTime;
    private Time time;
	private boolean measured;

	public Data() {}

	public Data(int type, double value) {
		this.type = type;
		this.value = value;
		this.measureTime = new Timestamp(System.currentTimeMillis());
        this.time = new Time(System.currentTimeMillis());
		this.measured = false;
	}

	public Timestamp getMeasureTime() {
		return measureTime;
	}

	public void setMeasureTime(Timestamp measureTime) {
		this.measureTime = measureTime;
	}

	public boolean isMeasured() {
		return measured;
	}

	public void setMeasured(boolean measured) {
		this.measured = measured;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

    public Time getTime() {
        return time;
    }

    public void setTime(Time time) {
        this.time = time;
    }

    @Override
	public String toString() {
		return "Data{" +
				"type=" + type +
				", value=" + value +
				", measureTime=" + measureTime +
				", time=" + time +
				", measured=" + measured +
				'}';
	}
}
