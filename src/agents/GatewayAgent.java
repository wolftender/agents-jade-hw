package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.Customer;
import model.Restaurant;
import services.RestaurantService;

import java.util.HashMap;
import java.util.Map;

/*
 * this agent represents the restoration to the directory facilitator
 */

public class GatewayAgent extends Agent {
    private static class MessageHandlerBehaviour extends CyclicBehaviour {
        private final Map<String, AID> activeConversations = new HashMap<> ();

        public MessageHandlerBehaviour (Agent agent) {
            super (agent);
        }

        private void handleCallForProposal (ACLMessage message) {
            String content = message.getContent ();
            Customer.Order order = Customer.Order.deserialize (content);
            ACLMessage reply = message.createReply ();

            System.out.printf ("%s from %s with reply %s\n", getAgent ().getName (), message.getSender ().getName (), reply.getInReplyTo ());

            if (order == null) {
                reply.setPerformative (ACLMessage.NOT_UNDERSTOOD);
                reply.setContent ("invalid_format");

                getAgent ().send (reply);
            } else {
                // handle the message, send the message to our own restaurant manager
                // to check if we can handle order, otherwise say no

                GatewayAgent gatewayAgent = (GatewayAgent) getAgent ();

                if (!gatewayAgent.ready) {
                    reply.setPerformative (ACLMessage.REFUSE);
                    reply.setContent ("unavailable");

                    getAgent ().send (reply);
                } else {
                    String conversationId = message.getReplyWith ();
                    activeConversations.put (conversationId, message.getSender ());

                    ACLMessage query = new ACLMessage (ACLMessage.PROPOSE);
                    query.setReplyWith (conversationId);
                    query.setContent (order.serialize ());
                    query.setConversationId ("food_query");
                    query.addReceiver (gatewayAgent.managerName);

                    System.out.printf ("%s sending query to its manager about %s\n", getAgent ().getName (), query.getContent ());
                    getAgent ().send (query);
                }
            }
        }

        private void handleHandshake (ACLMessage message) {
            GatewayAgent gatewayAgent = (GatewayAgent) getAgent ();

            if (message.getInReplyTo ().equals (gatewayAgent.handshakeId) && !gatewayAgent.ready) {
                gatewayAgent.ready = true;
                System.out.printf ("[handshake complete!] handshake complete between %s and %s!\n", getAgent ().getName (), message.getSender ().getName ());
            }
        }

        private void handleFoodQueryResponse (ACLMessage message) {
            GatewayAgent gatewayAgent = (GatewayAgent) getAgent ();

            if (!message.getSender ().equals (gatewayAgent.managerName)) {
                System.err.printf ("invalid sender id %s\n", message.getSender ().getName ());
                return;
            }

            String cfpId = message.getInReplyTo ();
            AID clientId = activeConversations.get (cfpId);

            if (clientId != null) {
                ACLMessage reply = new ACLMessage (message.getPerformative ());

                if (message.getPerformative () == ACLMessage.AGREE) {
                    System.out.printf ("restaurant %s chef accepted order %s, sending feedback to %s\n", getAgent ().getName (), cfpId, clientId.getName ());
                } else {
                    System.out.printf ("restaurant %s chef rejected order %s, sending feedback to %s\n", getAgent ().getName (), cfpId, clientId.getName ());
                }

                reply.addReceiver (clientId);
                reply.setContent ("chef_response");
                reply.setConversationId ("food_order");
                reply.setInReplyTo (cfpId);

                activeConversations.remove (cfpId);
                getAgent ().send (reply);
            } else {
                System.err.printf ("invalid cfp id %s\n", cfpId);
            }
        }

        @Override
        public void action () {
            ACLMessage message = getAgent ().receive ();

            if (message != null) {
                if (message.getPerformative () == ACLMessage.CFP) {
                    handleCallForProposal (message);
                } else if (message.getConversationId ().equals ("handshake")) {
                    if (message.getPerformative () == ACLMessage.AGREE) {
                        handleHandshake (message);
                    }
                } else if (message.getConversationId ().equals ("food_query")) {
                    handleFoodQueryResponse (message);
                }
            } else {
                block ();
            }
        }
    }

    private boolean ready = false;

    private AID managerName;
    private String restaurantId;
    private String handshakeId;

    @Override
    protected void setup () {
        Object [] args = getArguments ();

        // setup important values
        restaurantId = (String) args [0];
        managerName = new AID ((String) args [1], AID.ISLOCALNAME);
        ready = false;

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

        // handshake behaviour
        // i don't know if its needed, but its fun
        // the gateway agent checks if it was not bamboozled with incorrect agent
        addBehaviour (new OneShotBehaviour () {
            @Override
            public void action () {
                ACLMessage message = new ACLMessage (ACLMessage.PROPOSE);
                String replyWith = String.format ("hs-%s-%d", restaurantId, System.currentTimeMillis ());

                handshakeId = replyWith;

                message.setConversationId ("handshake");
                message.setContent (restaurantId);
                message.addReceiver (managerName);
                message.setReplyWith (replyWith);

                send (message);
            }
        });

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
