package agents;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import model.Restaurant;
import services.RestaurantService;

/*
 * this agent represents the restoration to the directory facilitator
 */

public class GatewayAgent extends Agent {
    @Override
    protected void setup () {
        Object [] args = getArguments ();
        String restaurantId = (String) args [0];

        Restaurant restaurant = RestaurantService.getInstance ().getRestaurant (restaurantId);

        DFAgentDescription dfDesc = new DFAgentDescription ();
        dfDesc.setName (getAID ());

        System.out.printf ("registering agent %s for restaurant %s in DF\n", getName (), restaurant.getName ());

        for (int cuisine : restaurant.getCuisinesAdvertised ()) {
            ServiceDescription sd = new ServiceDescription ();

            sd.setType ("cuisine" + cuisine);
            sd.setName ("cuisine" + cuisine);

            dfDesc.addServices (sd);
        }

        try {
            DFService.register (this, dfDesc);
        } catch (FIPAException fipaException) {
            fipaException.printStackTrace ();
        }

        Behaviour messageHandlerBehaviour = new CyclicBehaviour () {
            @Override
            public void action () {
                ACLMessage message = receive ();
            }
        };

        addBehaviour (messageHandlerBehaviour);
        super.setup ();
    }

    @Override
    protected void takeDown () {
        try {
            DFService.deregister (this);
        } catch (FIPAException fipaException) {
            fipaException.printStackTrace ();
        }

        super.takeDown ();
    }
}
