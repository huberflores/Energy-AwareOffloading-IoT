package ee.ut.mobile.contextaware.predict.predictor;

import org.encog.ml.BasicML;
import org.encog.neural.networks.BasicNetwork;

/*
* All Classes extend this so that they can be able to predict
* */
public abstract class BasePredictor {

    private BasicML network = new BasicNetwork();

    public abstract double predict(long newTime);

    public BasicML getNetwork() {
        return network;
    }

    public void setNetwork(BasicML network) {
        this.network = network;
    }
}
