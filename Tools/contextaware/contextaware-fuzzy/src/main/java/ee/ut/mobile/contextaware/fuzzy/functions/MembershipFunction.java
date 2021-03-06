
package ee.ut.mobile.contextaware.fuzzy.functions;

public interface MembershipFunction {

    /**
     * @param input the value of f(x).
     * @return double the result of the function.
     */
    double getValue(double input);

    /**
     * 
     * @param input the value x of f(x). 
     * @param clip the value to clip: if result > clip then return clip.
     * @return double the result of the function if it is under clip,
     * else return the clip value
     */
    double getClippedValue(double input, double clip);

    /**
     * 
     * @return the maximum (not infinity) value of the function. 
     */
    public double getMax();

    /**
     * 
     * @return the minimum (not infinity) value of the function. 
     */
    public double getMin();

	/**
	 *
	 * @param fnValue
	 * @return double the value of x sto that f(x) = input
	 */
	public double getXAtValue(double fnValue);
}
