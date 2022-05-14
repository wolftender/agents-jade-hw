package agents;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import model.Customer;
import model.Restaurant;
import services.RestaurantService;

/*
 * this agent represents the restoration to the directory facilitator
 */

public class GatewayAgent extends Agent {
    private static class MessageHandlerBehaviour extends CyclicBehaviour {
        public MessageHandlerBehaviour (Agent agent) {
            super (agent);
        }

        private void handleCallForProposal (ACLMessage message) {
            String content = message.getContent ();
            Customer.Order order = Customer.Order.deserialize (content);
            ACLMessage reply = message.createReply ();

            System.out.printf ("%s from %s with reply %s\n", getAgent ().getName (), message.getSender (), reply.getInReplyTo ());

            if (order == null) {
                reply.setPerformative (ACLMessage.NOT_UNDERSTOOD);
                reply.setContent ("invalid_format");
            } else {
                // handle the message, send the message to our own restaurant manager
                // to check if we can handle order, otherwise say no

                reply.setPerformative (ACLMessage.REFUSE);
                reply.setContent ("unavailable");
            }

            getAgent ().send (reply);
        }

        @Override
        public void action () {
            ACLMessage message = getAgent ().receive ();

            if (message != null) {
                if (message.getPerformative () == ACLMessage.CFP) {
                    handleCallForProposal (message);
                }
            } else {
                block ();
            }
        }
    }

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

        addBehaviour (new MessageHandlerBehaviour (this));
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
