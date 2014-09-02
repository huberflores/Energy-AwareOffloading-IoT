
package ee.ut.mobile.contextaware.fuzzy.rules;

import java.io.Serializable;


public interface FuzzyTerm extends Serializable {

    double getDOM();

    void clearDOM();

	void orWithDOM(double dom);
}
