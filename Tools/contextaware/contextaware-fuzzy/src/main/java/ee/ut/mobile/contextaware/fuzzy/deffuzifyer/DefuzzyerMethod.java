
package ee.ut.mobile.contextaware.fuzzy.deffuzifyer;

import ee.ut.mobile.contextaware.fuzzy.variables.FuzzySet;

public interface DefuzzyerMethod {

    public double getDefuzziedValue(FuzzySet... results);
}
